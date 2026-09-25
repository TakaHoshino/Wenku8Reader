buildscript {
    dependencies {
        // AGP 9 内置 Kotlin（默认 KGP 2.2.10）。miuix 0.9.1 的元数据由 Kotlin 2.3.20 生成，
        // 必须按官方说明用 classpath 把 KGP 提到 2.3.21（与 org.jetbrains.kotlin.plugin.compose 同版本）。
        // 注意：buildscript 块在版本目录 accessor 可用之前求值，这里只能写字面量；
        // 版本必须与 gradle/libs.versions.toml 里的 `kotlin` 保持一致。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    // Material 3 Expressive 需要 material3 1.5.0-alpha（1.4.0 的 MaterialExpressiveTheme 仍是
    // internal），而它要求 compileSdk ≥ 35 / AGP ≥ 8.6，故 AGP 与 Gradle 同步上移。
    // AGP 9：compileSdk 37（miuix 0.9.x 的 minCompileSdk）需要 AGP ≥ 9.1；
    // AGP 9 起 Kotlin 支持内置，**不能再应用 org.jetbrains.kotlin.android**。
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
