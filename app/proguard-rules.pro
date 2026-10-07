# 源引擎通过字符串/反射方式实例化，必须保留
-keep class com.aggregator.shell.core.source.engine.** { *; }
-keep class * implements com.aggregator.shell.core.source.api.** { *; }

# Rhino JS 引擎
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# OkHttp / Retrofit / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Jsoup / JsonPath
-dontwarn org.jsoup.**
-dontwarn com.jayway.jsonpath.**

# Room 实体
-keep class com.aggregator.shell.core.data.local.entity.** { *; }

# Medis3
-dontwarn androidx.media3.**

# 保留 @Keep 注解成员
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
}
