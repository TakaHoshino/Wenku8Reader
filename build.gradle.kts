plugins {
    // Material 3 Expressive 需要 material3 1.5.0-alpha（1.4.0 的 MaterialExpressiveTheme 仍是
    // internal），而它要求 compileSdk ≥ 35 / AGP ≥ 8.6，故 AGP 与 Gradle 同步上移。
    id("com.android.application") version "8.13.2" apply false
    // Kotlin 2.3.21：miuix-kmp 0.8.8 的元数据由 Kotlin 2.3.20 生成，
    // 编译器必须不低于该版本才能读取（2.2.x 会报 "compiled with a newer version of Kotlin"）。
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
