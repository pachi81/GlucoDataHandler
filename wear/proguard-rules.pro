# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn de.michelinside.glucodatahandler.common.**

# --------------------------------------------------------------------
# Internal Common Module Classes
# --------------------------------------------------------------------
# This prevents R8 from stripping away your core logic classes
-keep class de.michelinside.glucodatahandler.common.** { *; }

# Also keep the wear specific classes to ensure services and receivers
# are not renamed, which would break Android system callbacks
-keep class de.michelinside.glucodatahandler.** { *; }

# --------------------------------------------------------------------
# Play Services / Wear OS
# --------------------------------------------------------------------
-keep class com.google.android.gms.wearable.** { *; }
-keep class androidx.wear.** { *; }
-keep class androidx.wear.watchface.** { *; }

# --------------------------------------------------------------------
# Wear OS Watchface & Complications
# --------------------------------------------------------------------
# Verhindert, dass die Komplikations-Provider-Services umbenannt werden
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# Falls du eigene Custom Renderers für Watchfaces nutzt
-keep class * extends androidx.wear.watchface.Renderer { *; }
-keep class * extends androidx.wear.watchface.WatchFaceService { *; }


# --------------------------------------------------------------------
# MPAndroidChart
# --------------------------------------------------------------------
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --------------------------------------------------------------------
# Ktor (CIO Server)
# --------------------------------------------------------------------
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --------------------------------------------------------------------
# Kotlin Serialization (falls genutzt für Phone-Watch Sync)
#--------------------------------------------------------------------
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# --------------------------------------------------------------------
# UI & Layouts (ViewBinding / ConstraintLayout)
# --------------------------------------------------------------------
# Keeps ViewBinding and custom UI components working
-keep class androidx.constraintlayout.widget.** { *; }
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# --------------------------------------------------------------------
# Splashscreen (Android 12+)
# --------------------------------------------------------------------
-keep class androidx.core.splashscreen.** { *; }



# --------------------------------------------------------------------
# General View Handling (for ViewBinding/Custom Views)
# --------------------------------------------------------------------
-keepclassmembers class * extends android.view.View {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
   public void set*(***);
}

# --------------------------------------------------------------------
# WorkManager
# --------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# --------------------------------------------------------------------
# REMOVE all debug log messages
# --------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}