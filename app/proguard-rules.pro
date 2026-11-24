# Keep AccessibilityService, Receivers, and MainActivity referenced by AndroidManifest.xml
-keep class com.gxrawake.app.service.KeepAwakeAccessibilityService { *; }
-keep class com.gxrawake.app.service.BootRestoreReceiver { *; }
-keep class com.gxrawake.app.service.RestoreAlarmReceiver { *; }
-keep class com.gxrawake.app.ui.MainActivity { *; }

# Keep libadb-android / Conscrypt / Spake2 JNI & reflection classes
-keep class org.conscrypt.** { *; }
-keep class io.github.muntashirakon.adb.** { *; }
-keep class io.github.muntashirakon.crypto.spake2.** { *; }
