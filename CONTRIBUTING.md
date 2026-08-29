# 贡献指南

## 提交信息规范（Conventional Commits）

版本号由 GitHub Actions **依据提交信息自动升级**，请严格遵循以下前缀：

| 前缀 | 触发版本升级 | 示例 |
|---|---|---|
| `feat:` | 次版本 +1（1.0.0 → 1.1.0） | `feat: 新增阅读统计` |
| `fix:` | 修订号 +1（1.0.0 → 1.0.1） | `fix(reader): 修复音量键方向` |
| `BREAKING CHANGE` / `feat!:` | 主版本 +1（1.x → 2.0.0） | `feat!: 重构数据层` |
| 其他（docs/refactor/style/test/chore/ci） | 不触发发布 | `docs: 更新 README` |

规则：
- 单行提交：`类型(可选范围): 描述`，如 `feat(bookcase): 新增排序`。
- 提交描述用中文，简洁明了。
- 未按规范提交不会导致构建失败，但对应内容不会触发/进入自动发布的更新日志。

## 发布流程（自动化）

推送到 `main` 后 CI 自动执行（详见 `VERSIONING.md` 与 `.github/workflows/release.yml`）：

1. 解析最近 tag 之后的提交 → 决定主/次/修升级
2. 构建 Release APK（versionCode = `github.run_number`）
3. 打 `vX.Y.Z` 标签并推送
4. 生成更新日志并发布 GitHub Release（附 APK）

无需手动改 `build.gradle.kts` 版本号；本地构建回退使用 `gradle.properties` 的 `VERSION_NAME` / `VERSION_CODE`。
