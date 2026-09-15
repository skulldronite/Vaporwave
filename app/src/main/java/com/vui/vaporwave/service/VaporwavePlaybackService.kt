package com.vui.vaporwave.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vui.vaporwave.MainActivity

/**
 * VaporwavePlaybackService manages background audio playback,
 * system media notifications, audio focus, and headset disconnect events.
 * Extends AndroidX Media3 MediaSessionService.
 */
class VaporwavePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    // The platform Equalizer lives here (not in MusicViewModel) because it must attach to the
    // real ExoPlayer's audioSessionId, which only exists inside the process that owns the
    // ExoPlayer -- MediaController on the ViewModel side never sees a session id at all. UI
    // requests reach this through the COMMAND_EQ_APPLY custom session command below.
    private var equalizer: Equalizer? = null
    private var desiredEqEnabled = false
    private var desiredEqGains: FloatArray? = null

    override fun onCreate() {
        super.onCreate()

        // Configure audio attributes for music playback with automatic audio focus handling
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        this.player = exoPlayer

        // ExoPlayer can (rarely) regenerate its AudioTrack session id across its lifetime -- the
        // Equalizer is rebuilt (not just left stale) whenever that happens, reapplying whatever
        // enabled/gain state was last requested.
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                recreateEqualizer(audioSessionId)
            }
        })
        recreateEqualizer(exoPlayer.audioSessionId)

        // Intent to launch MainActivity when clicking on the system media notification
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            // Media3's default BitmapLoader doesn't reliably resolve the content:// MediaStore
            // URIs this app uses for artwork -- see ContentUriBitmapLoader for why.
            .setBitmapLoader(ContentUriBitmapLoader(this))
            .setCallback(EqualizerSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Without this, swipe-dismissing the app from Recents while paused (or with no queue) leaves
    // the service and its ExoPlayer instance alive indefinitely in the background.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    private fun recreateEqualizer(audioSessionId: Int) {
        equalizer?.release()
        equalizer = try {
            // Priority 0 (lowest): defers to any other effect -- including the device's own OEM
            // sound enhancements -- already attached to this session rather than fighting them
            // for control, which is the class of conflict the in-app warning dialog calls out.
            Equalizer(0, audioSessionId)
        } catch (e: Exception) {
            // Some OEM audio stacks refuse to attach a second equalizer effect at all.
            null
        }
        applyDesiredEqState()
    }

    private fun applyDesiredEqState() {
        val eq = equalizer ?: return
        try {
            eq.enabled = desiredEqEnabled
            val gains = desiredEqGains ?: return
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            for (band in gains.indices) {
                if (band >= eq.numberOfBands) break
                // Gains arrive from the UI in dB; the platform Equalizer works in millibels.
                val millibel = (gains[band] * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(band.toShort(), millibel.toShort())
            }
        } catch (e: Exception) {
            // The effect may have been released concurrently (session id changing mid-apply), or
            // an OEM DSP rejected the setting -- either way, nothing left to do but drop it.
        }
    }

    private inner class EqualizerSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(COMMAND_EQ_APPLY, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, connectionResult.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_EQ_APPLY) {
                desiredEqEnabled = args.getBoolean(EXTRA_EQ_ENABLED, false)
                desiredEqGains = args.getFloatArray(EXTRA_EQ_GAINS)
                applyDesiredEqState()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        const val COMMAND_EQ_APPLY = "com.vui.vaporwave.eq.APPLY"
        const val EXTRA_EQ_ENABLED = "enabled"
        const val EXTRA_EQ_GAINS = "gains"
    }
}
