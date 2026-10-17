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

**CI 自动化构建（release/dev 工作流）**：`2_030_000_000 + 自 2026-01-01 UTC 起的分钟数`（如 `2030385350`），时间基准、跨工作流单调递增，必然大于任何历史已装版本，且**分钟粒度**避免同一小时内多次构建撞号。

**本地手动构建**：`主版本 × 10000 + 次版本 × 100 + 修订号`，只增不减。

| versionName | versionCode（本地规则） |
|---|---|
| 1.0.0 | 10000 |
| 1.0.1 | 10001 |
| 1.1.0 | 10100 |
| 2.3.4 | 20304 |

- 本地规则在 `主<200、次<100、修订<100` 时无冲突（`int` 上限 20 亿内）。
- **铁律**：versionCode 一旦发布**绝不回退**——Android 系统以此判断是否允许覆盖安装（降版本会被拒绝）。
- 为什么 CI 不用 `主×10000+…`：自动化按提交递增语义版本，难以与「本地手动维护」的规则保持一致；时间基准天然满足「单调递增 + 跨工作流（dev/release）互不冲突 + 必然大于历史版本」，也无需跨工作流协调计数器。
- 为什么不是直接拼 `yyyyMMddHHmm`：**Android 的 versionCode 是 32 位 int，上限 2 147 483 647**，`yyyyMMddHHmm` 在 2026 年就已经是 2.0e11，连 aapt 都无法编码。因此采用「基准值 + 分钟偏移」：基准 `2_030_000_000` 取在历史已发布值（最高 `2_026_092_508`）之上以保证任何存量用户都能覆盖安装，余量约 1.17 亿分钟 ≈ 223 年。
- 历史沿革：2026-09-25 之前用的是小时粒度 `yyyymmddHH`，并因此发生过一次真实事故——`v0.7.0` 与 `v0.7.0-dev.92` 在同一小时内构建、versionCode 完全相同，导致测试版用户既看不到正式版更新、也无法覆盖安装。分钟粒度把这类碰撞的概率降到约 1/60。

## 2. 升级决策速查

- 修 bug / 文案 / 性能微调 → 升**修订号**（1.0.0 → 1.0.1）
- 加功能（新页面、新设置项）→ 升**次版本**（1.0.x → 1.1.0）
- 界面整体重构、包名变更、数据不兼容 → 升**主版本**（1.x.x → 2.0.0）
- 尚在内部测试未发布 → 不必每次提交都升版本，攒到一次发布再升

## 3. 自动化版本管理（已部署，推荐）

由 `.github/workflows/release.yml` 在**推送 main**（或手动触发）时自动完成，无需手工改版本号：

1. **语义解析**：读取最近 `vX.Y.Z` 标签之后的提交，按 Conventional Commits 决定升级——
   `feat:` → 次版本 +1；`fix:` / `perf:` → 修订号 +1；`BREAKING CHANGE` / `!` → 主版本 +1；无相关提交则跳过发布。
2. **versionCode = `2_030_000_000 + 自 2026-01-01 UTC 起的分钟数`**（时间基准）：每次构建必然大于历史所有已装版本（含本地调试包与 dev 包），保证覆盖安装不降级；分钟粒度也保证同一小时内的多次构建互不撞号（见 §2 的说明）。
3. **构建注入**：`build.gradle.kts` 优先读环境变量 `APP_VERSION_NAME` / `APP_VERSION_CODE`；本地构建回退 `gradle.properties` 的 `VERSION_NAME` / `VERSION_CODE`。
4. **产出**：打 `vX.Y.Z` 标签 → 生成更新日志（自上次标签的 feat/fix 提交）→ GitHub Release 附 APK。
5. **签名**：必须配置正式签名 Secrets（`KEYSTORE_*`）；**未配置则构建直接失败，不再回退 debug 签名**（原因见 §6 的安全说明）。

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

已实现于 `app/build.gradle.kts`：`debug` 加 `-debug` 后缀；`release` 在注入 `KEYSTORE_*` 环境变量时用正式签名。**CI 侧未配置签名密钥时会直接失败**，不会产出 debug 签名的"正式版"。

> 关于页已通过 `packageManager.getPackageInfo(...).versionName` 读取真实版本号，自动跟随此方案，无需额外维护。

## 6. 进阶（可选，当前部署已覆盖核心）

- ✅ **自动 versionCode**：CI 用 `github.run_number`（每次运行唯一递增），已部署。
- ✅ **语义化自动升级**：CI 解析 Conventional Commits（feat/fix/breaking），已部署。
- ✅ **Release 自动发布**：CI 打 tag + 生成更新日志 + 上传 APK，已部署。
- ⬜ **自动 versionName 含提交哈希**（`git describe --tags` 的 `-g<hash>` 风格）：暂不需要，语义化 `X.Y.Z` 更利于阅读；如做内测分发热度号可再加。
- ✅ **正式签名**：**发布必需**。在仓库 Settings → Secrets and variables → Actions 添加以下 Secrets；未配置时构建会**直接失败并提示**，不会发布 debug 签名包：

  | Secret 名 | 内容 | 获取方式 |
  |---|---|---|
  | `KEYSTORE_BASE64` | keystore 文件的 Base64 编码文本 | 见下方「签名配置操作步骤」 |
  | `KEYSTORE_PASSWORD` | keystore 存储密码 | 生成时设置 |
  | `KEY_ALIAS` | 密钥别名（默认 `release`） | 生成时设置 |
  | `KEY_PASSWORD` | 密钥密码 | 生成时设置（PKCS12 下与存储密码相同） |

  > Secrets 只能存文本（≤64KB），keystore 为二进制文件，须先 Base64 编码存入 `KEYSTORE_BASE64`；工作流会在运行器上还原为文件，并按步骤最小化注入 `KEYSTORE_PATH` 等环境变量（见 `.github/workflows/release.yml` 的「还原签名密钥」步骤）。

### 签名密钥的安全边界（重要）

发布链路的安全评审结论是：**签名密钥不能与写权限同处一个 job**。当前 `.github/workflows/{release,dev}.yml` 按以下方式隔离：

| 措施 | 说明 |
|---|---|
| build / publish 拆分 | build job 只有 `contents: read` 并接触密钥；publish job 只有 `contents: write` 且**不接触任何密钥** |
| 密钥不进 job 级 env | 也不经 `$GITHUB_ENV` 传播，只在「校验/还原/构建/验签」四步内按需注入 |
| 受保护环境 | 密钥取自 environment `release`（正式）/ `dev`（测试）。建议给 `release` 配置 **Required reviewers** 与 **Deployment branches 仅 main/master** |
| Action 固定 SHA | 所有第三方 Action 固定到完整 commit SHA，避免浮动 tag 被投毒 |
| 构建前校验 | 先跑 `gradle/actions/wrapper-validation`，防止被替换的 `gradle-wrapper.jar` |
| 产物校验 | 构建后用 `apksigner` 比对 APK 签名证书与本仓库 keystore 一致，并产出 `SHA256SUMS` 随 Release 发布 |

> 若要把密钥从仓库级 Secrets 迁移到环境级：环境 secrets **不会**与仓库 secrets 共享，请分别加入 `release`（正式发布，建议加审批）与 `dev`（测试构建，可不加审批，保持免打扰）。
> 需要手动开启一次的保护项：分支保护 + `Require review from Code Owners`（`.github/CODEOWNERS` 已声明工作流与构建配置的所有者）。

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
- 查看运行日志：「校验签名密钥已配置」与「还原签名密钥」步骤正常执行、「校验 APK 签名并生成校验和」输出的指纹与 keystore 一致
- 下载 Release 里的 APK 安装验证；Release 同时附带 `SHA256SUMS`，可用 `sha256sum -c SHA256SUMS` 校验，或用 `apksigner verify --print-certs app-release.apk` 核对证书

> 提示：首次配置正式签名后产出的 APK 与之前 debug 签名的包**签名不同**，已安装旧包的用户需卸载后才能覆盖安装。正式发布前务必配好签名；未配置时 CI 会直接失败（不会再用 debug 签名顶替）。

## 7. 常见坑

1. `versionCode` 只增不减；本地测试装过 `20304`，后面就不能再装 `20303`（需先卸载）。
2. `versionName` 不要用 `1.0`、`1.0.0.1` 等非 SemVer 写法；三段的语义化版本便于 changelog 与 tag 一一对应。
3. 修改版本号时 `versionCode` 与 `versionName` **必须同步改**，只改一个会导致「版本号没变但 code 变了」的困惑。
4. tag 命名统一 `v` 前缀小写（`v1.0.1`），与 versionName 完全一致，便于脚本解析。

## 8. dev 分支：只构建不发布

**场景**：dev 分支的开发包需要可安装验证；**versionName 与 master 保持一致**（同一套语义版本系统），只发测试版 Pre-release，不打正式 tag、不占用正式版本号。

**已部署**：`.github/workflows/dev.yml`（push 到 `dev` 分支或手动触发），版本解析用与 release 工作流相同的内联脚本（解析最近 tag 之后的 Conventional Commits）。

| 项 | 行为 |
|---|---|
| 触发 | push `dev` 分支 / 手动 `workflow_dispatch` |
| versionName | **与 master 同一套语义系统**：解析最近 tag 之后的 Conventional Commits（feat→minor / fix→patch / BREAKING→major），两边规则与结果一致 |
| versionCode | **递增**：`2_030_000_000 + 自 2026-01-01 UTC 起的分钟数`（如 `2030385350`），必然大于任何历史已装版本、覆盖安装不降级，且同一小时内多次构建也能区分先后 |
| 发布 | 同时产出 **Actions Artifact** 与 **GitHub Pre-release**（`v<版本>-dev.<N>`，标记为 prerelease；测试版通道靠 Release 资产才能匿名下载） |
| 签名 | 与正式发布**同一套正式签名**（未配置签名密钥时直接失败，不产出 debug 签名包）；密钥取自 environment `dev` |
| 旧测试版保留 | **只保留最近 10 个** `v<版本>-dev.<N>`（release 与 tag 一起清）；判据是 tag 名正则 + `isPrerelease`，正式版 release 与其 tag 一律不动 |

**与 release 工作流的关系**：版本解析逻辑**相同（各自内联）**，versionName 规则完全一致——dev 与 master 共享 git tag 历史，同一时刻解析出的版本号相同（如 master 合并 dev 后发布 0.2.0，dev 分支在此之前构建的包也是 0.2.0）。两者独立触发、互不干扰；versionCode 均为时间基准，天然错开。

> ⚠️ 经验教训：版本解析必须用**内联 `run` 步骤**写 `$GITHUB_OUTPUT`——曾尝试抽成 composite action 复用，但其输出在本环境不生效，导致 release 打了空 tag `v`、versionName 为空（`fix(ci)` 已改回内联）。
> ⚠️ 版本基准**必须排除 prerelease 标签**（`vX.Y.Z-dev.N`，含 `-`）：否则测试版 tag 会被当作基准，版本号随每次 dev 构建虚高（v0.2.0 之后曾错误地出现 v0.4.0-dev）。解析脚本用 `git tag --list 'v*' | grep -v -- '-' | sort -V | tail -n 1` 取基准；测试版应保持 `v<下一个版本>-dev.<N>`，直到对应正式版发布后再前进。

**注意事项**：
- dev 包与 release 包均使用同一套正式签名，因此**可互相覆盖安装**（若曾装过 debug 签名包，则需先卸载）。
- versionCode 已改为**分钟粒度**（见 §2），同一小时内多次构建不再撞号；`concurrency` 串行化仍然保留，避免并发发布互相覆盖。
- dev 构建会创建 `v<版本>-dev.<N>` 形式的 **prerelease tag**（用于测试版更新通道）；正式版基准解析用 `git tag --list 'v*' | grep -v -- '-'` 排除带 `-` 的测试版标签，因此这些 tag 不会推高正式版本号。
- 旧测试版由 `dev.yml` 发布步骤之后的「清理旧测试版预发布」自动收敛到最近 10 个（2026-09-25 首次清理：48 → 10，另有 2 个无 release 的孤儿 tag 一并删除）。

> ⚠️ 删 tag **不要用 `gh release delete --cleanup-tag`**：它删 tag 时请求
> `/git/refs/tags%2F<tag>`（把 `tags` 后的斜杠一并编码进 ref 名），GitHub 回
> `422 Reference does not exist`——结果是 release 删掉了、tag 永久残留
> （首次清理时 38 个 release 全部删成功，38 个 tag 一个都没删掉）。
> 正确做法是删完 release 后单独调 `gh api -X DELETE "repos/$REPO/git/refs/tags/$tag"`。
