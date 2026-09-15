package com.vui.vaporwave.ui.screens

import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.vui.vaporwave.R
import com.vui.vaporwave.theme.SplashPinkBright
import com.vui.vaporwave.theme.SplashPinkGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Light-theme counterpart to [SplashScreen] -- same handwriting reveal and flicker timing, but
 * drawn in a single saturated pink rather than the pink-violet-cyan neon gradient, with the glow
 * halo shaded dark instead of light. The original's gradient and white-hot glow are tuned for a
 * dark background; against [backgroundColor] here (a light surface) that combination washes out
 * to near-illegible, so the tube color goes more saturated and the glow goes darker to keep
 * contrast instead of brighter.
 */
@Composable
fun SplashScreenLight(
    backgroundColor: Color,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // The traced outline of "Vaporwave", built once the canvas has a real size to center within.
    var fullPath by remember { mutableStateOf<AndroidPath?>(null) }
    var contourLengths by remember { mutableStateOf<List<Float>>(emptyList()) }
    var totalLength by remember { mutableFloatStateOf(0f) }

    val progress = remember { Animatable(0f) }
    // Drives a quick flicker once the trace finishes -- real neon tubes don't just snap on at
    // full brightness, they stutter a couple of times before settling, so this dips and recovers
    // a few times right at the end instead of holding steady from the moment the last letter
    // completes.
    val glow = remember { Animatable(1f) }

    // Keyed on Unit rather than canvasSize: this must run exactly once. A LaunchedEffect keyed on
    // canvasSize looked equivalent, but a recomposition shortly after launch (the theme
    // preference flows settling) restarted it with the same size value -- which cancelled the
    // first run mid-animateTo and never restarted it, since the replacement saw fullPath already
    // built and bailed out early. That left progress stuck at ~0 forever: blank screen, no crash.
    LaunchedEffect(Unit) {
        snapshotFlow { canvasSize }.first { it.width > 0 && it.height > 0 }

        val typeface = ResourcesCompat.getFont(context, R.font.alex_brush)
        val textSizePx = with(density) { 128.sp.toPx() }
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
        }
        // A real signature's capital is drawn bigger than the letters that follow it -- rendered
        // as its own path at a larger size, sharing the same baseline (y=0) as the rest of the
        // word, then the rest is shifted over by exactly the big V's advance width so it picks up
        // right where that stroke ends. (An earlier attempt built this by carving the "V" out of
        // the full "Vaporwave" path with Path.op(DIFFERENCE) instead, to dodge a theorized
        // isolated-glyph swash substitution -- that theory turned out false, the outline is
        // identical either way, and the boolean op was quietly scrambling contour order, which
        // is why the reveal stopped tracing left-to-right like a signature.)
        val capitalScale = 1.05f
        val paintCapital = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx * capitalScale
        }

        val capitalPath = AndroidPath()
        paintCapital.getTextPath("V", 0, 1, 0f, 0f, capitalPath)
        val capitalAdvance = paintCapital.measureText("V")

        val restPath = AndroidPath()
        paint.getTextPath("aporwave", 0, "aporwave".length, 0f, 0f, restPath)
        restPath.offset(capitalAdvance, 0f)

        // getTextPath places the first letter's baseline start at the origin -- rotating there
        // (rather than around the word's center) is what makes it read as a signature rising
        // away from its own starting point, the way a hand lifts as it writes across the page.
        // Rotating the origin-pinned pieces separately (rather than the combined path) keeps
        // (0, 0) fixed in place, which the fit-to-width step below relies on.
        val rotation = Matrix().apply { setRotate(-15f, 0f, 0f) }
        capitalPath.transform(rotation)
        restPath.transform(rotation)

        // The fit-to-width scale below must be based on typographic extent (baseline start to
        // the last letter's ink), not the capital's full visual bounds -- the swash on the "V"
        // loops back out to the left, and sizing against that meant a smaller capitalScale also
        // shrank the loop's reach, which this step then scaled back up to compensate. That
        // silently cancelled out every attempt to tone the loop down. Measuring against restPath
        // (which the swash doesn't touch) instead means capitalScale actually controls how big
        // the loop reads on screen.
        val referenceBounds = RectF()
        restPath.computeBounds(referenceBounds, true)
        val scale = (canvasSize.width * 0.82f) / referenceBounds.right

        val path = AndroidPath()
        path.addPath(capitalPath)
        path.addPath(restPath)
        path.transform(Matrix().apply { setScale(scale, scale) })

        val bounds = RectF()
        path.computeBounds(bounds, true)
        val dx = (canvasSize.width - bounds.width()) / 2f - bounds.left
        val dy = (canvasSize.height - bounds.height()) / 2f - bounds.top
        path.offset(dx, dy)

        // Walk every contour (each letter is its own contour) once up front so the per-frame
        // reveal below just has to slice into this list instead of re-measuring every time.
        val lengths = mutableListOf<Float>()
        val measure = PathMeasure(path, false)
        var total = 0f
        do {
            lengths += measure.length
            total += measure.length
        } while (measure.nextContour())

        fullPath = path
        contourLengths = lengths
        totalLength = total

        progress.animateTo(1f, animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing))

        // The flicker-to-life moment: a couple of quick dips before it settles bright and steady.
        glow.animateTo(0.35f, tween(25))
        glow.animateTo(1f, tween(35))
        glow.animateTo(0.5f, tween(25))
        glow.animateTo(1f, tween(45))
        glow.animateTo(0.7f, tween(20))
        glow.animateTo(1f, tween(75))
        delay(200)
        onFinished()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { canvasSize = it }
    ) {
        val source = fullPath ?: return@Canvas
        val revealLength = totalLength * progress.value
        if (revealLength <= 0f) return@Canvas

        // Build the "drawn so far" path by walking each letter's contour in order, taking it
        // fully once the reveal has passed it and stopping mid-letter on the one it's currently
        // tracing -- this is what makes it read as being written left to right rather than
        // fading in all at once.
        val revealed = AndroidPath()
        val measure = PathMeasure(source, false)
        var remaining = revealLength
        var contourIndex = 0
        do {
            val length = contourLengths.getOrElse(contourIndex) { measure.length }
            if (remaining <= 0f) break
            if (remaining >= length) {
                measure.getSegment(0f, length, revealed, true)
                remaining -= length
            } else {
                measure.getSegment(0f, remaining, revealed, true)
                remaining = 0f
            }
            contourIndex++
        } while (measure.nextContour())

        val composePath = revealed.asComposePath()
        val glowAmount = glow.value

        // Same widening/fading stack as the dark splash, but colored with a soft light pink
        // instead of the tube color.
        val glowLayers = listOf(
            36.dp to 0.05f,
            26.dp to 0.09f,
            18.dp to 0.16f,
            11.dp to 0.26f
        )
        for ((width, alpha) in glowLayers) {
            drawPath(
                path = composePath,
                color = SplashPinkGlow,
                alpha = alpha * glowAmount,
                style = Stroke(width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // The tube's own colour -- a single saturated bright pink rather than the dark splash's
        // pink-violet-cyan gradient, which read as pastel against a light background.
        drawPath(
            path = composePath,
            color = SplashPinkBright,
            alpha = glowAmount,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // A near-white hot core, like the glowing glass itself seen through the colour -- this
        // sits inside the pink stroke rather than touching the background directly, so it stays a
        // legible highlight instead of vanishing into LightSurface.
        drawPath(
            path = composePath,
            color = Color.White,
            alpha = 0.8f * glowAmount,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
