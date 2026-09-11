# R8 rules for release builds.
#
# Most of what this app depends on (Compose, Media3, Coil, kotlinx-coroutines) ships its own
# consumer rules inside the AAR, so this file only needs to cover what R8 cannot infer here.

# Keep the app's model classes intact. They are plain data holders, but their field names are
# used as JSON keys when playlists and play stats are persisted to SharedPreferences, and any
# renaming there would silently invalidate data already written to disk by an earlier install.
-keep class com.vui.vaporwave.model.** { *; }

# The playback service is instantiated by the system from the manifest entry, never from code.
-keep class com.vui.vaporwave.service.VaporwavePlaybackService { *; }

# Line numbers in crash reports for release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
