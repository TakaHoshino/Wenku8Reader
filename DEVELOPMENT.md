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
| UI | Jetpack Compose（BOM 2026.05.01，ui/foundation 1.11.2）+ **Material 3 Expressive**（material3 1.5.0-alpha18）+ material-icons-extended |
| 架构 | 单 Activity + Navigation-Compose + ViewModel（手写 DI 容器，无 Hilt） |
| 网络 | OkHttp 4.12 + Cronet（chromium net）+ WebView 兜底；Coil 2.7 加载图片 |
| 依赖注入 | 手写 `AppContainer`（`di/AppContainer.kt`） |
| 异步 | Kotlin Coroutines + StateFlow |
| 简繁 | opencc4j（`com.github.houbb:opencc4j`） |
| minSdk / target / compile | 26 / 34 / **37**（material3 1.5 要求 minCompileSdk ≥ 35；miuix 0.9.x 要求 37） |
| Gradle | Gradle 9.7.1（wrapper 已入库，含 `gradlew`/`gradlew.bat`），AGP 9.4.1，Kotlin 2.3.21（AGP 9 内置 Kotlin，见 §5.6） |
| 备用 UI 风格 | MIUIX（`miuix-ui` / `miuix-blur` / `miuix-preference` **0.9.1**，实验性，设置→实验性切换，见 §5.6） |

关键 build 文件：`app/build.gradle.kts`（模块配置）+ `gradle/libs.versions.toml`（**版本目录**，
所有依赖与插件版本集中在此）+ 根 `build.gradle.kts`（AGP/Compose 插件声明与 KGP classpath 覆盖）。
用仓库自带的 wrapper 构建即可：`./gradlew :app:assembleDebug`。

> **加依赖请走版本目录**：在 `gradle/libs.versions.toml` 的 `[versions]`/`[libraries]` 里登记，
> 再在 `app/build.gradle.kts` 里用 `libs.xxx.yyy` 引用；不要往 build 文件里写死坐标。
> 目录里的版本号以**实际解析到的版本**为准（跑
> `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 看直接依赖那一层
> `x.y.z -> a.b.c` 的右侧），例如 `core-ktx` 与 `lifecycle` 早已被传递依赖顶到 1.18.0 / 2.9.4。
> 唯一的例外是根 `build.gradle.kts` 的 `buildscript` classpath：它在 accessor 可用之前求值，
> 只能写字面量，必须与目录里的 `kotlin` 保持一致。

> ⚠️ **JDK 版本要求 17–21（CI 用 21）**：Gradle 9.7.1 支持 JDK 17–24，JDK 25 仍不受支持。若 `JAVA_HOME` 指向过新的 JDK（例如 25），构建会在启动阶段直接失败并只打印版本号（`What went wrong: 25.0.2`）。此时把 `JAVA_HOME` 指到 JDK 17/21（如 Android Studio 自带 JBR 或 `C:\Users\<用户>\.jdks\jbr-21.x`）即可：
> ```powershell
> $env:JAVA_HOME="C:\Users\<用户>\.jdks\jbr-21.0.11"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
> .\gradlew.bat :app:assembleDebug
> ```

AndroidManifest：声明 `INTERNET` / `VIBRATE` 权限。安全加固：
- 明文流量改为 `networkSecurityConfig` **白名单**（仅 `app.wenku8.com` 保留 http —— 官方 App API 无 HTTPS 端点；封面/插图/站点页面本就都是 HTTPS），不再全局 `usesCleartextTraffic="true"`；
- `allowBackup` 保留但通过 `fullBackupContent`（API ≤30）与 `dataExtractionRules`（API 31+）**排除 `account`/`cookies`/`library`**，避免凭据、会话 Cookie 与书架数据进入云备份或 `adb backup`；
- 有 FileProvider（更新安装用 `cacheDir/updates/`）；无存储权限（下载走 MediaStore）。

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
        │       ├── AppPreferences.kt  # 书架排序 + 更新检查节流（SharedPreferences）
        │       ├── ReaderSettings.kt  # 全局设置源（DataStore + 同步可读 StateFlow）
        │       ├── LibraryStore.kt    # 书架读写（Room: books）
        │       ├── ReadingProgressStore.kt # 进度/已读标记（Room: reading_progress）
        │       ├── LocalDataMigration.kt   # 旧 SharedPreferences → Room 的一次性搬迁门
        │       ├── db/               # Room 实体/DAO/AppDatabase + 搬迁解析与校验
        │       └── DefaultAccount.kt  # 内置账号（首启静默登录用）
        ├── data/repository/Wenku8Repository.kt  # 统一仓库层（Result 包装）
        └── ui/
            ├── AppViewModelProvider.kt    # 手写 ViewModel.Factory
            ├── MainScaffold.kt           # 分页式主 Tab（HorizontalPager + 弹簧动画）+ NavHost 子页栈
            ├── navigation/Routes.kt      # 类型安全路由常量（MAIN 为三 Tab 宿主路由）
            ├── common/                   # UiText.kt / ReaderAppearance.kt(fontFamilyFor) / CoilRequests.kt(封面请求，带 Referer + 目标尺寸解码)
            ├── theme/Theme.kt            # MD3 主题（动态取色 + 种子色 + AMOLED 纯黑）
            ├── theme/Type.kt             # 全局 Typography（M3 Expressive 强调字重 + 中文排版修正）
            ├── theme/Colors.kt           # 种子色预设
            ├── components/Anim.kt        # 通用动画（pressClickable 按压缩放）
            ├── components/Expressive.kt  # M3 Expressive 组件层（Scaffold/顶栏/TonalCard/装饰形状/空状态/加载进度/Segmented 系列/ExpressiveSwitch）
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
- **内置分类清单**：`BUILT_IN_TAGS`（50 个标准分类，参考 LightNovelReader 内置 tagList）作为「标签」页分类的**直接来源**——`tags()` 秒回、无需登录/网络；每分类书籍仍在线抓取（`tagBooks(tag, page)`，需登录；**分页**：`tags.php?t=xxx&v=1&page=N`，TagBooksScreen 逐页追加、按 bookId 去重、空页或下一页无新书时停止）。`isLoggedIn()` 已简化为会话 Cookie 判据（去掉无意义的 `index.php` 联网检查）。
- **三级抓取栈**（`fetchWithBypass`，用于 tags/tagBooks/首页）：WebView（真浏览器跑 CF JS 挑战，读回 DOM）→ Cronet（TLS 指纹过 CF）→ OkHttp 随机 Android UA，逐镜像尝试。**首页/标签/标签书单均先走 `tryDirect` 快路径**（cookie-first，参考 LightNovelReader：已有 cf_clearance 时用绑定 UA 直连一次通过，跳过 WebView），仅失败/解析为空时升级到三级栈。其余接口（bookInfo/chapters/chapterContent）先网页直连，失败后走 App API 兜底。
- **登录去重（`loginMutex` + 双检）**：启动静默登录、探索页 `tags()`、书架 `bookcase()` 可能在启动瞬间同时发现"未登录"并各发一次登录请求。`ensureLoggedIn()` 与 `login()` 共用同一把 `Mutex`，进入锁后**再次检查**登录态（排队的协程直接复用已完成的登录）。入口路径都收敛到 `loginInternal()`，不存在绕过锁的调用点。
- **隐藏 WebView 的生命周期与并发**：`webViewGet()` 用 `webViewMutex` 串行化（同一时刻只有一个 WebView 解挑战：多开会在主线程同时跑 JS、放大内存与风控样本，而串行化后第二个请求通常直接复用刚拿到的 cf_clearance 走快路径）；`cont.invokeOnCancellation` 保证协程被取消（离开页面/超时/VModel 销毁）时也会销毁 WebView，统一走 `destroyWebView()`（stopLoading → about:blank → clearHistory → removeAllViews → 换掉 WebViewClient → destroy，每步 runCatching）。超时分两层：单次解挑战 15s，等待锁 + 执行整体 25s。
- **Cronet 挂起等待**：`cronetGet()` 不再用 `CountDownLatch.await(8s)`（每个请求独占一个 IO 线程空等，取消也要等超时才释放），改为 `suspendCancellableCoroutine` + `withTimeoutOrNull`，线程在等待期间释放，取消直接传播到 `UrlRequest.cancel()`。
- **App API 兜底**（参考 LightNovelReader 的 `Wenku8AppDataSource`）：`bookInfo/chapters/chapterContent` 在网页失败后走官方 App API（`http://app.wenku8.com/android.php`，POST `request`(base64)/`timetoken`/`appver` + Dalvik UA，社区中继 `https://wenku8-relay.mewx.org` 兜底），串行限流 + 请求间随机 1.5~2s 延迟。**2026-08 实测两个端点均已失效**（官方回 "Welcome"、中继 400），保留为无害兜底：失败极快，不影响网页主路径。
- **内存缓存**（参考 LightNovelReader 的 2h Cache）：`bookInfo`/目录缓存 2h、章节缓存 30min，仅缓存成功结果，减少重复请求与被拦概率。三者均带 **LRU 容量上限**（info 128 / toc 32 / chapter 64 条）：只有 TTL 时，长读一本书会让章节正文无限累积在内存里。
- **本地磁盘缓存**（`data/local/HtmlDiskCache.kt`）：抓取内容按 URL 落盘到 `filesDir/html_cache`（卸载前持久），命中且未过期直接返回、避免二次加载；仅缓存非 CF 挑战/非登录页。TTL：首页 1h、详情/目录 7d、章节正文 30d、标签书单 1d；总量超 30MB 时按最旧优先清理。内存缓存（快）→ 磁盘缓存（持久）→ 网络，三级取数。
- **自适应限速**：全局 `lastRequest` 间隔基数 600ms × `rate`（成功 ×0.85 回落、失败 ×2 放大，上限 ×8）；`RATE_CODES = {403,429,500,502,503,504}` 触发指数退避重试（1.5s→3s→…上限 30s，3 次）。搜索额外 5s 最小间隔；命中站点限流错误页时**有界重试**（最多 3 次、每次等 5s，超出即报错）——原实现是无上限的递归调用，站点持续限流时会一直挂着且无法取消。
- **取消语义**：所有 `runCatching` 均改为「不吞 `CancellationException`」的封装（`Wenku8Client` / `Wenku8Repository`），否则页面被取消会被误判成"直连失败"，继续走 WebView/Cronet 绕过栈。
- **搜索精确命中**：POST `/so.php`，302 到 `/book/{id}.htm` 时直接解析单书详情。
- **Referer**：`refererFor()` 按请求自身 host 生成，镜像安全。

### 4.3 解析（`Parsers.kt`）
纯正则（`java.util.regex.Pattern`）。注意：
- `parseChapter`：用 `indexOf("<div id=\"content\">")` 到 `indexOf("<div id=\"footlink\"")` 截取正文（**不用正则**，防嵌套 div）；剥离 `<ul id="contentdp">` 水印；`<br>/</p>` → `\n`；去标签；实体解码；`\u3000` → 两个半角；按行 trim、合并连续空行。
- `splitFullTxt(txt, volumes)`：用目录树生成期望章节头（`卷名 章节名`），在 TXT 行里**一次遍历、按行号游标线性定位**并切分章节，用于 EPUB 生成（相比"每行 trim 两次 + 文本→行号全量 map"，8MB/1000 章实测 53ms→38ms、55MB→43MB）。
- `parseBookcase`：按 `bid` 分组，无 `cid` 链接为书名、有 `cid` 为最新章节。
- `parseHomepage` / `parseBookList`：首页 `<div class="block">` 切块、书籍列表（两者共用 `homeBookAt` 解析条目；封面统一经 `absolutizeCover` 补全域名）。
  > 注：`parseTags` 已删除——标签来源改为内置清单 `BUILT_IN_TAGS`，不再抓取页面。

### 4.4 Cookie（`CookieStore.kt`）
实现 `okhttp3.CookieJar`，登录成功后 `persist()` 落盘；`cronetGet`/`webViewGet` 手动拼 `Cookie` 头。

### 4.5 下载（`DownloadEngine` / `FileSaver` / `EpubBuilder`）
- `DownloadEngine`：`Dispatchers.IO` 单协程任务，`StateFlow<Map<Int, DownloadJob>>` 暴露进度；`cancelFlags`（`ConcurrentHashMap`，跨线程可见性有保证）支持取消，任务作用域由 `AppContainer` 统一注入。**经 `Wenku8Repository` 取数**（不再直连 `Wenku8Client`，保持分层一致）；落盘失败（`FileSaver.saveDownload` 返回 null）标记为 `FAILED` 而非"完成"。TXT 优先站点全本直链（`dl.wenku8.com/down.php?type=txt|utf8|big5`），失败回退逐章抓取；EPUB 先取全本 TXT 用 `splitFullTxt` 切章（成功率 <60% 回退逐章），再 `EpubBuilder.build` 打包（EPUB3：mimetype STORED + container/opf/nav/ncx/css + 每章 xhtml；章节 id/文件名统一 `%04d`；uid 取书名+作者 SHA-256 前 8 字节，避免中文书名退化成同一个 uid；`dcterms:modified` 用构建时刻）。
- `FileSaver`：API 29+ 走 MediaStore（`Downloads/Wenku8/`），以下写应用私有目录。**重名即覆盖**：插入前先按 `RELATIVE_PATH` 查回本目录的文件并删掉同名/自动重命名副本（判定见 `isDuplicateDownloadName()`：标准形态 `书名 (1).txt` 与部分 ROM 的 `书名.txt(1)` 都算副本，另有单测覆盖），否则 MediaProvider 会把新文件改名成 `书名 (1).txt`，每重新下载一次就多一份副本；写入用 `IS_PENDING`，其他应用在写完前看不到半截文件。

### 4.6 本地存储
- **用户数据（Room，`data/local/db/`）**：书架与阅读进度在 2026-09 从 SharedPreferences 迁到 Room（库文件 `wenku8.db`）。
  - `LibraryStore` → 表 `books`：书目快照 + 入架时间，字段与旧 `library.json` 逐字段对齐；按 `addedAt` 倒序查询。
  - `ReadingProgressStore` → 表 `reading_progress`：`resumeCid`（继续阅读）、`lastReadAt`（最后阅读时间，**清理过期记录的唯一依据**）、`totalChapters`（书架进度分母）、`finishedCids`（目录页"已读"标记 + 重读重置）。
  - **一次性搬迁**：`LocalDataMigration` 是唯一入口，两个 store 的每次读写都先 `ensure()`——首帧不会读到"空书架"或丢续读位置。顺序是 解析 → upsert → **逐条读回比对** → 全部一致才清旧数据；任何异常或丢条都保留旧数据、下次启动重试（幂等：主键 upsert 不产生重复行）。旧 `library`/`reading` 偏好键已清理，文件保留作降级兜底。
  - 清理过期阅读用 `ReadingProgressStore.cleanupStale(keepDays)`，判定抽成纯函数 `staleProgressBookIds()` 并单测（`ReadingProgressCleanupTest`）——**没有时间戳的旧记录一律保留**，避免凭猜测删用户数据。
- **设置（DataStore）**：`ReaderSettings` 仍是**全局唯一设置源**，`StateFlow<ReaderSettingsState>` 同时驱动 MainActivity 主题、阅读器与设置页。存储改为 `files/datastore/settings.preferences_pb`，键名与旧 `settings.xml` **完全同名**，首次运行把旧值搬进来并置 `migrated_from_prefs` 标记（旧文件保留作降级兜底，置位后不再读）。对外仍**同步可读**：`Application.onCreate` 里同步读一次首值，避免启动瞬间按默认值渲染出"设置被重置"的闪烁；写入立即更新内存、再经 conflated 通道按顺序合并落盘（Slider 拖动时不会后写先落）。`hapticsStrength`/`cacheMaxMb` 读取时夹紧；逐字段编解码有单测（`ReaderSettingsCodecTest`）。应用内语言由 `MainActivity.attachBaseContext` 经应用级静态引用读取——那个时点 `activity.application` 尚未赋值。UI 重构新增字段：`amoled`（纯黑模式，深色下 surface 压真黑，仅影响应用主题，不影响阅读器纸张色）。
- **其余偏好（SharedPreferences）**：`AppPreferences`（prefs：`ui`）只剩书架排序与更新检查节流/跳过版本，**不保存任何账号密码**（原先的 `account` 明文凭据接口全仓无调用点，已整体移除；登录态由 `CookieStore` 的会话 Cookie 承担）。
- `ReadingStatsStore`（prefs：`reading_stats`）：阅读时长，按「书 + 日期」聚合秒数（一书一天一条），`version` 流通知 UI 重算（详见 §4.7）。
- `DefaultAccount`：**内置共享账号，本应用唯一且全程使用的账号**——不提供登录/退出/切换入口，首启与切换镜像时静默登录。凭据为硬编码常量（不再声称从 `wenku8account.txt` 读取，该文件仅作运维记录）。

### 4.6.1 多书架（实验性，2026-09）

- **开关**：`ReaderSettingsState.multiShelfEnabled`（DataStore 键 `multi_shelf_enabled`，**默认 false**），
  位于设置 → 实验性，Material 与 MIUIX 各一个开关。关闭时书架页与收藏路径与单书架版本逐像素一致，
  且**不删任何数据**，重新开启即恢复。
- **数据**：书的归属复用 `BookEntity.shelf` 列（迁移时就已带上，老用户零迁移）；**书架清单**
  （只存用户自建的，默认书架隐式存在）存独立的 DataStore 文件
  `files/datastore/shelves.preferences_pb`，键 `list` = JSON 数组（保序）。
  不放 Room 是刻意的：那需要 `AppDatabase` 升版 + 手写 Migration，而本工程没有
  Robolectric/instrumentation，迁移无法自动化验证，而书架清单只是个短字符串数组。
- **纯逻辑**：`data/local/ShelfOps.kt`（重名校验、增删改、归属兜底）与 `ShelfStore` 的编解码
  都是可单测的纯函数（`ShelfOpsTest` / `ShelfStoreCodecTest`）。
  「默认书架不可删、不可改名、永远至少一个书架」由"默认隐式存在"这一结构保证。
- **顺序约束**（两处都是先动书、再动清单，反了会让书架凭空清空）：
  删除书架 → 先 `UPDATE books SET shelf='默认'`，再移除清单项；
  重命名 → 先批量改书的归属，再改清单。
- **UI**：书架页顶部切换条（Material 用 `FilterChip` 横滚、MIUIX 用主题色胶囊文字）、
  「管理书架」二级页（两套独立实现，共用 `ShelfManageViewModel`）、
  长按卡片 → 移动到其他书架、详情页收藏时可选目标书架。
  详情页的**收藏 / 取消收藏 / 移动到书架三处共用同一个复选框弹窗**
  （`shelf_picker_*`）：选中态一律用复选框表达；取消收藏时弹窗不可勾选，
  只用来指出"当前在哪个书架"并要求确认——此前点一下星标就直接移除，没有确认步骤。
  多书架开关关闭时这些弹窗一律不出现，收藏仍是"点一下即刻收藏/移除"。

### 4.7 阅读统计（`ReadingStatsStore` / `ui/stats/`）

- **埋点**：`ReaderScreen` 内 `ReadingTimeTracker`——仅应用前台（Lifecycle RESUMED）且正文可见时累计，每 60s 整段写入并持久化，退出阅读器时冲刷余量（不丢最后不足 60s 的阅读）；1s 定时器仅在组合期内存在，开销可忽略。
- **存储**：按「书 + 日期」聚合秒数（一书一天一条），SharedPreferences JSON；`persist()` 后 `version` 流 +1，UI 据此重算。
- **聚合算法**：先按天/按书**分别累计秒数**，再统一 `ceil(秒/60)` 成分钟（不足 1 分钟按 1 分钟）——逐条 ceil 再求和会有累加误差（两条 30s 同日应计 1 分钟而非 2 分钟）；聚合在 `Dispatchers.Default` 执行。
- **热力图**：GitHub 风格周列矩阵（列=周、行=周一..周日），尺度（本周/本月/本年/全部）由 ViewModel 的 `rangeOf` 定界，`buildWeeks` 铺格子（范围外/未来为 null），`buildLabels` 每月首列标 "M月"；**参考 LNR 优化**：① 汇总卡行（累计/本周/连续阅读天数/日均，日均=总分钟÷活跃天数）；② 色阶改 LNR 风格——工作日绿 `#329c32`、周末蓝 `#29538f`，按 alpha（0x44/0x8C/0xFF）递增区分 1~10/11~30/>30 分钟三档，0 分钟为中性灰；③ 图例（少→多，工作日/周末两行）；④ **点日期出当日详情卡**（当日总分钟 + 当日每本书明细，`dayTotalMinutes` 先累计秒再 ceil 一次避免逐条取整误差）；⑤ 触觉反馈。书籍列表按时长降序（点条目跳详情）。

### 4.8 详情页增强（作者 / Tag / 独立目录页 / 重读重置 / 书架已读统计）
- **作者**：详情页作者名强调色 + 可点击 → `author/{name}` 路由 → `AuthorBooksScreen`（复用 `repository.search(name, byAuthor=true)` 按作者搜索接口）。
- **Tag**：`StatusTag` 增加可选 `onClick`；详情页 Tag 可点击 → 复用 `tag/{tag}` 路由（TagBooksScreen，**分页加载该标签下全部书籍**）。
- **目录页**：`toc/{id}` 路由 → `TocScreen`（独立二级页）：分卷可折叠（`AnimatedVisibility`），**默认全部展开、全卷已读自动折叠**，顶栏可全部展开/折叠；已读章节灰色 + "已读"标记，当前章节主题色加粗；点章节 → `reader/{id}?cid=...`（阅读器新增可选 `cid` 起始章节参数）。
- **章节完成状态**：`ReadingProgressStore.finishedChapters(bookId)`（Room 表 `reading_progress.finishedCids`，见 §4.6）；阅读器读至章节 100%（页模式最后一页 / 滚动模式到底，`snapshotFlow` 检测）→ `markFinished`；**重读重置**：`loadChapter` 进入已完成章节时立即 `resetFinished`（回到未完成），再次读完才恢复"已读"。仅章节级，不影响书级统计。
- **书架已读统计**：`BookcaseEntry.readCount = finishedChapters(bookId).size`（与目录页"已读"同源），进度条 = 已读/总数（原为阅读位置 `(pos+1)/total`，已改为基于目录已读标记）。

### 4.9 应用内更新（`UpdateChecker` / `UpdateCenter` / `ui/update/`）
- **检查**：查 GitHub Releases（`api.github.com`）；正式版通道取 `releases/latest`，测试版通道取列表里**按 `published_at` 排序后**最新的一条带 APK 的发布（**不能相信接口返回的顺序**，见下）。版本判定优先 `versionCode`（发布描述里的 `versionCode: <N>`，时间基准「`2_030_000_000` + 分钟偏移」，见 VERSIONING.md §2），旧发布无该字段时回退 `versionName` 语义比较（同基础"测试版→正式版"视为更新）。启动自动检查有 24h 节流。
  > ⚠️ **GitHub 的 releases 列表顺序不可靠**（2026-09-25 实测）：`v0.8.0-dev.100` 的 `id`/`created_at` 都是最新，却被排在 `dev.94` 之后（位置第 5）；新建的探针 release 同样落不到首位，带随机参数绕过缓存后顺序不变。所以"取第一条带 APK 的"会把旧包当成最新——测试版通道会一直提示已经过期好几个版本的 dev 包。实现改为显式按 `published_at` 排序（单测 `UpdateCheckerPickLatestTest` 用真实顺序当夹具）。这条**无法用 CI 侧绕过**：重新发布不会置顶，缩减保留数量也治不了。
- **下载**：支持 GitHub 直连或 `gh-proxy.com` 镜像前缀；`OkHttpClient` **显式设置连接/读写/整体超时**（默认无超时会长期挂起且无取消点）。
- **安装前签名校验（安全关键）**：下载完成后 `verifyApkSignature()` 比对 APK 与当前已安装应用的签名证书（SHA-256 指纹集合，API 28+ 用 `signingInfo.apkContentsSigners`，26/27 回退 `signatures`），**不一致则删除文件、提示用户并拒绝安装**。这是必要的：更新包可经第三方镜像下载，若不校验，中间人或被接管的镜像可下发任意 APK 并直接拉起安装器。
- **两个 CoroutineScope 统一注入**：`UpdateCenter` 与 `DownloadEngine` 的作用域均由 `AppContainer.applicationScope`（`SupervisorJob + Main.immediate`，与原 UpdateCenter 行为一致）提供，不再各自裸建且永不取消。

### 4.10 存储占用统计与清理（`AppStorageManager` / `ui/settings`）

**背景（用户报障）**：系统「应用信息 → 存储」显示缓存 33MB / 用户数据 36MB，而应用内「缓存管理」只显示 2.8MB。
根因是**统计口径只覆盖了一个目录**：旧实现只统计 `filesDir/html_cache`（网页离线缓存），
而实际占用的四个大头分散在别处 —— Coil 图片缓存（`cacheDir/image_cache`）、
更新安装包（`cacheDir/updates`）、WebView/Chromium 缓存（`cacheDir/WebView*`）、
数据目录其余部分（`shared_prefs`、`app_webview`、`code_cache`）。

| 位置 | 系统设置里的归类 | 归属组件 | 可清理 |
|---|---|---|---|
| `filesDir/html_cache` | 用户数据 | `HtmlDiskCache` | ✅ 按类型/全部 |
| `cacheDir/image_cache` | 缓存 | Coil `ImageLoader.diskCache` | ✅（经 Coil API，不手删目录） |
| `cacheDir/updates` | 缓存 | `UpdateCenter.download` | ✅（下载中会跳过） |
| `cacheDir/WebView*` | 缓存 | WebView/Chromium | ✅（`WebView.clearCache(true)` + 目录兜底；**不动 Cookie**） |
| `cacheDir/reader_background_*.tmp` | 缓存 | 自定义背景图复制 | ✅ |
| `shared_prefs`（`reading`/`ui`/`settings`/`cookies`…） | 用户数据 | `AppPreferences` 等 | ⚠️ 仅清理过期阅读记录，其余不动 |
| `app_webview` / `code_cache` | 用户数据 | WebView 数据 / JIT | ❌ 系统管理（`code_cache` 只展示） |

- `AppStorageManager.stats()` 返回 `StorageBreakdown`，其中 `cacheDirTotal` 对齐系统「缓存」、`dataDirTotal` 对齐系统「用户数据」，设置页把两个合计值直接摆在最前面，用户可自行与系统设置对照。
- 所有统计/清理都是挂起函数并跑在 `Dispatchers.IO`（要 walk 上万个文件）；`WebView.clearCache` 单独切回 `Dispatchers.Main`。
- 清理图片缓存走 `ImageLoader.diskCache.clear()`：Coil 内部有 journal，绕过它直接删目录会让索引与文件不一致。
- 清理结果由 `SettingsViewModel` 以 `CacheActionResult(kind, freedBytes, affectedBooks)` 回传（ViewModel 不持 Context），文案与 Toast 在设置页渲染。
- ⚠️ **不要**在更新下载进行中删 `cacheDir/updates`：文件句柄仍指向它，删掉会让"下载成功"却写出一个已被删除的文件（`clearCacheDir(preserveUpdatePackage = true)` 已处理）。
- 内存缓存（`TimedCache`）本就有 LRU 上限（info 128 / toc 32 / chapter 64），不在系统存储统计口径内。

---

## 5. UI 层

### 5.1 入口与装配
- `MainActivity`：`enableEdgeToEdge()`；`dispatchKeyEvent` 把音量键转发给 `VolumeKeyTurn`；`setContent` 中按 `ReaderSettings` 组装 `Wenku8ReaderTheme`，并用 **`HapticScope`** 注入全局点击振动（`LocalIndication` 委托 ripple + 按下时按 `hapticsStrength` 强度调用系统 `Vibrator`）。
- **高刷新率适配**：`requestHighRefreshRate()` 在 API 30+ 用 `preferredDisplayModeId`、API 26-29 用 `preferredRefreshRate`，请求同分辨率下的最高刷新率（60Hz 设备无副作用）。
- `Wenku8Application` + `AppContainer`：手动 DI，无框架。容器持有**应用级作用域**：`applicationScope`（`Main.immediate`，供更新弹窗/安装器）与 `ioScope`（同 Job + `Dispatchers.IO`）；对外只暴露 `launchIo { }` 作为后台任务入口（静默登录、启动清理都走它）——两个作用域共享同一个 Job，未来统一取消只需取消 `applicationScope`。
- `AppViewModelProvider`：手写 `ViewModelProvider.Factory`（从 `AppContainer` 取依赖注入 ViewModel）。

### 5.2 导航（`Routes.kt` + `MainScaffold.kt`，2026-08 UI 重构后）
- **主界面分页式 Tab**：`Routes.MAIN` 为宿主路由，内部 `HorizontalPager` 承载 3 个 Tab 页（`ExplorePage` / `BookcasePage` / `SettingsPage`），页面常驻（`beyondViewportPageCount=2`），切换保留滚动位置。
- **弹簧动画**：底栏点击走 `MainPagerState.animateToPage()`（`ui/components/PagerNavigation.kt`），动画取自 `MaterialTheme.motionScheme.defaultSpatialSpec()`（Expressive 主题下为弹性空间动效，标准主题下自动退化）；手动滑动由 `syncPage()` 同步选中态。
- **底栏**：`NavigationBar`（containerColor = `surfaceContainer`），选中/未选中用 Filled/Outlined 图标对。
- **返回键**：非首个 Tab 时 `BackHandler` 先回首页 Tab，再回退导航栈。
- 子页路由（`detail/{id}`、`reader/{id}?cid=`、`tag/{tag}`、`author/{name}`、`toc/{id}`、`stats`、`downloads`、`settings/custom`、`about`）走 NavHost（淡入 + 侧滑过渡），自带顶栏、无底栏；阅读器自绘 chrome。
- 每个 Tab 页自带 `ExpressiveScaffold` + 顶栏（主 Tab 用静态 64dp `ExpressiveTopAppBar`，已去掉折叠大顶栏以消除滚动逐帧布局级联；子页统一用 Expressive Flexible 版 `ExpressiveLargeTopAppBar`，可带副标题并在滚动时收起），顶栏右侧下载图标进 `downloads`。

### 5.3 各页面（统一 M3 Expressive：surfaceContainer 背景 + surfaceBright 卡片 + 大圆角/弹性动效）
- `ExplorePage/ViewModel`：静态顶栏「轻小说文库」+ 圆角搜索条（surfaceContainerHigh 药丸形）+ **Expressive 按钮组**（`ExpressiveToggleGroup`，按下项变宽/相邻项压缩）切换推荐 / 标签；首页栏目封面轮播、文字榜单（surfaceBright 卡片）、标签入口。**标签页按需加载**：标签清单来自内置常量（秒回），每个标签的书籍预览在行进入可见区域时才请求（`TagsBody` 的 `LaunchedEffect(tag, generation)` → `loadTagPreview`），刷新用 `tagsGeneration` 重新触发可见行。
- `TagBooksScreen/ViewModel`：某标签下书籍列表（SegmentedColumn 卡片行）+ Flexible 大顶栏，**分页加载全部**（底部「加载更多」逐页追加）。
- `DetailScreen/ViewModel`：Flexible 大顶栏（副标题=作者）+ 封面信息卡（标签用 StatusTag 药丸）+ 56dp 高强调阅读主按钮 + tonal 目录按钮 + 离线下载卡（TXT/EPUB + 波浪进度）+ 简介卡。
- `BookcasePage/ViewModel`：书架卡列表 + **`SplitButtonLayout`**（主按钮选排序方式 / 尾随 toggle 切正倒序）+ 刷新 + 顶栏统计与下载入口。
- `DownloadsScreen/ViewModel`：Flexible 大顶栏 + 下载任务卡列表（进行中用 `ActiveProgressBar` 波浪进度、完成/失败为文本），自带返回键。
- `SettingsPage`：SegmentedColumn 分组卡片——账号 / **外观**（深色模式下拉、纯黑模式、动态取色、**表达性动效开关**、手动种子色）/ **存储**（只留一个入口行 → `settings/storage` 二级页，见 §4.10）/ **实验性**（UI 风格：Material 3 Expressive ↔ MIUIX，见 §5.6）/ **网络**（主站域名镜像切换，切换后自动清 Cookie 并重登）/ 更新 / 阅读设置（进 `settings/custom`）/ 关于（进 `about`）。
- `StorageSettingsPage`（`ui/settings/StorageSettingsScreen.kt`，路由 `settings/storage`）：存储设置二级页——占用合计（与系统设置同口径）/ 按类型清理（图片、更新包、WebView 与临时文件）/ 网页缓存分类明细 / 过期阅读记录清理 / 缓存上限；清理结果以 Toast 回报（`CacheActionResult`）。主设置页因此不再被十余行缓存明细撑长，且这是低频操作。
- `AboutScreen`：Flexible 大顶栏（副标题=应用名）——应用图标（`painterResource(R.mipmap.ic_launcher)`）、版本号（`versionName`）、GitHub 仓库与爱发电链接（`LocalUriHandler` 打开，链接常量在 `strings.xml`）、应用介绍与声明。
- `CustomizationScreen`：阅读器外观定制（仍在 `settings/custom`）——Expressive 大顶栏、按钮组选深色模式、✓/✕ 开关；**浅色模式/深色模式各自独立的背景色与字体色**（默认纯白+纯黑 / 纯黑+纯白）、背景图片、字体/字号/字重/行距、简繁、四边边距、翻页方式等。
- `SettingsComponents.kt`：`SectionTitle` / `SettingLabel` 等复用组件。

### 5.4 通用组件（`ui/components/Expressive.kt`，material3 1.5 Expressive API）
- `ExpressiveScaffold` + `expressiveTopAppBarColors` / `ExpressiveTopAppBar` / `ExpressiveLargeTopAppBar`（+ `rememberExpressiveScrollBehavior`）：surfaceContainer 背景；主 Tab 用静态小顶栏，子页用 Flexible 大顶栏（展开两行、滚动收起）。
- `TonalCard`：surfaceBright + `MaterialTheme.shapes.large`（20dp，Expressive 形状刻度），可选 onClick/onLongClick。
- `DecorativeShapeBox` / `ExpressiveEmptyState`：用 `MaterialShapes`（Cookie9Sided / Clover4Leaf / SoftBurst / Boom / Sunny / PuffyDiamond）做装饰形状底 + 空态/错误态（标题 + 说明 + 可选操作按钮），各页不再各自手写空态。
- `ExpressiveLoadingIndicator` / `ActiveProgressBar`：`LoadingIndicator`（形变加载）与 `LinearWavyProgressIndicator`（波浪进度，仅用于"正在进行"的下载/更新）。
- `ExpressiveToggleGroup`：官方 `ButtonGroup` + `toggleableItem` + `ButtonGroupDefaults.OverflowIndicator`（项过多自动折叠）。
- `SegmentedColumn` / `SegmentedListItem` / `SegmentedSwitchItem` / `SegmentedDropdownItem`：分组卡片列表，底层是官方 `SegmentedListItem` + `ListItemDefaults.segmentedShapes/SegmentedGap/segmentedColors`——**按下时按规范做形状变化**，分组圆角取自官方 token；分组下标由 `SegmentedColumn` 经 CompositionLocal 下发，调用点无需手写 index/count。
- `StatusTag` / `ExpressiveSwitch`（✓/✕ 拇指图标，material3 默认不画图标，需由调用方通过 `thumbContent` 提供）。
- `HapticIndication`（`ui/components/HapticIndication.kt`）：**全局点击振动**——Compose 1.7 `IndicationNodeFactory` + `DelegatingNode` 实现，委托默认 ripple 保留水波纹，按下时调系统 `Vibrator`（`VibrationEffect.createOneShot(20ms, strength*255/100)`，可调强度）；`MainActivity` 根部 `HapticScope` 注入 `LocalIndication`，所有 clickable/按钮/开关自动生效。⚠️ 经验：foundation 1.7 已弃用旧 `Indication` API（`LocalIndication` 在 `androidx.compose.foundation` 包）；`IndicationNodeFactory.create` 返回 `DelegatableNode`，需 `as Modifier.Node` 后交给 `DelegatingNode.delegate()`。

### 5.5 Material 3 Expressive 落地要点（2026-09）
- **主题**：`MainActivity` 走 `Wenku8ReaderTheme(...)` → `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)`。`motionScheme` 由设置项 `ReaderSettingsState.expressiveMotion` 决定（默认 `MotionScheme.expressive()`，可切 `standard()`）；`shapes` 只把 `large` 抬到 20dp（`ShapeDefaults.LargeIncreased`），其余档位保持 M3 默认，避免菜单/对话框/FAB 圆角被一并改动。
- **排版**：`theme/Type.kt` 以 `Typography()` 为基线，标题/标签改用 emphasized 字重，中文正文/标题字距收敛为 0。
- **动效**：页面切换（`MainScaffold` NavHost 过渡）、Tab 分页弹簧（`PagerNavigation`）、按压缩放（`Anim.pressClickable`）、折叠展开（详情下载区 / 目录分卷）统一取自 `MaterialTheme.motionScheme`，不再写死 `tween`/`spring` 参数。
- **版本约束**：`MaterialExpressiveTheme` 在 material3 **1.4.0 仍是 internal**，Expressive 组件（`MaterialShapes`/`LoadingIndicator`/`ButtonGroup`/`SplitButton`/`FloatingToolbar`）也只出现在 1.5.0-alpha；因此 `app/build.gradle.kts` 显式把 material3 钉在 `1.5.0-alpha18`（BOM 2026.05.01 之外的单点覆盖）。升级 material3 时必须同时满足 `minCompileSdk`（当前 35，取 36）与 `minAndroidGradlePluginVersion`，并复核 `@ExperimentalMaterial3ExpressiveApi` 的 opt-in（见 `app/build.gradle.kts` 的 `compilerOptions.optIn`）。

### 5.6 MIUIX 备用风格与双风格切换（2026-09，实验性）

**目标**：Material 3 Expressive 与 MIUIX（HyperOS 设计语言）**两套界面各自独立**，用户在**设置 → 实验性 → UI 风格**里切换，无需重启。

⚠️ **重要约定（2026-09 起）**：MIUIX 模式**不再复用 Material 版的界面代码**。Material 版在 `ui/<模块>/`（`ui/components/Expressive.kt` 那套门面），MIUIX 版在 **`ui/miuix/`**，两边只共享 ViewModel 与数据层，UI 层零复用；路由处按风格二选一（见 `MainScaffold`）。`ui/components/Expressive.kt` 里的 `isMiuixStyle()` 分支只服务于**尚未迁移**的旧页面，迁移完成后会逐步移除。

| MIUIX 专属文件 | 作用 |
|---|---|
| `ui/miuix/MiuixComponents.kt` | MIUIX 基础件：`MiuixPage`/`MiuixSubPage`（miuix Scaffold + 大标题/小标题顶栏）、`MiuixSection`（分组卡片）、`MiuixRow`/`MiuixArrowRow`/`MiuixSwitchRow`/`MiuixDropdownRow`/`MiuixSliderRow`、`MiuixRowDivider`、`MiuixLoading`、`MiuixEmptyState`。**只用 miuix 组件**，颜色/字号只取 `MiuixTheme`（图标仍用 material-icons：miuix-icons 图标集太小） |
| `ui/miuix/MiuixSettingsPage.kt` | MIUIX 设置主 Tab（账号/外观/通用/存储入口/实验性/网络/更新/阅读/关于），逻辑复用 `SettingsViewModel` |
| `ui/miuix/MiuixStoragePage.kt` | MIUIX 存储二级页（占用合计、按类型清理、网页缓存明细、上限、过期记录），确认弹窗用 miuix `WindowDialog` |

**MIUIX 与 MD3 的解耦（2026-09）**：
- **不使用动态取色**：MIUIX 模式的配色完全由 miuix 自己的色板决定（`ThemeController` 不传 keyColor，即 HyperOS 默认蓝），不读 Material 侧的 `dynamicColor`/`seedColor`；深浅色仍跟随应用设置。
- **MD3 专属设置项在 MIUIX 下隐藏**：动态取色、手动主题色、纯黑模式只在 Material 风格的设置页出现（MIUIX 设置页不渲染这些行），避免"能点但无效"的误导。
- **弹层宿主**：MIUIX 模式在 `MainActivity` 里用 `MiuixRootHost`（miuix 根 Scaffold）包住整个界面，给 miuix 下拉/对话框一个不随页面切换而销毁的 popup host；下拉统一用覆盖层版 `OverlayDropdownPreference`（不再用窗口版）。
- **切换 UI 风格延后生效**：`MiuixSettingsPage` 的"UI 风格"下拉把选择结果暂存，等弹层收起（`onExpandedChange(false)`）后再写设置——避免在弹层显示过程中替换整棵界面导致宿主被销毁。

| 层 | 实现 | 位置 |
|---|---|---|
| 风格枚举 / 分发源 | `UiStyle{MATERIAL3, MIUIX}` + `LocalUiStyle`（`staticCompositionLocalOf`） | `ui/theme/UiStyle.kt` |
| 设置持久化 | `ReaderSettingsState.uiStyle`（prefs key `ui_style`，默认 `material3`） | `data/local/ReaderSettings.kt` |
| 根主题分发 | `Wenku8ReaderTheme(uiStyle=…)`：M3 → `MaterialExpressiveTheme`；MIUIX → `MiuixTheme(ThemeController)` **内嵌一层由 MIUIX 色板映射的 Material 主题** | `ui/theme/Theme.kt` |
| 组件分发 | 公共组件（Scaffold / 顶栏 / Card / 列表组 / 开关 / 分段控件 / 进度 / 空态）在 `isMiuixStyle()` 分支里走 MIUIX 实现 | `ui/components/Expressive.kt` |
| 排版适配 | MIUIX `TextStyles` → Material `Typography` 槽位映射，**所有既有 `MaterialTheme.typography.*` 调用点自动变成 HyperOS 字号** | `ui/theme/Theme.kt`（`miuixTypography`） |
| 底部导航 | MIUIX `NavigationBar`（固定）或 `FloatingNavigationBar`（悬浮，HyperOS 胶囊底栏）+ `FloatingNavigationBarItem` | `ui/MainScaffold.kt` |
| 液态玻璃 | `miuix-blur`：`rememberLayerBackdrop` + `Modifier.layerBackdrop`（页面内容作模糊源）+ `drawBackdrop { blur() }`，封装为 `Modifier.miuixGlass` | `ui/components/MiuixGlass.kt` |
| 入口 | 设置 → 实验性 → UI 风格（`ExperimentalSection`） | `ui/settings/SettingsScreen.kt` |

- **为什么内嵌 Material 主题**：页面里仍有大量 Material 组件（`Text`、`Slider`、`DropdownMenu`、`AlertDialog`、阅读器的 `ModalBottomSheet`）。它们在 MIUIX 模式下若拿不到 M3 的 `LocalContentColor`，会退回默认黑色、深色下不可读；映射一层色板后两套组件的配色保持一致。
- **哪些是真正的 miuix 组件**：`Scaffold`、`SmallTopAppBar`/`TopAppBar`（大标题 + 副标题）、`Card`、`Switch`、`SwitchPreference`（开关行）、`WindowDropdownPreference`（下拉行，HyperOS 圆角弹层）、`Slider`、`TabRow`（分段控件）、`CircularProgressIndicator`/`LinearProgressIndicator`、`NavigationBar`/`FloatingNavigationBar`。只有"行内容任意 composable"的普通列表行仍按 MIUIX 取值自绘（miuix 的行组件要求 `String` 标题，而封面/色点/Slider 这类内容无法字符串化）。
- **两套风格不追求一致**：MIUIX 模式以 HyperOS 观感为准（大标题顶栏、胶囊底栏、分组卡片 + 缩进分隔线、miuix 滑块/下拉），不刻意与 M3 对齐；`Expressive*` 门面只负责"同一调用点分派到哪套实现"。为此顶栏门面签名从 `@Composable` 标题改为 `String` 标题 + `String?` 副标题（miuix 顶栏要求字符串），13 个调用点已同步。
- **三种实验性外观开关**（设置 → 实验性）：UI 风格（Material 3 Expressive ↔ MIUIX）、悬浮底栏、底栏液态玻璃（后两者仅 MIUIX 生效；玻璃还要求 Android 12L+，低版本自动退化为半透明纯色）。
- **工具链（本次一并升级）**：compileSdk **37** + **AGP 9.4.1** + **Gradle 9.7.1**，CI 的 JDK 由 17 提到 **21**。三个坑与对策：
  1. **AGP 9 内置 Kotlin**，不能再应用 `org.jetbrains.kotlin.android`（会直接报 "not compatible"）；内置默认 KGP 2.2.10 读不了 miuix 的 2.3.20 元数据 → 在根 `build.gradle.kts` 用 `buildscript { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21") }` 提升 KGP（官方文档做法）。
  2. **cronet-embedded / cronet-api / cronet-common 共用 `org.chromium.net` namespace**，AGP 9 将其从警告升级为错误；三者的类缺一不可，故用 `android.uniquePackageNames=false` 放行。
  3. **miuix-blur 声明 minSdk 32**，项目仍保留 minSdk 26：manifest 用 `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"` 放行合并，代码侧由 `isMiuixGlassSupported` 做运行时门控，低版本不触碰任何模糊 API。
- **构建内存**：`gradle.properties` 调整为 `-Xmx1536m -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC` + `kotlin.compiler.execution.strategy=in-process`（本机页面文件小，G1 + 独立 Kotlin 守护进程会因提交内存不足直接崩 JVM；这套配置在本地与 CI 都够用）。
- **尚未迁移**（下一步清单，按收益排序）：① 设置类列表行 → `ArrowPreference`（带 chevron 的跳转行）；② 对话框 → `SuperDialog`（存储页两个确认框、更新弹窗）；③ 阅读器底部弹层 → `SuperBottomSheet`；④ 长列表 → miuix `ScrollBar`；⑤ 探索页搜索框 → miuix `TextField`/`SearchBar`；⑥ `Text`/`Icon` 换 miuix 版本（配色与字号已映射，收益最低）。这些都不影响现有双风格切换，属于逐页替换的机械工作。
- **维护提示**：升级 miuix 前先看 AAR metadata 的 `minCompileSdk` 与 pom 里的 kotlin-stdlib 版本（决定是否需要再提 KGP）；0.9.x 起 `extra` 包改名为 `preference`（`SuperSwitch` → `SwitchPreference`）。

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
`darkMode(system)/dynamicColor(true)/seedColor/amoled(false)/backgroundMode(color|image)/readerBackgroundLight(纯白)/readerTextColorLight(纯黑)/readerBackgroundDark(纯黑)/readerTextColorDark(纯白)/backgroundImagePath/fontFamily(default|sans|serif|mono)/fontSize(18)/fontWeight(400)/lineSpacing(1.8)/traditionalChinese(false)/scrollMode(false)/volumeKeyTurnPage(true)/autoNextChapter(false)/pageTurnDirection(true)/autoTurnInterval(10s)/clickTurnPage(true)/hapticsEnabled(true)/hapticsStrength(50)/autoPadding(true)/topPadding(24)/bottomPadding(16)/leftPadding(20)/rightPadding(20)`。
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

### 7.2 UI 重构

**2026-08（组件外壳）**：参考 `技术性文档(只读勿动)/SukiSU-Ultra-main` Material 侧，把主外壳与各页面改为 surfaceContainer 背景 + surfaceBright 卡片 + 分组卡片列表。

**2026-09（Material 3 Expressive）**：升级到 material3 1.5.0-alpha18 Expressive API，见 §5.4 / §5.5 —— 主题改用 `MaterialExpressiveTheme`、顶栏改 Flexible 大顶栏、分组列表改官方 `SegmentedListItem`（按下形状变化）、分段控件改 `ButtonGroup`、加载/进度改 `LoadingIndicator`/波浪进度条、空态用 `MaterialShapes` 装饰形状，动效统一走 motion scheme，并新增「表达性动效」开关。
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
- 无自动化测试；分页算法 `paginateChapter` 是纯函数，适合补单测（当前无 test 源集）。
- 未做：日/周排行榜、书单、外部打开 EPUB/TXT、深链（`reader/{id}` 已可被外部跳转）。
  （**多书架分组已实现**，见 §4.6.1；实验性开关默认关闭。）

### 7.7 2026-09 功能增强（dev 分支开发，fast-forward 合并回 master）
- **阅读热力图**（`ui/stats/`，书架顶栏日历图标入口）：GitHub 风格周列矩阵 + 时间尺度切换（本周/本月/本年/全部）+ 汇总卡（累计/本周/连续天数/日均）+ 当日详情卡 + 工作日绿/周末蓝双色阶 + 图例（参考 LNR）。
- **阅读时长埋点**（`ReadingStatsStore` + `ReaderScreen` 内 `ReadingTimeTracker`）：前台阅读每 60s 落盘、退出冲刷余量；按「书+日期」聚合秒数，先累计再 ceil 成分钟。
- **详情页增强**（§4.8）：作者高亮跳转、Tag 跳转、独立目录页（分卷折叠 + 已读标记 + 重读重置）、书架已读统计。
- **标签分页**：`tagBooks(tag, page)`，「查看全部」逐页加载该标签全部书籍（去重 + 空页停止）。
- **全局点击振动**（§5.4 `HapticIndication`）：设置页「外观」新增「触觉反馈」开关 + 振动强度滑动条（0-100 → Vibrator 幅度 1-255）。
- **CI**：dev 分支独立构建工作流（仅构建、不发布）；语义版本解析**必须用内联 run 步骤**（composite action 输出在本环境失效，曾导致 tag 变 `v`——见 VERSIONING.md §8 教训）；versionCode 为「`2_030_000_000` + 自 2026-01-01 起的分钟数」（跨工作流单调递增、分钟粒度）。

---

## 8. 参考文档与版本管理

- 版本号管理方案见根目录 **`VERSIONING.md`**（versionName SemVer + versionCode 时间基准（分钟粒度）、发布三件套、dev 分支只构建不发布）。
- **CI 经验**：`release.yml`（push main/master 触发：语义版本解析 + 构建 + tag + Release）与 `dev.yml`（push dev 触发：仅构建上传 Artifact）。⚠️ 版本解析必须用**内联 run 步骤**写 `$GITHUB_OUTPUT`——composite action 的输出在本环境不生效（曾导致 release tag 变成 `v`、versionName 为空）。

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
# wrapper 已入库（gradlew / gradlew.bat），会自动拉取 Gradle 9.7.1 到 ~/.gradle/wrapper/dists/
$env:JAVA_HOME="C:\Users\<用户>\.jdks\jbr-21.0.11"
.\gradlew.bat :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest    # 纯 JVM 单测（CI 每次推送都会先跑这个）
```

⚠️ **命令行构建注意**：
- **JDK 必须是 17–21（CI 用 21）**：Gradle 9.7.1 支持 JDK 17–24，JDK 25 会在启动阶段直接失败（只打印 `25.0.2`），详见 §2 的说明。
- **必须加 `--no-daemon`**：本机页面文件小，Gradle/Kotlin 守护进程容易因提交内存不足直接崩 JVM（`gradle.properties` 已把堆压到 `-Xmx1536m` + SerialGC + 进程内 Kotlin 编译）。
- 依赖版本集中在 `gradle/libs.versions.toml`（见 §2）。

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
11. **material3 版本约束（BOM 2026.05.01 + material3 1.5.0-alpha18）**：Expressive API 现已可用（`MaterialExpressiveTheme` / `MaterialShapes` / `LoadingIndicator` / `ButtonGroup` / `SplitButton` / `LargeFlexibleTopAppBar` / `SegmentedListItem` 等），但整体仍是 `@ExperimentalMaterial3ExpressiveApi` —— 已通过 `app/build.gradle.kts` 的 `compilerOptions.optIn` 全局放行，升级 material3 时按 §5.5 复核。新增 Expressive 组件统一放 `components/Expressive.kt`；页面**不要**直接写死颜色/圆角/动画参数，优先复用该文件的组件与 `MaterialTheme.motionScheme`。
12. **主题**：`Wenku8ReaderTheme(darkTheme, dynamicColor, seedColor, amoled, expressiveMotion)`；手工色板在 `Theme.kt` 的 `manualScheme()`，要补齐 surfaceContainer* 全角色，勿退回旧版只有 primary/background 的残缺色板。`amoled` / `expressiveMotion` 与 `darkMode/dynamicColor/seedColor` 一样存于 `ReaderSettings`（prefs `settings`）。

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
| 主 Tab 分页 + motion scheme 弹簧（MainPagerState/springAnimateToPage） | `components/PagerNavigation.kt` |
| 主外壳（NavHost + NavigationBar + BackHandler） | `MainScaffold.kt` |
| 三 Tab 宿主（HorizontalPager） | `MainScaffold.kt`（`MainPagerScreen`） |
| M3 Expressive 组件层（Scaffold/顶栏/TonalCard/装饰形状/空状态/加载进度/Segmented 系列/ExpressiveSwitch） | `components/Expressive.kt` |
| Expressive 主题装配（MaterialExpressiveTheme + MotionScheme + Shapes） | `theme/Theme.kt`（`Wenku8ReaderTheme`） |
| 手工色板（surfaceContainer* 全角色 + AMOLED） | `theme/Theme.kt`（`manualScheme`） |
| 全局 Typography | `theme/Type.kt` |
| 主题种子色预设 | `theme/Colors.kt` |
| 探索页（折叠顶栏/搜索/推荐/标签） | `explore/ExploreScreen.kt`（`ExplorePage`） |
| 设置页（分组卡片 + 外观） | `settings/SettingsScreen.kt`（`SettingsPage`） |
| 详情页卡片化 | `detail/DetailScreen.kt` |
