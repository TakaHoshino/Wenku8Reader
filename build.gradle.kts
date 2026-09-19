plugins {
    // Material 3 Expressive 需要 material3 1.5.0-alpha（1.4.0 的 MaterialExpressiveTheme 仍是
    // internal），而它要求 compileSdk ≥ 35 / AGP ≥ 8.6，故 AGP 与 Gradle 同步上移。
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
