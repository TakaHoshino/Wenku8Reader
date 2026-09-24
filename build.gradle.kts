buildscript {
    dependencies {
        // AGP 9 内置 Kotlin（默认 KGP 2.2.10）。miuix 0.9.1 的元数据由 Kotlin 2.3.20 生成，
        // 必须按官方说明用 classpath 把 KGP 提到 2.3.21（与 org.jetbrains.kotlin.plugin.compose 同版本）。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    // Material 3 Expressive 需要 material3 1.5.0-alpha（1.4.0 的 MaterialExpressiveTheme 仍是
    // internal），而它要求 compileSdk ≥ 35 / AGP ≥ 8.6，故 AGP 与 Gradle 同步上移。
    // AGP 9：compileSdk 37（miuix 0.9.x 的 minCompileSdk）需要 AGP ≥ 9.1；
    // AGP 9 起 Kotlin 支持内置，**不能再应用 org.jetbrains.kotlin.android**。
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
