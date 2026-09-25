# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布说明由 GitHub Actions 依据 Conventional Commits 自动生成（见 `.github/workflows/release.yml` 与 `VERSIONING.md`）。

> 本文件按**真实 git tag** 记录；每个版本的详细提交清单以对应 GitHub Release 的自动生成说明为准。
> `dev` 分支每次推送会出预发布包 `vX.Y.Z-dev.N`（例如 `v0.7.0-dev.58`），它们共用同一个即将发布的 X.Y.Z。

## [Unreleased]

暂无（新改动随 `dev` 预发布，见下方 [0.7.0]）。

## [0.7.0] - 开发中（`dev` 预发布，最新 `v0.7.0-dev.92`；预发布只保留最近 10 个）

### 新增
- **多书架管理**（实验性，默认关闭，见 DEVELOPMENT.md §4.6.1）：新建/重命名/删除书架、一本书可同属多个书架、书架切换条 + 管理二级页、长按卡片改归属、收藏时勾选目标书架；关闭开关后书架页与收藏路径与单书架版本完全一致，数据不丢
- **账户登录与网站书架**（实验性，默认关闭，依赖多书架，见 DEVELOPMENT.md §4.11）：可登录自己的 wenku8 账户（**不保存密码**），书架切换条出现「Wenku8书架」；详情页书架图标 + 长按条目可加入/移出网站书架并同步站方；退出登录即刻回落内置账号，阅读不受影响
- **MIUIX（HyperOS）界面层**：`ui/miuix/` 与 Material 3 实现完全独立，可在「设置 → 实验性」切换；页面、弹层、设置项全部使用 miuix 组件（0.9.1）
- **HyperOS 形态对齐**：悬浮胶囊底栏（可切换固定/悬浮）与液态玻璃背景、miuix 大/小标题顶栏、分组卡片 + miuix `SmallTitle`、空态插图、长列表 miuix 滚动条
- **设置分类二级页**（参考 PiliPlus）：设置主页只放分类入口，MD3 与 MIUIX 各自独立实现各分类页
- **缓存与存储管理**：`AppStorageManager` 统计 cacheDir/dataDir 全量占用（与系统设置同口径）、按类型清理、缓存上限选择、过期阅读记录清理
- **Material 3 Expressive 界面重做**，并把工具链升到 AGP 9.4.1 / Gradle 9.7.1 / compileSdk 37 / Kotlin 2.3.21（AGP 9 起使用内置 Kotlin 编译）

### 修复
- **站方书架解析**：`readbookcase.php` 链接的 href 先做反转义（站点可能输出 `&amp;`），否则参数拆分会得到 `amp;bid` 之类的键、整页书架被静默解析成空列表
- **MIUIX 打开下拉/弹层必崩**：`activity-compose` 升到 1.13.0 提供 `NavigationEventDispatcherOwner`
- **悬浮底栏下方整条留白**：底栏改为覆盖层，余量计入滚动内容的内边距
- **MIUIX 页面无法滚动**：滚动行为在 MIUIX 下返回不消费滚动的实现
- **重复下载生成 `书名 (1).txt`**：写入前先删同名/自动重命名副本，并按 `IS_PENDING` 落盘
- **缓存统计只有 2.8MB**：统计范围从 `html_cache/` 扩到 SharedPreferences、Coil 图片缓存等全部存储位置
- **MIUIX 设置页叠出两个更新弹窗**；目录页/统计页加载失败被误显示成「暂无数据」
- 网络层：应用级后台任务收敛到统一作用域、`ensureLoggedIn()` 加 Mutex 去重、WebView 取消时释放资源

### 变更
- MIUIX **不使用**动态取色/手动主题色/纯黑模式等 MD3 专属项（设置项在 MIUIX 下隐藏）
- 阅读器（顶栏/底栏/翻页 chrome）暂保持 Material 实现

## [0.6.1] - 2026-09-12

### 变更
- 发布链路加固：密钥与写权限隔离、第三方 Action 固定到 commit、环境审批、产物验签

### 修复
- APK 验签指纹提取随 `apksigner` 输出格式变化失效；breaking 判定正则误伤代码片段

## [0.6.0] - 2026-09-06

### 新增
- GitHub Issue 模板（Bug 报告 / 功能建议 / 配置）

### 变更
- 搜索结果移到独立页面

## [0.5.1] - 2026-09-05

### 修复
- 搜索/作者页封面无法显示的两个数据层缺陷（封面 key 用错 ID；单结果重定向时漏传封面）

## [0.5.0] - 2026-09-05

### 新增
- 缓存分类管理：文件名带分类前缀、按类型统计与清理、动态上限（旧格式自动迁移）
- 设置页新增「通用」分组（触感反馈 + 缓存管理）

### 修复
- 同小时构建的 dev 包在 versionCode 相同时按 dev 序号比较

## [0.4.0] - 2026-09-05

### 新增
- 搜索结果与作者书籍列表展示封面；作者书籍页复用按作者搜索接口

## [0.3.2] - 2026-08-30

### 修复
- 更新检查改为以 versionCode 为依据（Release 描述写入 versionCode）

## [0.3.1] - 2026-08-30

### 变更
- **包体积缩减 47.2%**（32.9MB → 17.38MB）：release 开启 R8 混淆 + 资源收缩、原生库 ABI 裁剪
- **功耗优化**：启动检查更新节流为 24h；阅读时长埋点计时 1s→5s
- 编译警告清零（Parsers 的 `groupOrEmpty`、类型化 `getSystemService`）；strings.xml 精简

## [0.3.0] - 2026-08-30

### 修复
- 更新检测：正式版通道取最新 release、测试版通道取最新发布；同版本不弹窗、跳过按基础版本线生效
- 繁体中文切换未生效

### 变更
- CI 版本基准排除 prerelease 标签，测试版不再虚高版本号

## [0.2.0] - 2026-08-30

### 修复
- 空 `APP_VERSION_NAME` 导致 versionName 为空；书架已读章节统计按目录已读标记计算；详情页 Tag 解析缺失

## [0.1.0] - 2026-08-29

### 新增
- 初始版本：探索（首页栏目/搜索/标签）、书架、书籍详情、沉浸式阅读器、下载管理、设置与关于页
- 沉浸式阅读：侧滑/滚动双模式、自动翻页、音量键翻页、简繁转换、外观定制（浅色/深色独立配色）
- 离线下载：TXT / EPUB，保存至 `Downloads/Wenku8/`
- Cloudflare 绕过：WebView → Cronet → OkHttp 三级抓取（tags/首页）
- 高刷新率适配与性能优化
