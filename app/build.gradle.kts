plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vui.vaporwave"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vui.vaporwave"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.4.3"
    }

    buildTypes {
        release {
            // R8 code + resource shrinking. Matters most for material-icons-extended, which ships
            // thousands of vector icons of which this app references a few dozen; unshrunk they
            // are all packaged and all counted against dex/method limits.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No production signingConfig exists in this project yet. Signing with the debug
            // keystore here only so this release build type is installable for local testing
            // (needed to get wildcard baseline-prof.txt rules expanded -- AGP only runs that
            // expansion, expandReleaseArtProfileWildcards, for the release variant, not debug).
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // Keep debug builds fast to iterate on -- no shrinking.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // SAF folder access for reading sibling .lrc lyric files -- scoped storage blocks direct
  // File/MediaStore access to non-owned, non-audio files on some devices (see MusicRepository's
  // fetchLyrics), so this is the reliable fallback.
  implementation(libs.androidx.documentfile)
  // Installs app/src/main/baseline-prof.txt as an ahead-of-time compilation profile at install
  // time, so ART can compile the app's cold-start hot path (splash -> MediaStore scan -> Library)
  // instead of interpreting/JIT-warming it from scratch on every fresh install -- see the profile
  // file itself for why this exists.
  implementation(libs.androidx.profileinstaller)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Media3 & Audio Engine (Supports MP3, FLAC, WAV, AAC, OGG, OPUS, M4A, ALAC, etc.)
  // media3-ui is deliberately absent: it ships the View-based player UI, which a Compose-only
  // app never touches.
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)

  // Coil for Album Artwork loading
  implementation(libs.coil.compose)

  // Extended Material 3 Icons
  implementation(libs.androidx.compose.material.icons.extended)
}
