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
#-dontwarn **
#-keep class **
#-keepclassmembers class *{*;}
#-keepattributes *
-dontwarn de.michelinside.glucodatahandler.common.**
# --------------------------------------------------------------------
# Internal Common Module Classes
# --------------------------------------------------------------------
# This prevents R8 from stripping away your core logic classes
-keep class de.michelinside.glucodatahandler.common.** { *; }

# Also keep the mobile specific classes to ensure services and receivers
# are not renamed, which would break Android system callbacks
-keep class de.michelinside.glucodatahandler.** { *; }

# --------------------------------------------------------------------
# Tasker Plugin Support
# --------------------------------------------------------------------
-keep class com.joaomgcd.taskerpluginlibrary.** { *; }
-keep class * implements com.joaomgcd.taskerpluginlibrary.TaskerPlugin { *; }
# Keep your specific Tasker Action/Condition classes
-keep class * extends com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginCondition { *; }
-keep class * extends com.joaomgcd.taskerpluginlibrary.action.TaskerPluginAction { *; }

# --------------------------------------------------------------------
# Kotlin Serialization
# --------------------------------------------------------------------
# Keep the serializable classes to prevent stripping of names/fields
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# --------------------------------------------------------------------
# Play Services / Wear OS
# --------------------------------------------------------------------
-keep class com.google.android.gms.wearable.** { *; }

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
# General View Handling (for ViewBinding/Custom Views)
# --------------------------------------------------------------------
-keepclassmembers class * extends android.view.View {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
   public void set*(***);
}

# --------------------------------------------------------------------
# Android Auto (Car App Library)
#--------------------------------------------------------------------
-keep class androidx.car.app.** { *; }
-keep class * extends androidx.car.app.CarAppService { *; }

# --------------------------------------------------------------------
# Health Connect
# --------------------------------------------------------------------
-keep class androidx.health.connect.client.** { *; }

# --------------------------------------------------------------------
# WorkManager
# --------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# --------------------------------------------------------------------
# Slidetoact & ColorPicker (UI Libraries often use reflection)
# --------------------------------------------------------------------
-keep class com.ncorti.slidetoact.** { *; }
-keep class com.jaredrummler.colorpicker.** { *; }

# --------------------------------------------------------------------
# Kotlin Coroutines (Used by Ktor and WorkManager)
# --------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    volatile <fields>;
}



# --------------------------------------------------------------------
# REMOVE all debug log messages
# --------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}