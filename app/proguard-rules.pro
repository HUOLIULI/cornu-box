# ============================================
# Source engine layer — keep via reflection
# ============================================
# Engines are instantiated via `SourceProvider` / reflection; keep class + members.
-keep class com.aggregator.shell.core.source.engine.** { *; }
-keep class * implements com.aggregator.shell.core.source.api.** { *; }

# ============================================
# Rhino 1.7.14 — pure-JVM JS engine
# ============================================
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# ============================================
# OkHttp / Moshi — networking + JSON
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# ============================================
# Jsoup / JsonPath — HTML scraping
# ============================================
-dontwarn org.jsoup.**
-dontwarn com.jayway.jsonpath.**

# ============================================
# Room entities — reflection by Room compiler
# ============================================
-keep class com.aggregator.shell.core.data.local.entity.** { *; }

# ============================================
# Media3 / ExoPlayer
# ============================================
# ExoPlayer uses native callbacks and reflection on internal classes; R8 must
# keep listener methods intact or playback will crash silently.
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keepclassmembers class androidx.media3.** {
    public *;
    protected *;
}
-dontwarn androidx.media3.**

# ============================================
# @Keep-annotated members
# ============================================
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
}
