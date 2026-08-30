# ---- R8 / ProGuard 混淆规则 ----
# 说明：release 开启 minify+shrinkResources 后需要保留的反射/JNI 相关类。

# OkHttp / Okio 依赖的 optional 依赖告警抑制
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Cronet：内含 JNI 绑定与反射，整体保留（CF 绕过三级栈依赖）
-keep class org.chromium.** { *; }
-dontwarn org.chromium.**

# opencc4j：简繁转换词典按资源/反射加载，整体保留
-keep class com.github.houbb.opencc4j.** { *; }

# org.json：JSONObject 反射性较弱，但直接保留最稳妥（体积很小）
-keep class org.json.** { *; }

# opencc4j 依赖的 jieba 分词（optional，未直接使用）
-dontwarn com.huaban.analysis.jieba.**
