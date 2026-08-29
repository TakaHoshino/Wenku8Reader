# 版本号管理方案（Versioning）

> 适用于 Wenku8Reader（Android）。核心目标：`versionName` 给人看、`versionCode` 给系统看，两者一一对应、单调可追溯。
> **已部署自动化（推荐默认流程）**：GitHub Actions 依据 Conventional Commits 自动升级版本并发布，详见 §3。

## 1. 版本号组成

### versionName（展示版本，语义化 SemVer：`主.次.修订`）

| 段位 | 规则 | 示例 |
|---|---|---|
| 主版本 | 不兼容的重大改动：UI 整体重构、数据层重写、更换包名重新发布 | 1.x.x → 2.0.0 |
| 次版本 | 向后兼容的新功能 | 1.0.x → 1.1.0（如：新增关于页） |
| 修订号 | 缺陷修复与微调 | 1.0.0 → 1.0.1（如：修复关于页闪退） |

约定：次版本、修订号取值范围 **0–99**（见下方 versionCode 规则）。

### versionCode（系统版本号，单调递增整数）

规则：`主版本 × 10000 + 次版本 × 100 + 修订号`，只增不减。

| versionName | versionCode |
|---|---|
| 1.0.0 | 10000 |
| 1.0.1 | 10001 |
| 1.1.0 | 10100 |
| 2.3.4 | 20304 |

- 该规则在 `主<200、次<100、修订<100` 时无冲突（`int` 上限 20 亿内）。
- **铁律**：versionCode 一旦发布**绝不回退**——Android 系统以此判断是否允许覆盖安装（降版本会被拒绝）。

## 2. 升级决策速查

- 修 bug / 文案 / 性能微调 → 升**修订号**（1.0.0 → 1.0.1）
- 加功能（新页面、新设置项）→ 升**次版本**（1.0.x → 1.1.0）
- 界面整体重构、包名变更、数据不兼容 → 升**主版本**（1.x.x → 2.0.0）
- 尚在内部测试未发布 → 不必每次提交都升版本，攒到一次发布再升

## 3. 自动化版本管理（已部署，推荐）

由 `.github/workflows/release.yml` 在**推送 main**（或手动触发）时自动完成，无需手工改版本号：

1. **语义解析**：读取最近 `vX.Y.Z` 标签之后的提交，按 Conventional Commits 决定升级——
   `feat:` → 次版本 +1；`fix:` → 修订号 +1；`BREAKING CHANGE` / `!` → 主版本 +1；无相关提交则跳过发布。
2. **versionCode = `github.run_number`**：每次工作流运行的全局递增编号，保证唯一且单调递增（这正是 Android 系统判断新旧所需的性质）。
3. **构建注入**：`build.gradle.kts` 优先读环境变量 `APP_VERSION_NAME` / `APP_VERSION_CODE`；本地构建回退 `gradle.properties` 的 `VERSION_NAME` / `VERSION_CODE`。
4. **产出**：打 `vX.Y.Z` 标签 → 生成更新日志（自上次标签的 feat/fix 提交）→ GitHub Release 附 APK。
5. **签名**：配置仓库 Secrets（`KEYSTORE_PATH` 等）后用正式签名；未配置时回退 debug 签名，保证可安装。

提交规范见 `CONTRIBUTING.md`。

## 4. 手动回退流程（无 CI 或临时发布）

当自动化不可用时（如本地出包）：

1. **改版本号**：更新 `gradle.properties` 中 `VERSION_NAME` 与 `VERSION_CODE`（二者按 §1 规则同步；`build.gradle.kts` 在无环境变量时读取它们）。
2. **写更新日志**：维护 `CHANGELOG.md`（分类：新增 / 修复 / 优化）。
3. **打标签**：`git tag v<versionName>`（如 `v1.0.1`），推送后基于该标签生成 GitHub Release 并上传 APK。

> 注意：若手动发布后 CI 下次运行，语义解析会以该手动标签为基准继续升级，两者衔接。

## 5. 构建变体

| 变体 | versionName 显示 | 用途 |
|---|---|---|
| debug | `1.0.0-debug`（`versionNameSuffix` 自动加后缀，已实现） | 自测 / 内测包 |
| release | `1.0.0`（CI 由语义解析注入） | 对外发布 |

已实现于 `app/build.gradle.kts`：`debug` 加 `-debug` 后缀；`release` 在注入 `KEYSTORE_*` 环境变量时用正式签名，否则回退 debug 签名。

> 关于页已通过 `packageManager.getPackageInfo(...).versionName` 读取真实版本号，自动跟随此方案，无需额外维护。

## 6. 进阶（可选，当前部署已覆盖核心）

- ✅ **自动 versionCode**：CI 用 `github.run_number`（每次运行唯一递增），已部署。
- ✅ **语义化自动升级**：CI 解析 Conventional Commits（feat/fix/breaking），已部署。
- ✅ **Release 自动发布**：CI 打 tag + 生成更新日志 + 上传 APK，已部署。
- ⬜ **自动 versionName 含提交哈希**（`git describe --tags` 的 `-g<hash>` 风格）：暂不需要，语义化 `X.Y.Z` 更利于阅读；如做内测分发热度号可再加。
- ⬜ **正式签名**：在仓库 Settings → Secrets and variables → Actions 添加以下 Secrets 后自动启用（未配置时回退 debug 签名）：

  | Secret 名 | 内容 | 获取方式 |
  |---|---|---|
  | `KEYSTORE_BASE64` | keystore 文件的 Base64 编码文本 | 见下方「签名配置操作步骤」 |
  | `KEYSTORE_PASSWORD` | keystore 存储密码 | 生成时设置 |
  | `KEY_ALIAS` | 密钥别名（默认 `release`） | 生成时设置 |
  | `KEY_PASSWORD` | 密钥密码 | 生成时设置 |

  > Secrets 只能存文本（≤64KB），keystore 为二进制文件，须先 Base64 编码存入 `KEYSTORE_BASE64`；工作流会在运行器上还原为文件并注入 `KEYSTORE_PATH` 等环境变量（见 `.github/workflows/release.yml` 的「准备正式签名」步骤）。

### 签名配置操作步骤（Windows）

**① 生成 keystore（本地一次，终生复用）**

```bat
keytool -genkeypair -v -keystore %USERPROFILE%\wenku8reader-release.keystore ^
  -alias release -keyalg RSA -keysize 2048 -validity 10950 ^
  -storepass 你的存储密码 -keypass 你的密钥密码 ^
  -dname "CN=Wenku8Reader, OU=Dev, O=Hoshino, L=Beijing, C=CN"
```

> `validity 10950` = 30 年有效期。**密码务必牢记并离线备份**：丢失后无法再对老版本做覆盖升级；keystore 文件本身不要提交进仓库。

**② 转成 Base64（PowerShell）**

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$env:USERPROFILE\wenku8reader-release.keystore")
)
```

复制输出的整段文本（约 3~6KB，远低于 64KB 上限；建议单行，含换行亦可）。

**③ 添加 Secrets**

1. 打开仓库网页 → **Settings** → 左侧 **Secrets and variables** → **Actions**
2. 点 **New repository secret**，逐条添加：

   | Name | Secret |
   |---|---|
   | `KEYSTORE_BASE64` | 上一步复制的内容 |
   | `KEYSTORE_PASSWORD` | 存储密码 |
   | `KEY_ALIAS` | `release` |
   | `KEY_PASSWORD` | 密钥密码 |

3. 保存后 Secrets 会以 `***` 打码显示，无法再查看（可覆盖更新）。

**④ 验证**

- 手动触发：**Actions** → 左侧 **Release** → **Run workflow**（或推送一个 `feat:`/`fix:` 提交）
- 查看运行日志：「准备正式签名」步骤正常执行（还原 keystore）、「构建 Release APK」无签名警告
- 下载 Release 里的 APK 安装验证；也可用 `jarsigner -verify app-release.apk` 核对证书

> 提示：正式签名配置前后产出的 APK **签名不同**（debug 签名 → 正式签名），已安装旧包的用户需卸载后才能覆盖安装新包。建议在对外发布前就配好签名。

## 7. 常见坑

1. `versionCode` 只增不减；本地测试装过 `20304`，后面就不能再装 `20303`（需先卸载）。
2. `versionName` 不要用 `1.0`、`1.0.0.1` 等非 SemVer 写法；三段的语义化版本便于 changelog 与 tag 一一对应。
3. 修改版本号时 `versionCode` 与 `versionName` **必须同步改**，只改一个会导致「版本号没变但 code 变了」的困惑。
4. tag 命名统一 `v` 前缀小写（`v1.0.1`），与 versionName 完全一致，便于脚本解析。
