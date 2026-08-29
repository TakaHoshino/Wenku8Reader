# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。
发布说明由 GitHub Actions 依据 Conventional Commits 自动生成（见 `.github/workflows/release.yml` 与 `VERSIONING.md`）。

## [Unreleased]

<!-- 本次提交之后、下次发布之前的变更记录到这里 -->

## [1.0.0] - 2026-08-29

### 新增
- 初始版本：探索（首页栏目/搜索/标签）、书架、书籍详情、沉浸式阅读器、下载管理、设置与关于页
- 沉浸式阅读：侧滑/滚动双模式、自动翻页、音量键翻页、简繁转换、外观定制（浅色/深色独立配色）
- 离线下载：TXT / EPUB，保存至 `Downloads/Wenku8/`
- Cloudflare 绕过：WebView → Cronet → OkHttp 三级抓取（tags/首页）
- 高刷新率适配与性能优化
