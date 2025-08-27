# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

-dontwarn org.slf4j.impl.**
-dontwarn com.codahale.metrics.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn com.oracle.svm.core.annotate.TargetClass.**
-dontwarn javax.lang.model.element.Modifier.**
-dontwarn javax.security.auth.callback.NameCallback.**
-dontwarn javax.security.auth.callback.TextOutputCallback.**
-dontwarn javax.security.sasl.**
-dontwarn org.apache.log4j.**
-dontwarn org.springframework.beans.factory.**
-dontwarn javax.lang.model.element.Modifier.**
-dontwarn javax.security.auth.callback.NameCallback.**
-dontwarn javax.security.auth.callback.TextOutputCallback.**
#
#-dontwarn javax.lang.model.element.**
#-dontwarn javax.security.auth.callback.**
#
# Keep specific classes and their members
-keep class com.esri.arcgisruntime.data.** { *; }
-keep class com.esri.arcgisruntime.geometry.** { *; }
-keep class com.esri.arcgisruntime.layers.** { *; }
-keep class com.esri.arcgisruntime.mapping.** { *; }
-keep class com.esri.arcgisruntime.mapping.view.** { *; }
-keep class com.esri.arcgisruntime.symbology.** { *; }
#
## Obfuscate all other classes
#-allowaccessmodification
#-keep class !com.esri.arcgisruntime.data.**,** {*;}
#-keep class !com.esri.arcgisruntime.geometry.**,** {*;}
#-keep class !com.esri.arcgisruntime.layers.**,** {*;}
#-keep class !com.esri.arcgisruntime.mapping.**,** {*;}
#-keep class !com.esri.arcgisruntime.mapping.view.**,** {*;}
#-keep class !com.esri.arcgisruntime.symbology.**,** {*;}


-keep class kotlin.reflect.** { *; }
-keep class pk.gop.pulse.katchiAbadi.data.local.** { *; }
-keep class pk.gop.pulse.katchiAbadi.data.remote.** { *; }


# Retrofit
-keep class retrofit2.** { *; }

# OkHttp
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.examples.android.model.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation