# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Firebase Crashlytics ──
# Preserve line numbers and source file names for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase Remote Config ──
-keep class com.google.firebase.remoteconfig.** { *; }

# ── Room Database (Java entities) ──
-keep class com.example.neckguard.data.local.** { *; }

# ── Rive Animation ──
-keep class app.rive.** { *; }

# ── CameraX (R8 full-mode fix) ──
# R8 full mode strips Camera2Config (the camera2 implementation bootstrap)
# even though its reflection entry point, Camera2Config$DefaultProvider, is
# kept by the library's consumer rules. At runtime DefaultProvider then hits
# NoClassDefFoundError → ProcessCameraProvider init fails → the posture-check
# camera never binds in RELEASE builds only (debug skips R8, works fine).
# Verified via mapping.txt: "Camera2Config -> R8$$REMOVED$$CLASS$$18".
-keep class androidx.camera.camera2.Camera2Config { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ── Strip Logging ──
# Only strip verbose/info/debug. Keep warn/error so crash-context
# logging from MainViewModel, FirebaseAuthManager, etc. survives in
# release builds and reaches Crashlytics breadcrumbs.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
}