# Wenku8Reader — 技术细节与开发进度汇总

> 本文档用于**二次开发 / 其他 Agent 接手**时的快速导航。包含项目结构、技术栈、核心模块实现细节、已知问题与开发约定。
> 最后更新：2026-08-29。源码基于以下真实代码梳理，改动代码后请同步更新本文档。

---

## 1. 项目概述

轻小说文库（wenku8.net，jieqi CMS）的 Android 客户端，纯 **Kotlin + Jetpack Compose + Material Design 3（MD3）** 实现。

功能现状：MD3 界面（动态取色）、内置账号登录、首页栏目/搜索/标签浏览、书籍详情、书架、**沉浸式在线阅读器**（滚动/侧滑双模式、自动边距、章节进度、状态指示器、自动翻页、音量键翻页、图片章节）、TXT/EPUB 离线下载（保存至 `Downloads/Wenku8/`）。

---

## 2. 技术栈与环境

| 项 | 值 |
|---|---|
| 语言 | Kotlin（JVM target 17） |
| UI | Jetpack Compose（BOM 2024.09.02）+ Material 3 + material-icons-extended |
| 架构 | 单 Activity + Navigation-Compose + ViewModel（手写 DI 容器，无 Hilt） |
| 网络 | OkHttp 4.12 + Cronet（chromium net）+ WebView 兜底；Coil 2.7 加载图片 |
| 依赖注入 | 手写 `AppContainer`（`di/AppContainer.kt`） |
| 异步 | Kotlin Coroutines + StateFlow |
| 简繁 | opencc4j（`com.github.houbb:opencc4j`） |
| minSdk / target / compile | 26 / 34 / 34 |
| Gradle | Gradle 8.9，AGP 8.5.2（需 JDK 17） |

关键 build 文件：`app/build.gradle.kts`。**项目根目录没有 gradlew wrapper**，命令行需用 Android Studio 自带 Gradle/JBR 构建（见 §9）。

AndroidManifest：仅声明 `INTERNET` 权限，`usesCleartextTraffic="true"`（兼容 http 图片）。无 FileProvider、无存储权限（下载走 MediaStore）。

---

## 3. 项目结构（当前真实结构）

```
Wenku8Reader/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── local.properties          # sdk.dir=F:\Android\Sdk
├── 技术性文档(只读勿动)/     # 参考文档，勿修改（wenku8-api.md 等，见 §8）
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/strings.xml        # 全部 UI 文案（中文字符串资源）
    └── java/com/hoshino/wenku8reader/
        ├── MainActivity.kt            # 入口；音量键转发；全局主题装配
        ├── Wenku8Application.kt       # 持有 AppContainer
        ├── di/AppContainer.kt         # 手写 DI：client/repository/preferences/readerSettings/localLibrary/downloadEngine
        ├── data/
        │   ├── Models.kt              # SearchResult/BookInfo/Chapter/Volume/ChapterContent/BookcaseItem/FlatChapter/HomeBook/HomeSection
        │   ├── Wenku8Client.kt        # 全部 wenku8 网络接口（限速/重试/多镜像/WebView+Cronet+OkHttp）
        │   ├── Parsers.kt             # 正则 HTML 解析；splitFullTxt 全本切章
        │   ├── CookieStore.kt         # Cookie 持久化（OkHttp CookieJar）
        │   ├── DownloadEngine.kt      # 下载任务状态机（StateFlow）
        │   ├── FileSaver.kt           # MediaStore 保存到 Downloads/Wenku8/
        │   ├── EpubBuilder.kt         # 最小 EPUB3 打包器
        │   └── local/
        │       ├── AppPreferences.kt  # 账号凭据 + 阅读进度（SharedPreferences）
        │       ├── ReaderSettings.kt  # 全局阅读器外观/交互设置（StateFlow）
        │       ├── LocalLibraryStore.kt # 本地书架快照（JSON in SharedPreferences）
        │       └── DefaultAccount.kt  # 内置账号（首启静默登录用）
        ├── data/repository/Wenku8Repository.kt  # 统一仓库层（Result 包装）
        └── ui/
            ├── AppViewModelProvider.kt    # 手写 ViewModel.Factory
            ├── MainScaffold.kt           # 分页式主 Tab（HorizontalPager + 弹簧动画）+ NavHost 子页栈
            ├── navigation/Routes.kt      # 类型安全路由常量（MAIN 为三 Tab 宿主路由）
            ├── common/                   # UiText.kt / ReaderAppearance.kt(fontFamilyFor) / CoilRequests.kt(封面请求，带 Referer + 目标尺寸解码)
            ├── theme/Theme.kt            # MD3 主题（动态取色 + 种子色 + AMOLED 纯黑）
            ├── theme/Type.kt             # 全局 Typography（参考 SukiSU expressive 风格）
            ├── theme/Colors.kt           # 种子色预设
            ├── components/Anim.kt        # 通用动画（pressClickable 按压缩放）
            ├── components/Expressive.kt  # SukiSU 风格组件（TonalCard/ExpressiveScaffold/Segmented 系列/StatusTag/WarningCard/ExpressiveSwitch）
            ├── components/PagerNavigation.kt # 主 Tab 弹簧动画（MainPagerState + springAnimateToPage）
            ├── explore/                  # 首页栏目 + 搜索 + 标签书单（ExplorePage / TagBooksScreen）
            ├── about/                    # 关于页（图标/版本/GitHub/爱发电链接）
            ├── detail/                   # 书籍详情（卡片化）
            ├── bookcase/                 # 书架（BookcasePage，卡片化）
            ├── downloads/                # 下载管理（卡片化，自带返回）
            ├── settings/                 # 设置（SettingsPage 分组卡片 + 外观）+ 阅读器自定义
            └── reader/                   # 阅读器（核心，未改动）
                ├── ReaderScreen.kt       # 阅读器整屏（约 1077 行）
                ├── ReaderViewModel.kt    # 目录/章节加载 + 进度持久化 + 简繁转换
                ├── Pagination.kt         # 分页算法（纯函数）
                └── VolumeKeyTurn.kt      # 音量键翻页桥
```

---

## 4. 数据层

### 4.1 核心数据模型（`Models.kt`）
全部为 `@Immutable` data class。要点：
- `BookInfo.groupId` 可能为 `null`，缺省时用 `id / 1000` 兜底（`Wenku8Repository.groupIdOf()`）。
- `ChapterContent(title, text, images)`：images 为章节内插图 URL 列表（插图章节 text 为空）。
- `FlatChapter(index, cid, name)`：目录树扁平化结果，供阅读器前后章导航。

### 4.2 网络客户端（`Wenku8Client.kt`）
所有方法 suspend，内部 `withContext(Dispatchers.IO)`。关键细节：
- **编码**：响应声明 `charset=gbk` 但实际为 **GB18030**（GBK 严格超集）。全部用 `Charset.forName("GB18030")` 手动解码字节；POST 表单体用 `URLEncoder.encode(k, "GBK")`。
- **浏览器头伪装**：`browserHeaders()` 带 `Sec-Fetch-*`、`Upgrade-Insecure-Requests`、`Accept`（含 image/avif/webp）、`Cache-Control: max-age=0`、随机/固定 Chrome UA。
- **多镜像域名**：`MIRRORS = [www.wenku8.cc, www.wenku8.net, www.wenku8.com]`；**默认主域为 wenku8.cc**，可在设置页切换（`ReaderSettings.primaryMirror`，`Wenku8Client` 经 `primaryMirrorProvider` 读取），请求顺序 = 选定主域 + 其余兜底；旧默认 wenku8.net（未手动改过的用户）自动迁移到新默认。
- **cf_clearance 持久化复用**（参考 `LightNovelReader`）：WebView 解出 CF 挑战后，把 WebView 写入的 Cookie（含 `cf_clearance`/`__cf_bm`）经 `CookieStore.saveRaw` 持久化，并记录该主机挑战时使用的 UA（`challengeUa`，cf_clearance 与该 UA 绑定）；后续 OkHttp/Cronet 请求用 `uaFor()` 复用同一 UA 直接带令牌通过，无需每次重跑 WebView。切换主镜像时 `clearCookies()` 清空全部 Cookie 与 UA 绑定并自动用内置账号重登。
- **登录判据**：以 `jieqiUserInfo` 会话 Cookie 为准（`hasSession()`）。⚠️ 旧版用 `index.php` 是否含 `frmlogin` 判定，而首页公开且无登录表单，**永远误判为已登录** → 静默登录从未执行 → 需登录的接口（tags/bookcase）拿到登录页重定向。现已改为 Cookie 判据 + `ensureLoggedIn()`（tags/tagBooks 内先确保登录）+ 启动静默登录重试 3 次。
- **内置分类清单**：`BUILT_IN_TAGS`（50 个标准分类，参考 LightNovelReader 内置 tagList）作为「标签」页分类的**直接来源**——`tags()` 秒回、无需登录/网络；每分类书籍仍在线抓取（`tagBooks`，需登录）。`isLoggedIn()` 已简化为会话 Cookie 判据（去掉无意义的 `index.php` 联网检查）。
- **三级抓取栈**（`fetchWithBypass`，用于 tags/tagBooks/首页）：WebView（真浏览器跑 CF JS 挑战，读回 DOM）→ Cronet（TLS 指纹过 CF）→ OkHttp 随机 Android UA，逐镜像尝试。**首页/标签/标签书单均先走 `tryDirect` 快路径**（cookie-first，参考 LightNovelReader：已有 cf_clearance 时用绑定 UA 直连一次通过，跳过 WebView），仅失败/解析为空时升级到三级栈。其余接口（bookInfo/chapters/chapterContent）先网页直连，失败后走 App API 兜底。
- **App API 兜底**（参考 LightNovelReader 的 `Wenku8AppDataSource`）：`bookInfo/chapters/chapterContent` 在网页失败后走官方 App API（`http://app.wenku8.com/android.php`，POST `request`(base64)/`timetoken`/`appver` + Dalvik UA，社区中继 `https://wenku8-relay.mewx.org` 兜底），串行限流 + 请求间随机 1.5~2s 延迟。**2026-08 实测两个端点均已失效**（官方回 "Welcome"、中继 400），保留为无害兜底：失败极快，不影响网页主路径。
- **内存缓存**（参考 LightNovelReader 的 2h Cache）：`bookInfo`/目录缓存 2h、章节缓存 30min，仅缓存成功结果，减少重复请求与被拦概率。
- **本地磁盘缓存**（`data/local/HtmlDiskCache.kt`）：抓取内容按 URL 落盘到 `filesDir/html_cache`（卸载前持久），命中且未过期直接返回、避免二次加载；仅缓存非 CF 挑战/非登录页。TTL：首页 1h、详情/目录 7d、章节正文 30d、标签书单 1d；总量超 30MB 时按最旧优先清理。内存缓存（快）→ 磁盘缓存（持久）→ 网络，三级取数。
- **自适应限速**：全局 `lastRequest` 间隔基数 600ms × `rate`（成功 ×0.85 回落、失败 ×2 放大，上限 ×8）；`RATE_CODES = {403,429,500,502,503,504}` 触发指数退避重试（1.5s→3s→…上限 30s，3 次）。搜索额外 5s 最小间隔，超频错误页自动等 5s 重试一次。
- **搜索精确命中**：POST `/so.php`，302 到 `/book/{id}.htm` 时直接解析单书详情。
- **Referer**：`refererFor()` 按请求自身 host 生成，镜像安全。

### 4.3 解析（`Parsers.kt`）
纯正则（`java.util.regex.Pattern`）。注意：
- `parseChapter`：用 `indexOf("<div id=\"content\">")` 到 `indexOf("<div id=\"footlink\"")` 截取正文（**不用正则**，防嵌套 div）；剥离 `<ul id="contentdp">` 水印；`<br>/</p>` → `\n`；去标签；实体解码；`\u3000` → 两个半角；按行 trim、合并连续空行。
- `splitFullTxt(txt, volumes)`：用目录树生成期望章节头（`卷名 章节名`），在 TXT 行里按序二分定位切分章节，用于 EPUB 生成。
- `parseBookcase`：按 `bid` 分组，无 `cid` 链接为书名、有 `cid` 为最新章节。
- `parseHomepage` / `parseTags` / `parseBookList`：首页 `<div class="block">` 切块、标签、书籍列表。

### 4.4 Cookie（`CookieStore.kt`）
实现 `okhttp3.CookieJar`，登录成功后 `persist()` 落盘；`cronetGet`/`webViewGet` 手动拼 `Cookie` 头。

### 4.5 下载（`DownloadEngine` / `FileSaver` / `EpubBuilder`）
- `DownloadEngine`：`Dispatchers.IO` 单协程任务，`StateFlow<Map<Int, DownloadJob>>` 暴露进度；`cancelFlags` 支持取消。TXT 优先站点全本直链（`dl.wenku8.com/down.php?type=txt|utf8|big5`），失败回退逐章抓取；EPUB 先取全本 TXT 用 `splitFullTxt` 切章（成功率 <60% 回退逐章），再 `EpubBuilder.build` 打包（EPUB3：mimetype STORED + container/opf/nav/ncx/css + 每章 xhtml）。
- `FileSaver`：API 29+ 走 MediaStore（`Downloads/Wenku8/`），以下写应用私有目录。

### 4.6 本地存储
- `AppPreferences`（prefs：`account`/`reading`/`ui`）：账号明文凭据；每书 `progress_{bookId}` = 当前 cid；`progress_pos_/progress_total_` = 章节位置；书柜排序。
- `ReaderSettings`（prefs：`settings`）：**全局唯一设置源**，`StateFlow<ReaderSettingsState>` 同时驱动 MainActivity 主题与阅读器。所有 setter 均先更新内存 StateFlow 再写 SharedPreferences（`emit()`）。UI 重构新增字段：`amoled`（纯黑模式，深色下 surface 压真黑，仅影响应用主题，不影响阅读器纸张色）。
- `LocalLibraryStore`（prefs：`library`）：本地书架快照（JSONArray 序列化 `LibraryBook`）。
- `DefaultAccount`：内置账号（`技术性文档(只读勿动)/wenku8account.txt`），首启静默登录保证未登录也能读内容。

---

## 5. UI 层

### 5.1 入口与装配
- `MainActivity`：`enableEdgeToEdge()`；`dispatchKeyEvent` 把音量键转发给 `VolumeKeyTurn`；`setContent` 中按 `ReaderSettings` 组装 `Wenku8ReaderTheme`。
- **高刷新率适配**：`requestHighRefreshRate()` 在 API 30+ 用 `preferredDisplayModeId`、API 26-29 用 `preferredRefreshRate`，请求同分辨率下的最高刷新率（60Hz 设备无副作用）。
- `Wenku8Application` + `AppContainer`：手动 DI，无框架。
- `AppViewModelProvider`：手写 `ViewModelProvider.Factory`（从 `AppContainer` 取依赖注入 ViewModel）。

### 5.2 导航（`Routes.kt` + `MainScaffold.kt`，2026-08 UI 重构后）
- **主界面分页式 Tab**：`Routes.MAIN` 为宿主路由，内部 `HorizontalPager` 承载 3 个 Tab 页（`ExplorePage` / `BookcasePage` / `SettingsPage`），页面常驻（`beyondViewportPageCount=2`），切换保留滚动位置。
- **弹簧动画**：底栏点击走 `MainPagerState.animateToPage()`（`ui/components/PagerNavigation.kt`，stiffness 322.2 / damping≈0.9），与 SukiSU-Ultra 一致；手动滑动由 `syncPage()` 同步选中态。
- **底栏**：`NavigationBar`（containerColor = `surfaceContainer`），选中/未选中用 Filled/Outlined 图标对。
- **返回键**：非首个 Tab 时 `BackHandler` 先回首页 Tab，再回退导航栈。
- 子页路由（`detail/{id}`、`reader/{id}`、`tag/{tag}`、`downloads`、`settings/custom`、`about`）仍走 NavHost（淡入 + 侧滑过渡），自带折叠大顶栏、无底栏；阅读器自绘 chrome。
- 每个 Tab 页自带 `ExpressiveScaffold` + `LargeTopAppBar`（`exitUntilCollapsedScrollBehavior` 折叠），顶栏右侧下载图标进 `downloads`。

### 5.3 各页面（UI 重构后均为 SukiSU 风格：surfaceContainer 背景 + surfaceBright 卡片）
- `ExplorePage/ViewModel`：折叠大顶栏「轻小说文库」+ 圆角搜索条（surfaceContainerHighest 药丸形）+ 推荐/标签 SegmentedButton；首页栏目封面轮播、文字榜单（surfaceBright 卡片）、标签入口。
- `TagBooksScreen/ViewModel`：某标签下书籍列表（SegmentedColumn 卡片行）。
- `DetailScreen/ViewModel`：封面+基本信息 TonalCard（标签用 StatusTag 药丸）、阅读按钮、离线下载 TonalCard（TXT/EPUB + 进度）、简介 TonalCard。
- `BookcasePage/ViewModel`：书架 TonalCard 列表 + 排序（顶栏 DropdownMenu）+ 刷新。
- `DownloadsScreen/ViewModel`：下载任务 TonalCard 列表（进度/取消/完成路径），自带返回键。
- `SettingsPage`：SegmentedColumn 分组卡片——账号 / **外观**（深色模式下拉、纯黑模式、动态取色、手动种子色）/ **网络**（主站域名镜像切换，切换后自动清 Cookie 并重登）/ 阅读设置（进 `settings/custom`）/ 关于（进 `about`）。
- `AboutScreen`：关于页——应用图标（`painterResource(R.mipmap.ic_launcher)`）、版本号（`versionName`，现为 1.0.0）、GitHub 仓库与爱发电链接（`LocalUriHandler` 打开，链接常量在 `strings.xml`）、应用介绍与声明。
- `CustomizationScreen`：阅读器外观定制（仍在 `settings/custom`）——**浅色模式/深色模式各自独立的背景色与字体色**（默认纯白+纯黑 / 纯黑+纯白）、背景图片、字体/字号/字重/行距、简繁、四边边距、翻页方式等。
- `SettingsComponents.kt`：`SectionTitle` / `SettingLabel` 等复用组件。

### 5.4 通用组件（`ui/components/Expressive.kt`，material3 1.3 稳定 API 实现）
- `ExpressiveScaffold`：surfaceContainer 背景 + `expressiveTopAppBarColors` / `expressiveLargeTopAppBarColors`。
- `TonalCard`：surfaceBright + `shapes.large`，可选 onClick/onLongClick。
- `SegmentedColumn` / `SegmentedListItem` / `SegmentedSwitchItem` / `SegmentedDropdownItem` / `SegmentedRadioItem`：surfaceBright 分组卡片列表（首项大圆角、项间 2dp 缝隙）。**注意**：material3 1.3 没有可点击 `ListItem`，`SegmentedListItem` 为自定义 Row 实现（标题/次要文本/前导/尾随 + `pressClickable`）。
- `StatusTag` / `WarningCard` / `ExpressiveSwitch`（✓/✕ 拇指图标）。

---

## 6. 阅读器核心逻辑（重点，改动最频繁）

### 6.1 状态与数据流
- `ReaderViewModel`：`ReaderUiState(title, gid, volumes, flatChapters, tocLoading, currentChapter, currentCid, chapterLoading, error)`。
  - `openReader()`：取详情 → 目录 → 扁平化 → 依 `AppPreferences.resumeCid(bookId)` 续读（跳过「插图」章节）。
  - `loadChapter(cid)`：抓正文 → 空内容报错 → 保存进度（cid + 位置）→ **简繁转换**（`ZhConverterUtil.toTraditional`，`Dispatchers.Default`）→ 更新 State。
  - 阅读器外观相关 setter 直接转发到 `ReaderSettings`（全局生效）。
- `ReaderScreen` 顶部状态：`immersive`（沉浸）、`showSettings`、`showToc`、`autoTurn`、`suppressImmersiveUntil`。

### 6.2 沉浸模式与系统栏
- 进入阅读器默认 `immersive = true`；`LaunchedEffect(immersive)` 通过 `WindowInsetsControllerCompat.hide/show(systemBars())` 隐藏/显示系统栏，行为 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`。
- 点按屏幕三分区：左右 1/3 翻页（`pageMode && clickTurnPage`）否则唤出界面；中间 1/3 切换 `immersive`。
- `suppressImmersiveUntil`：翻页/拖进度条后延迟恢复沉浸。
- `VolumeKeyTurn` 全局单例桥接 MainActivity 的音量键；**约定：音量上键=上一页、音量下键=下一页**（`ReaderScreen` 中 `onVolumeUp→turnPage(-1)`、`onVolumeDown→turnPage(1)`）。

### 6.3 布局几何（易踩坑）
阅读器整屏为 `Scaffold(topBar=顶栏, bottomBar=底栏, content=BoxWithConstraints)`：
- 顶栏 `ReaderTopBar`（返回 + 书名）、底栏 `ReaderBottomBar`（上一章/自动翻页/目录/设置/下一章），均 `AnimatedVisibility(!immersive)` 垂直展开/收缩。
- `contentPadding`（**关键常量**，ReaderScreen.kt:223-234）：
  - 自动边距：`top=64.dp, bottom=80.dp, start=16.dp, end=16.dp`（恒定，与 immersive 无关，保证分页稳定）。
  - 手动边距：`top=maxOf(64.dp, rs.topPadding.dp)`，`bottom=maxOf(80.dp, rs.bottomPadding.dp)`，左右取用户值。
- **80dp 下边距**必须 ≥ 底部 chrome：状态指示器高 40dp（immersive 时位于屏幕最底）、底栏高约 80dp；`contentHeightPx = maxHeight - top - bottom` 用于分页。
- 状态指示器 `IndicatorBar`（ReaderScreen.kt:636-687，高 40dp）：沉浸时底部 `align(BottomCenter)` 显示 电量/时间/章节名/阅读百分比，`textColor.copy(alpha=0.7f)`。**电量/时钟状态只在本组件内维护**（30s 更新一次），避免整屏重组。
- 悬浮章节进度条 `Slider`：非沉浸时显示于 `padding(bottom=80.dp)`（位于底栏之上），拖动按页跳转/按滚动位置跳转。

### 6.4 分页算法（`Pagination.kt`，纯函数，Dispatchers.Default 执行）
输入：`density, chapter, maxWidthPx, maxHeightPx, fontSizeSp, lineSpacing`。
```
charsPerLine  = floor(maxWidthPx / fontSizePx × 0.95)   // 每行字数，留 5% 余量防裁切
linesPerPage  = floor(maxHeightPx / lineHeightPx)        // lineHeight = fontSize × lineSpacing
```
然后**逐字符模拟换行**切页（不是纯字数切分）：
- 逐字符推进；遇 `\n` 或行满（累计 charsPerLine 字）即换行；
- 行数累计到 `linesPerPage` 时翻页（分页点支持在行中断开）；
- 每页 `substring(...).trim('\n')`，空页跳过；
- 尾部剩余文本单独成页；标题并入第一页；图片各自成独立页。

> **为什么不能只用字数切分**：`charsPerPage = charsPerLine × linesPerPage` 假设每行排满，但轻小说大量短段落每段末尾是"残行"（不满一行仍占一行高度），实际行数会远超容量导致底部裁切。逐行模拟把残行计入，保证每页渲染行数 ≤ linesPerPage。
> 触发重分页的 `LaunchedEffect` keys：`chapter, rs.fontSize, rs.lineSpacing, contentWidthPx, contentHeightPx, pageMode`。

### 6.5 翻页与阅读体验
- 侧滑：`HorizontalPager` + `rememberPagerState { pagedChapters.size }`；滚动：`ScrollContent`（`verticalScroll`）。
- 点按左右翻页方向受 `rs.pageTurnDirection`（向左/向右）控制。
- 自动翻页：`autoTurn` 启动 `LaunchedEffect` 定时器按 `rs.autoTurnInterval` 秒翻页，到章节末尾自动进下一章。
- 章节切换 `LaunchedEffect(chapter)`：滚动/分页归零，短时抑制沉浸。
- 正文 Text：`fontFamily = fontFamilyFor(rs.fontFamily)`，`fontSize = rs.fontSize.sp`，`lineHeight = (rs.fontSize * rs.lineSpacing).sp`，`color = textColor`。
- 插图页：`SubcomposeAsyncImage` + `ImageRequest` 带 `Referer: https://www.wenku8.net/`。

### 6.6 阅读器设置项（ReaderSettingsState，含默认值）
`darkMode(system)/dynamicColor(true)/seedColor/amoled(false)/backgroundMode(color|image)/readerBackgroundLight(纯白)/readerTextColorLight(纯黑)/readerBackgroundDark(纯黑)/readerTextColorDark(纯白)/backgroundImagePath/fontFamily(default|sans|serif|mono)/fontSize(18)/fontWeight(400)/lineSpacing(1.8)/traditionalChinese(false)/scrollMode(false)/volumeKeyTurnPage(true)/autoNextChapter(false)/pageTurnDirection(true)/autoTurnInterval(10s)/clickTurnPage(true)/autoPadding(true)/topPadding(24)/bottomPadding(16)/leftPadding(20)/rightPadding(20)`。
- 阅读器配色按主题模式分离：`ReaderScreen` 依 `darkMode`（含跟随系统）取 `*Light` 或 `*Dark` 两套背景/字体色；旧 prefs 键 `reader_bg`/`reader_text_color` 自动迁移为浅色模式值（`load()` 中兜底）。

---

## 7. 已实现功能 / 开发进度

### 7.1 已完成（对照 `技术性文档(只读勿动)/plan.md`）
- [x] 沉浸式阅读（进入即沉浸，滑动/翻页后隐藏顶底栏与系统栏；点中间呼出）
- [x] 底栏切换按钮（图标 + 「上/下一章」小字）
- [x] 底栏上方悬浮章节进度条（拖动跳转）
- [x] 侧滑翻页（默认）+ 点按左右翻页
- [x] 自动翻页按钮（可设间隔）
- [x] 底栏设置图标 → 设置浮窗/页面
- [x] 状态指示器：右下电量（电池图标+百分比）、左下本章进度（百分比）、左上章节名、右上时间
- [x] 设置：滚动/侧滑切换、音量键翻页（默认开）、自动下一章（默认关）、翻页方向（默认向左）、自动翻页间隔输入
- [x] 阅读器外观自定义（背景色/图、正文字色、字体、字号、行距、简繁、四边边距）

### 7.2 UI 重构（2026-08，参考 `技术性文档(只读勿动)/SukiSU-Ultra-main` Material 侧）
- [x] 主题：完整 surfaceContainer* 角色色板（手工种子色生成）+ AMOLED 纯黑模式 + expressive Typography；动态取色不变。
- [x] 主界面：三 Tab 改 `HorizontalPager` + 弹簧动画（`PagerNavigation.kt`），底栏 `NavigationBar` 用 surfaceContainer 同色；返回键先回首页 Tab。
- [x] 全页面卡片化：折叠大顶栏（LargeTopAppBar + exitUntilCollapsed）、surfaceBright 卡片列表（TonalCard / SegmentedColumn）、StatusTag 药丸、圆角搜索条。
- [x] 设置页新增「外观」分组：深色模式（跟随系统/浅色/深色）、纯黑模式、动态取色开关、手动种子色选择。
- [x] 阅读器（ReaderScreen/ViewModel/Pagination）未改动。

### 7.3 近期针对阅读器的修复记录
1. **状态指示器遮挡正文** → 下边距最小 80dp（`maxOf(80.dp, …)`），指示器（40dp）不再压字。
2. **纯字数分页裁切** → 改为「charsPerLine/linesPerPage + 逐行模拟」分页，段落残行计入，不裁切。
3. 网络层已跟进 `wenku8-bypass-analysis.md` 的清单：GB18030 解码、`Sec-Fetch-*` 头、多镜像域名、随机 Android UA、WebView/Cronet/OkHttp 三级兜底、自适应限速 + 429 退避。

### 7.4 性能优化（2026-08，卡顿 + 高刷新率）
- **阅读器整屏重组修复**：电池/时间状态原提升在 ReaderScreen 顶层，每 15s 触发整个阅读器重组；已下沉到 `IndicatorBar` 内部（30s 更新），重组范围缩到指示器本身。
- **封面按尺寸解码**：新增 `ui/common/CoilRequests.kt` 的 `rememberCoverRequest()`（带 Referer + Coil size 提示），首页/书架/标签/详情封面不再以原图全尺寸解码，列表滚动更顺滑、内存更省。
- **列表封面默认关闭 crossfade**：滚动时批量出现的封面若逐个播放淡入动画会与滚动帧竞争主线程；`rememberCoverRequest()` 默认 `crossfade=false`，仅详情页单图开启。
- **TonalCard 默认 0 阴影**：`Card` 默认 1dp 阴影在滚动时逐帧重绘，surfaceBright 层级对比已足够，去掉阴影绘制成本。
- **状态类 @Immutable**：`ExploreUiState`/`TagSection` 补 `@Immutable`，配合已有 @Immutable 的 Models，减少不必要的重组。
- **标签页批量更新**：`loadTags()` 每抓 4 个标签才刷新一次 `tagSections`（原每标签刷新一次导致整列反复重组）。
- **高刷新率**：`MainActivity.requestHighRefreshRate()` 请求同分辨率最高刷新率模式（API 30+ `preferredDisplayModeId` / API 26-29 `preferredRefreshRate`）。
- 动画均为时间驱动（spring/tween/Choreographer），120Hz 屏上自动按高帧率渲染，无固定 60fps 限制。

### 7.5 gfxinfo 实测优化（2026-08，基于 `dumpsys gfxinfo` 逐帧分析）
实测结论（120Hz 屏，帧预算 8.3ms）：50% 帧仅 5ms，但输入帧周期性 15–32ms、偶发 100–950ms 停顿；GPU 99 分位仅 6ms → **瓶颈在 UI 线程的组合/测量/布局 + 图片批量解码回调**（High input latency 1201）。
- **封面行 `take(6)`**：首页每行最多 6 本（原全量），图片总量减半 → 减少滚动时解码批量回调与 Tab 切换时整页绘制节点数。
- **LazyColumn `contentType`**：探索页列表项复用提示。
- **`android:largeHeap="true"`**：图片批量解码的内存压力下减少 GC 停顿（实测 5 次 ~950ms 大停顿疑似 GC）。
- 保留 `beyondViewportPageCount = 2`（三页预组合）：切换时纯滚动+绘制，不触发组合。
- 遗留：滚动模式阅读器长章节整章排版、Tab 切换双页绘制的固有成本。
- **首页滚动优化（参考 LightNovelReader）**：① 滚动时**预取下一个区块封面**（`HomeBody` 监听 `firstVisibleItemIndex`，用 `Coil.imageLoader().enqueue` 提前解码），进入视口时图片已就绪，消除「区块组合+解码同帧爆发」；② 封面加**占位底色**（`surfaceContainerHighest`），加载中视觉稳定无弹出感；③ 封面行 4 本 + 普通 `clickable`（去动画状态）；④ 主 Tab 顶栏改为静态 64dp（去折叠布局级联）。

### 7.6 待办 / 可扩展方向（建议）
- 分页目前用**估算**（宽/字号），可改用 Compose `TextMeasurer` 精确测量后切页（此前实现过基于 TextMeasurer 的 `getSlipStrings`，后为性能改回估算）。
- **滚动模式长章节**：`ScrollContent` 用单个 `Text(chapter.text)` 一次性排版整章，超长章节打开时首帧排版偏慢；可改为按段落 `LazyColumn` 增量排版（注意保持阅读位置语义）。
- 章节进度条在非沉浸时可能覆盖正文最后约 1~2 行（浮动层叠于文本之上），如需避免可调整其位置/透明度。
- 无 git 提交历史（仓库尚无 commit），建议接手后先 `git init`/首次提交再开发。
- 无自动化测试；分页算法 `paginateChapter` 是纯函数，适合补单测（当前无 test 源集）。
- 未做：日/周排行榜、书单、多书架分组、阅读统计、外部打开 EPUB/TXT、深链（`reader/{id}` 已可被外部跳转）。

---

## 8. 参考文档与版本管理

- 版本号管理方案见根目录 **`VERSIONING.md`**（versionName SemVer + versionCode 规则、发布三件套）。

`技术性文档(只读勿动)/`：
- `wenku8-api.md` —— wenku8.net 全接口逆向文档（登录/搜索/详情/目录/正文/下载/书架、编码约定、限流策略）。**优先读它再改数据层。**
- `wenku8-bypass-analysis.md` —— Cloudflare 绕过分析（浏览器头/多域名/重试）；其 §5 对当前实现的对照表部分已过时（项目现已补上所列手段）。
- `plan.md` —— 阅读器开发计划（已完成，见 §7.1）。
- `LightNovelReader-refactoring/` —— 一个更完整的开源轻小说阅读器参考工程（含插件/代理/EPUB 模块），仅作思路参考。
- `SukiSU-Ultra-main/` —— UI 重构参考工程（2026-08 已按其 Material 侧设计语言重构本应用外壳与页面；其依赖 miuix-kmp / navigation3 / material3-1.4-expressive 未引入）。
- `wenku8account.txt` —— 内置账号（对应 `DefaultAccount.kt`）。

---

## 9. 构建与运行

```bash
# 项目根目录无 gradlew wrapper。推荐直接用 Android Studio 打开本目录同步后 Run。
# 命令行（本机已装 Gradle 8.9 于 ~/.gradle/wrapper/dists/gradle-8.9-bin/...）：
$env:JAVA_HOME="<JDK17或Android Studio 的 jbr>"
gradle :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

⚠️ **命令行构建注意**：
- AGP 8.5.2 要求 **JDK 17+**；系统默认 `java` 是 JDK 1.8，直接跑会报 `Dependency requires at least JVM runtime version 11`。需用 Android Studio 自带 JBR 或设置 `JAVA_HOME`。
- 本机路径示例：`C:\Users\a3451\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat`。
- 本仓库无 `gradlew`；若在 Android Studio 里首次 Sync 会自动生成 wrapper 与 `~/.gradle` 缓存。

---

## 10. 开发约定与易踩坑点（给后续 Agent）

1. **编码**：wenku8 全部为 GB18030 解码、GBK 表单编码；任何新网络方法都要走 `getHtml`/`gbkForm`，不要直接 `String(bytes)`。
2. **限速**：新请求必须走 `execute()`（自带限速/退避）；批量请求注意 429。
3. **gid**：`/novel/` 系列 URL 都要 gid；`BookInfo.groupId` 缺失用 `id/1000` 兜底。
4. **阅读器边距**：改 `contentPadding` 必须保证 top≥64、bottom≥80 的约束，否则状态指示器/底栏会遮字；且该值不得随 immersive 变化，否则分页抖动。
5. **分页**：改 `Pagination.kt` 后注意 `LaunchedEffect` keys 是否需要更新；分页在 `Dispatchers.Default` 执行，别放主线程。
6. **设置**：阅读器与主题共用 `ReaderSettings`；新增设置项需同时改 `ReaderSettingsState` 字段、`load()`、`emit()` 的 prefs 读写、`CustomizationScreen` 的 UI，三处保持同步。
7. **文案**：所有 UI 文本走 `res/values/strings.xml` + `stringResource`；错误消息用 `UiText`（避免 ViewModel 持 Context）。
8. **沉浸系统栏**：切换用 `WindowInsetsControllerCompat`；离开阅读器时 `DisposableEffect` 恢复显示系统栏。
9. **图片防盗链**：所有 Coil 请求需带 `Referer: https://www.wenku8.net/`。
10. **命名/风格**：与现有代码一致即可；文件内大量中文注释，新增注释可用中文。
11. **material3 版本约束（BOM 2024.09.02 = material3 1.3.0）**：没有可点击 `ListItem`、`ShortNavigationBar`、`SegmentedListItem`、`LargeFlexibleTopAppBar` 等 1.4 expressive API，也没有 `Pager(overscrollEffect)`。需要此类能力时：Tab 用 `HorizontalPager` + `PagerNavigation.kt` 弹簧、列表行用 `components/Expressive.kt` 的 `SegmentedListItem`（自定义 Row）、顶栏用 `LargeTopAppBar`。新增 SukiSU 风格组件统一放 `Expressive.kt`。
12. **主题**：`Wenku8ReaderTheme(darkTheme, dynamicColor, seedColor, amoled)`；手工色板在 `Theme.kt` 的 `manualScheme()`，要补齐 surfaceContainer* 全角色，勿退回旧版只有 primary/background 的残缺色板。`amoled` 与 `darkMode/dynamicColor/seedColor` 一样存于 `ReaderSettings`（prefs `settings`）。

---

## 11. 关键文件行号索引（阅读器）

| 内容 | 位置 |
|---|---|
| contentPadding 常量与 contentWidth/Height | `ReaderScreen.kt:223-242` |
| 异步分页 LaunchedEffect | `ReaderScreen.kt:244-268` |
| 沉浸系统栏控制 | `ReaderScreen.kt:151-167` |
| 点按翻页/唤出 | `ReaderScreen.kt:339-357` |
| 正文渲染（page Text / 插图） | `ReaderScreen.kt:384-423` |
| 状态指示器（IndicatorBar） | `ReaderScreen.kt:449-468, 636-687` |
| 悬浮章节进度条 | `ReaderScreen.kt:470-509` |
| 分页算法 | `Pagination.kt:22-62` |
| 简繁转换 + 进度持久化 | `ReaderViewModel.kt:129-172` |

## 12. 关键文件行号索引（UI 重构后）

| 内容 | 位置 |
|---|---|
| 主 Tab 分页 + 弹簧动画（MainPagerState/springAnimateToPage） | `components/PagerNavigation.kt` |
| 主外壳（NavHost + NavigationBar + BackHandler） | `MainScaffold.kt` |
| 三 Tab 宿主（HorizontalPager） | `MainScaffold.kt`（`MainPagerScreen`） |
| SukiSU 风格组件（TonalCard/ExpressiveScaffold/Segmented 系列/StatusTag/WarningCard/ExpressiveSwitch） | `components/Expressive.kt` |
| 手工色板（surfaceContainer* 全角色 + AMOLED） | `theme/Theme.kt`（`manualScheme`） |
| 全局 Typography | `theme/Type.kt` |
| 主题种子色预设 | `theme/Colors.kt` |
| 探索页（折叠顶栏/搜索/推荐/标签） | `explore/ExploreScreen.kt`（`ExplorePage`） |
| 设置页（分组卡片 + 外观） | `settings/SettingsScreen.kt`（`SettingsPage`） |
| 详情页卡片化 | `detail/DetailScreen.kt` |
