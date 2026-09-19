plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- 版本号来源（自动化版本管理，详见根目录 VERSIONING.md）----
// 优先级：
// 1) CI 环境变量（GitHub Actions）：APP_VERSION_NAME（Conventional Commits 语义解析）
//    与 APP_VERSION_CODE（时间基准 yyyymmddHH，唯一且递增）
// 2) 本地构建回退：gradle.properties 的 VERSION_NAME / VERSION_CODE（手动维护）
// 注意：空字符串（""）按未设置处理——System.getenv 对空变量返回 "" 而非 null，
// 若不处理会导致 versionName 为空、安装器不显示版本。
val releaseVersionName: String =
    System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("VERSION_NAME") as String?)
        ?: "1.0.0"
val releaseVersionCode: Int =
    (System.getenv("APP_VERSION_CODE")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("VERSION_CODE") as String?)
        ?: "10000").toIntOrNull() ?: 10000

// 是否注入正式签名（CI secrets）：未注入时 release 回退 debug 签名，保证可安装
val useReleaseSigning = System.getenv("KEYSTORE_PATH") != null

android {
    namespace = "com.hoshino.wenku8reader"
    // compileSdk 35 是 material3 1.5.0-alpha / compose 1.11 的硬性下限（AAR metadata
    // minCompileSdk=35）；取 36 与已安装的 build-tools 36.0.0 对齐。
    // targetSdk 暂不动：升到 35+ 会强制开启 edge-to-edge 与新的前台行为，属于另一轮改动。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hoshino.wenku8reader"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        // 体积优化：裁剪原生库 ABI——仅保留真机必需的 arm64-v8a / armeabi-v7a，
        // 以及 x86_64（模拟器）。去掉 32 位 x86（已无真实设备，省约 5.5MB）。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (useReleaseSigning) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH")!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            // 体积优化：R8 混淆 + 资源收缩（基线 32.9MB → 目标缩减 ≥20%）
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (useReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Kotlin 2.2 起 `kotlinOptions` 已弃用，改用类型安全的 compilerOptions（等价配置）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Material 3 Expressive 组件在 material3 1.5 中仍标注 @ExperimentalMaterial3ExpressiveApi。
        // 本项目的 UI 层整体采用该设计语言（组件层已另行注明来源），故在此统一 opt-in，
        // 避免每个页面都要重复一遍注解。升级 material3 时需要按 release notes 复核一次。
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    // Compose BOM 统一 ui/foundation/material 系列版本；material3 单列覆盖到 Expressive 版本
    // （BOM 内的 material3 是 1.4.0，其 MaterialExpressiveTheme 仍为 internal，不可用）
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    // MIUIX（第三方 HyperOS/MIUI 设计语言实现，作者 yukonga）：实验性 UI 风格可切换，
    // 见 ui/theme/UiStyle.kt。取 0.8.8 而不是 0.9.x：0.9.x 起要求 compileSdk 37
    //（进而要求 AGP 9 + Gradle 9），0.8.8 只要求 compileSdk 36，与本项目工具链一致。
    implementation("top.yukonga.miuix.kmp:miuix:0.8.8")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.houbb:opencc4j:1.14.0")
    implementation("org.chromium.net:cronet-embedded:119.6045.31")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 本地单元测试：Parsers / UpdateChecker.isNewer 等纯逻辑（无 Android 依赖）在此覆盖。
    // 这两处是历史上 bug 最密集、且最容易在重构中悄悄回归的区域。
    testImplementation("junit:junit:4.13.2")
}
