# Add project specific ProGuard rules here.

# ── Moshi ──────────────────────────────────────────────────────────────────────
# Keep Moshi-generated JsonAdapter classes
-keep class com.squareup.moshi.** { *; }
-keep class **JsonAdapter { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Keep @JsonClass-annotated classes and their generated adapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <init>(...);
}

# ── Gson ───────────────────────────────────────────────────────────────────────
# Keep TypeToken generic signatures (used in AddonConfigServer)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Retrofit ───────────────────────────────────────────────────────────────────
# Keep generic signatures for Retrofit service methods
-keepattributes Signature
# Keep Retrofit service interfaces (must preserve generic return types)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Keep all project API interfaces
-keep class com.nuvio.tv.data.remote.api.** { *; }

# ── OkHttp ─────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Data classes (DTOs) ────────────────────────────────────────────────────────
# Keep all DTO classes used with Moshi/Retrofit
-keep class com.nuvio.tv.data.remote.dto.** { *; }
-keep class com.nuvio.tv.domain.model.** { *; }

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Kotlin Metadata for reflection
-keepattributes RuntimeVisibleAnnotations

# ── ExoPlayer / Media3 ────────────────────────────────────────────────────────
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class androidx.media.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }
-keep class com.google.android.exoplayer2.ext.** { *; }

# Keep native interfaces and handles for Nuvio Engine JNI
-keep class androidx.media3.exoplayer.upstream.DefaultAllocatorNative {
    native <methods>;
}
-keep class androidx.media3.exoplayer.source.SampleDataQueueNative {
    native <methods>;
}
-keep class androidx.media3.exoplayer.upstream.Allocation {
    <init>(java.nio.ByteBuffer, int, long);
    public long nativeHandle;
}

# ── Kotlinx Serialization ──────────────────────────────────────────────────────
# Keep @Serializable classes and their generated serializers
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── General ────────────────────────────────────────────────────────────────────
# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MPV (native JNI callbacks)
# Native code reflects into multiple classes/methods under is.xyz.mpv,
# so keep the whole package to avoid JNI lookup crashes after R8.
-keep class is.xyz.mpv.** { *; }

-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
# BalooTV: AccesibilidadCompose lee estos campos por reflexión. -keepnames / -keepclassmembernames conservan
# los nombres aunque R8 optimice: con -keepclassmembers solo se conservaban si la clase se conservaba, y en 0.2.0
# R8 renombró la clase y la reflexión dejó de encontrarla.
-keepnames class androidx.compose.ui.platform.AndroidComposeView
-keepclassmembernames class androidx.compose.ui.platform.AndroidComposeView {
    androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat composeAccessibilityDelegate;
}
-keepnames class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat
-keepclassmembernames class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat {
    java.util.List _enabledServices;
}
# BalooTV: la copia de preferencias va en JSON con Gson. Sin esto R8 renombra los campos (la copia salía con
# claves a, b, c…) y una copia dejaría de valer en cuanto otra compilación los renombrase distinto.
-keep class com.nuvio.tv.balootv.ajustes.CopiaPreferencias { <fields>; <init>(...); }
