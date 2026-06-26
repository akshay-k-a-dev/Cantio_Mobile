# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

-dontwarn org.slf4j.impl.StaticLoggerBinder

# Retrofit rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson rules
-keepattributes *Annotation*, Signature, ElementPrecision, UnderlyingStructure
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Keep all API model classes to prevent obfuscation of JSON fields
-keep class com.appplayer.music.data.api.models.** { *; }
-keepclassmembers class com.appplayer.music.data.api.models.** { *; }

# Keep InnerTube models and pages
-keep class com.appplayer.innertube.** { *; }
-keepclassmembers class com.appplayer.innertube.** { *; }

# Keep Kotlin serialization / metadata attributes just in case
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
