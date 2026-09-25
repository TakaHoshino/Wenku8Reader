plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Room 的注解处理走 KSP（KSP 2.3.x 跟随 Kotlin 2.3 的版式）
    id("com.google.devtools.ksp") version "2.3.12"
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
    // compileSdk 37：material3 1.5 要求 ≥35，而 miuix 0.9.x 的 AAR metadata 要求 37
    //（0.9.x 起 minCompileSdk=37，需要 AGP ≥ 9.1；0.8.8 才是 36）。
    // targetSdk 暂不动：升到 35+ 会强制开启 edge-to-edge 与新的前台行为，属于另一轮改动。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hoshino.wenku8reader"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        // 资源语言裁剪：应用只提供中文（默认 values 即简体）+ 繁体（values-zh-rTW），
        // 但依赖库（androidx / emoji2 / material3 / miuix）带了几十种语言的字符串，
        // 实测 resources.arsc 里 2326 条带 config 的条目中 zh 系仅 357 条。
        // 声明后 aapt2 只保留这三个语言配置，arsc 体积明显下降（英文留作缺失资源的兜底）。
        resourceConfigurations += listOf("en", "zh", "zh-rTW")

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

    packaging {
        resources {
            // 死资源：opencc4j（简繁转换）的传递依赖 `com.github.houbb:nlp-common` 带了一套
            // jieba 分词词典（nlp/word_freq_dict.txt 未压缩 5.29MB，压缩后 1.83MB）。
            // 实测 dex 里既没有 `nlp/` 字符串也没有 `com/github/houbb/nlp/*` 类——
            // 代码路径不可达，直接排除。注意 opencc4j 真正使用的 `data/dictionary/*`
            //（ST/TS 字典，约 0.45MB）必须保留，否则简繁转换会失效。
            excludes += setOf("nlp/**")
        }
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
        // miuix 的 ScrollBar 同样标注为实验 API（MiuixBookLists / MiuixExplorePage /
        // MiuixStatsPage / MiuixTocPage 共 10 处调用）。集中 opt-in 的理由同上：
        // 逐个加注解只是噪声；影响面也限于"长列表右侧滚动条"这一处观感。
        optIn.add("top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi")
    }
}

dependencies {
    // Compose BOM 统一 ui/foundation/material 系列版本；material3 单列覆盖到 Expressive 版本
    // （BOM 内的 material3 是 1.4.0，其 MaterialExpressiveTheme 仍为 internal，不可用）
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.core:core-ktx:1.13.1")
    // 必须 ≥1.11：miuix 的弹层（下拉/对话框/底部弹层）用 NavigationEvent 处理返回键，
    // 需要宿主提供 LocalNavigationEventDispatcherOwner，而它由该版本的 ComponentActivity 提供。
    // 旧版（1.9.x）下打开任意 miuix 弹层都会抛
    // "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"。
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    // MIUIX（第三方 HyperOS/MIUI 设计语言实现，作者 yukonga）：实验性 UI 风格可切换，
    // 见 ui/theme/UiStyle.kt。0.9.1 的 kotlin-stdlib 与项目一致（2.3.21）；
    // miuix-blur 提供悬浮组件的液态玻璃（背景模糊 + 高光描边），SukiSU-Ultra 同款。
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.1")
    // 0.9.x 起设置行/下拉等"偏好项"组件被拆到 miuix-preference（原 extra 包）
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.houbb:opencc4j:1.14.0")
    implementation("org.chromium.net:cronet-embedded:119.6045.31")
    // Room：书架与阅读进度（替代整份 JSON 解析 + SharedPreferences 的进度键）
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 本地单元测试：Parsers / UpdateChecker.isNewer 等纯逻辑（无 Android 依赖）在此覆盖。
    // 这两处是历史上 bug 最密集、且最容易在重构中悄悄回归的区域。
    testImplementation("junit:junit:4.13.2")
    // android.jar 里的 org.json 在单元测试中只是抛 "Stub!" 的空壳，而书架迁移要解析
    // 旧 SharedPreferences 里的 JSON。引入同名的纯 Java 实现顶上：API 与 Android 端一致，
    // 且只作用于测试运行时 classpath（AGP 把 mockable android.jar 排在最后，不会遮蔽它）。
    testImplementation("org.json:json:20240303")
}
