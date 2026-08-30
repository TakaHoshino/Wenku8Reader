# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布说明由 GitHub Actions 依据 Conventional Commits 自动生成（见 `.github/workflows/release.yml` 与 `VERSIONING.md`）。

## [Unreleased]

### 变更（性能 / 体积 / 功耗优化）
- **包体积缩减 47.2%**（32.9MB → 17.38MB）：release 开启 R8 混淆 + 资源收缩（dex 43MB→3.7MB，含 material-icons-extended 未用图标裁剪）；原生库 ABI 裁剪（移除 32 位 x86，保留 arm64-v8a/armeabi-v7a/x86_64）
- **编译警告清零**：Parsers 新增 `Matcher.groupOrEmpty` 消除 28 条 Java 正则 nullability 警告；HapticIndication 改用类型化 `getSystemService` 消除弃用警告
- **strings.xml 精简**：简/繁各移除 12 个未使用字符串
- **功耗优化**：启动检查更新节流为 24h 一次（减少网络无线电）；阅读时长埋点计时器 1s→5s 滴答（减少 CPU 唤醒）
- 更新检测逻辑修正：正式版通道=最新 release；测试版通道=最新发布（不论 prerelease）；同基础版本「测试版→正式版」视为更新

<!-- 本次提交之后、下次发布之前的变更记录到这里 -->

## [1.0.0] - 2026-08-29

### 新增
- 初始版本：探索（首页栏目/搜索/标签）、书架、书籍详情、沉浸式阅读器、下载管理、设置与关于页
- 沉浸式阅读：侧滑/滚动双模式、自动翻页、音量键翻页、简繁转换、外观定制（浅色/深色独立配色）
- 离线下载：TXT / EPUB，保存至 `Downloads/Wenku8/`
- Cloudflare 绕过：WebView → Cronet → OkHttp 三级抓取（tags/首页）
- 高刷新率适配与性能优化
