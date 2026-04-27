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

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}