plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- 版本号来源（自动化版本管理，详见根目录 VERSIONING.md）----
// 优先级：
// 1) CI 环境变量（GitHub Actions）：APP_VERSION_NAME（Conventional Commits 语义解析）
//    与 APP_VERSION_CODE（= github.run_number，唯一且递增）
// 2) 本地构建回退：gradle.properties 的 VERSION_NAME / VERSION_CODE（手动维护）
val releaseVersionName: String =
    System.getenv("APP_VERSION_NAME")
        ?: (project.findProperty("VERSION_NAME") as String?)
        ?: "1.0.0"
val releaseVersionCode: Int =
    (System.getenv("APP_VERSION_CODE")
        ?: (project.findProperty("VERSION_CODE") as String?)
        ?: "10000").toIntOrNull() ?: 10000

// 是否注入正式签名（CI secrets）：未注入时 release 回退 debug 签名，保证可安装
val useReleaseSigning = System.getenv("KEYSTORE_PATH") != null

android {
    namespace = "com.hoshino.wenku8reader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hoshino.wenku8reader"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = releaseVersionName
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
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.houbb:opencc4j:1.14.0")
    implementation("org.chromium.net:cronet-embedded:119.6045.31")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
