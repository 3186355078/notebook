# WorkLog AI

WorkLog AI 是一款面向个人使用的 Android 工作日志应用。它以本地记录为核心，计划支持文字、图片和简单表格，并在用户明确允许时调用大模型生成周报和月报。

当前仓库已完成阶段 1～8：除日志、历史、AI 总结和自动任务外，现已支持 Markdown 导出、SAF 文件交互与完整 ZIP 备份恢复。阶段状态与实际验证结果见 [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)。

## 当前可用界面

- “今天”作为启动页，加载或创建当天本地日志。
- 可以新增、编辑、上移、下移和删除 TEXT 内容块。
- 输入后以 600 ms 防抖自动保存；失焦、进入后台、离开页面和切换日期会主动 flush。
- 保存状态显示为“正在保存 / 已保存 / 保存失败，点击重试”。
- 可添加图片、查看缩略图/大图、编辑图片说明；图片文件缺失时显示受控状态。
- 可添加默认 2×2 表格，编辑标题、列名和单元格，并增删行列（最多 50 行、8 列）。
- “历史”支持最近、日、周、月视图与标题、文字、图片说明、表格内容搜索。
- “总结”支持手动生成当前或过去的自然周/月总结、查看生成状态、编辑、恢复 AI 原始版本和复制。
- “今天”右上角可进入设置页，并可返回。
- 界面跟随系统浅色/深色模式，支持系统字体缩放和边到边显示。

当前 ADB 环境已恢复。Android 16 / API 36 的 HONOR PPG-AN00 真机已实际执行 41/41 个 Instrumentation 方法，failures、errors、skipped 均为 0；设备序列号不进入文档。Android 10～13 第二台真机或模拟器专项兼容测试按用户明确要求跳过，该项未执行、不宣称通过。

阶段 4 当时已通过工程验证与测试矩阵验收：70 个 JVM/Robolectric 测试、APK、Android Test 编译、Lint、Detekt 与 ktlint 均成功。FileStore 覆盖 JPEG/PNG、EXIF、缩放、原子写入失败、路径边界和孤儿清理；图片说明和表格 Draft 覆盖防抖、失败保留、重试、flush、排序与删除。其后阶段 9 已补充上述 Android 16 真机矩阵。

## 今天页自动保存

编辑内容会先保存在 ViewModel 草稿缓冲区，随后按内容块独立防抖保存：

- 连续输入 600 ms 内只写入最终文本。
- 不同文字块的等待任务相互独立。
- Repository 的旧数据回流不会覆盖未保存草稿。
- 保存失败时文本继续留在当前页面，可点击顶部状态重试。
- 同一块写入保持串行；旧保存完成后若发现存在更新草稿，会继续保存最新版本。

系统强制终止进程前的极短输入窗口无法绝对保证，但失焦、`ON_STOP`、页面离开及日期切换均会立即触发保存。

## 当前数据层能力

- 每个本地自然日唯一一条 `WorkEntry`，支持创建、查询、软删除和恢复。
- `TEXT`、`IMAGE`、`TABLE` 三种内容块可通过 Repository 创建、更新、排序和删除；UI 不接触 Room Entity 或 JSON。
- Attachment 只保存私有文件相对路径和元数据；删除块或物理清理日志会返回待清理路径，真实文件删除留给后续 FileStore 协调。
- 周报和月报按“类型 + 起止日期”唯一，保存时更新既有周期记录而非无条件替换。
- `TableContentCodec` 使用 Kotlin Serialization，非法 JSON 返回受控数据错误，不会让 UI 解析数据库字符串。
- `SummarySourceHasher` 使用规范化 SHA-256；不包含图片绝对路径、时间戳或密钥。

## 技术基线

- Kotlin 2.0.21
- Android Gradle Plugin 8.12.1 / Gradle 8.13
- `minSdk 26`，`compileSdk 36`，`targetSdk 36`
- Jetpack Compose + Material 3，单 Activity
- MVVM + Repository（从阶段 2 开始落地业务实现）
- Hilt 2.55、Room、DataStore、WorkManager
- Coroutines / Flow、Retrofit、OkHttp、Kotlin Serialization
- Coil 3、Android Photo Picker（从阶段 4 使用）
- JUnit、MockK、Compose UI Test、Android Lint、Detekt、ktlint

Hilt 固定为 2.55，是因为它与当前 Kotlin 2.0.21 处理器链一致，并兼容本项目使用的 AGP 8.12.1。依赖升级必须作为独立变更执行完整构建和测试，不能只修改版本号。

数据库当前版本为 `1`。Room schema 已生成并纳入版本控制：`app/schemas/com.worklogai.app.core.database.WorkLogDatabase/1.json`。后续 schema 变更必须提升版本号、提供显式 Migration，并更新该文件；项目禁止使用破坏性迁移回退。

## 本地构建

### 环境要求

- Android Studio 2025.1.2 或能支持 AGP 8.12.x 的更新稳定版本
- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 35.0.0 或更新的兼容版本

Android Studio 通常会自动配置 SDK。命令行构建时，在项目根目录创建不会提交到 Git 的 `local.properties`：

```properties
sdk.dir=D\:\\Android\\Sdk
```

请替换为本机 SDK 路径。不要在这个文件或 Gradle 文件中放 API Key。

### 常用命令

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:detekt
.\gradlew.bat :app:ktlintCheck
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:detekt
./gradlew :app:ktlintCheck
./gradlew :app:compileDebugAndroidTestKotlin
```

连接模拟器或设备后可执行仪器测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。

## 在 Android Studio 中运行

1. 用 Android Studio 打开仓库根目录。
2. 选择 JDK 17 作为 Gradle JDK。
3. 等待 Gradle Sync 完成。
4. 选择 API 26 以上的模拟器或真机。
5. 运行 `app` 配置。

启动后应默认看到“今天”，底部包含“今天 / 历史 / 总结”，右上角包含设置入口。

## 隐私与大模型配置

工作日志默认只保存在应用本地。应用不会申请通讯录、位置、电话或大范围存储权限；图片通过系统 Photo Picker 选择，采样、纠正 EXIF 方向并压缩后写入 `files/attachments/images/`。数据库仅记录受控相对路径、MIME 类型、尺寸、文件大小和可选说明，不保留临时 URI 或图片二进制。

阶段 6 已通过 `AiSummaryProvider` 接入 Mock 和可配置的 OpenAI Compatible 服务，并遵守以下约束：

- API Key 不进入源码、Room、DataStore、BuildConfig、日志或普通备份。
- API Key 通过 Android Keystore 支持的 `SecretStore` 保存，并支持用户删除。
- 只有用户允许且生成总结时，才发送所选时间范围内的必要文本。
- 日志正文、Authorization Header 和完整 AI 请求体不得写入日志或崩溃信息。
- AI 或网络失败不得影响本地日志。

直接从手机调用第三方大模型 API 适合个人使用和原型验证。如果未来公开发布应用，应改用自有服务端代理请求，避免供应商密钥长期保存在客户端。Provider 抽象会为这一迁移保留边界。

设置页默认启用 Mock Provider；切换到真实服务后，用户可配置 HTTPS Base URL、模型名称和 10～120 秒超时，并用密码输入框保存或删除 API Key。数据库和普通 DataStore 只保存非敏感设置，以及经 AES-GCM 加密后的密文和 IV；Android Keystore 密钥材料不离开系统密钥库。应用不回显已保存的完整 API Key。

手动生成周报/月报前会显示发送的时间范围与可处理记录数，仅发送允许 AI 处理的文字、图片说明和表格文本，不发送图片文件、绝对路径、URI、UUID 或 API Key。默认输入限制为 30,000 字符；超过时会明确提示。AI 原始结构化 JSON 与用户可编辑 Markdown 分别保存，重新生成失败会保留上一次成功内容。

## 数据库测试

`testDebugUnitTest` 同时运行纯 Kotlin、TodayViewModel 和 Robolectric 驱动的内存 Room 测试，因此无需设备即可验证自动保存、SQL、外键、级联删除、唯一约束与 Repository 事务。首次执行会下载 Robolectric 运行时依赖；缓存完成后可使用离线模式：

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest
```

设备仪器测试仍需连接可用的模拟器或真机，不能以“已编译”代替“已执行”。

## 项目结构

```text
app/src/main/kotlin/com/worklogai/app/
  app/                 Application、Activity、导航
  core/                通用能力和设计系统
    database/           Room、Entity、DAO、关系、mapper、codec、Repository 实现
    model/              不依赖 Room 的领域模型
    repository/         领域 Repository 接口
  feature/             editor、history、summary、settings
  ai/                  Provider、Skill、解析器（阶段 6）
  worker/              自动总结检查、生成和结果通知（阶段 7）
```

MVP 先保持单 Gradle Module，通过 package 和接口控制依赖方向。详细决策、计划数据模型和安全边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 安全提交检查

提交前至少执行：

```powershell
git status --short
git diff --check
git grep -n -I -E "(sk-[A-Za-z0-9_-]{16,}|api[_-]?key[[:space:]]*[=:][[:space:]]*[^<])" -- . ":(exclude)README.md"
```

同时人工确认没有提交 `local.properties`、`.env*`、`secrets.properties`、签名文件、真实服务地址中的凭证或包含工作日志正文的测试夹具。

## 开发文档

- [ARCHITECTURE.md](ARCHITECTURE.md)：架构边界、计划数据模型和关键决策。
- [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)：阶段状态、验收条件和下一步。

## 阶段 5：历史工作记录

历史页支持最近、按日、按周和按月浏览。最近记录按日期倒序显示，每次最多读取 30 条，可用“加载更多”继续浏览；没有实际内容的空日志、仅空白文字的日志和软删除日志不会出现。

搜索会在停止输入 300 ms 后执行，覆盖标题、文字块、图片说明、表格标题、列名和单元格。英文匹配不区分大小写，中文采用规范化后的子串匹配。表格 JSON 仅在数据层的 IO dispatcher 中解析；单个损坏表格不会阻断其余列表或搜索结果。当前策略适合个人本地日志规模，后续数据量显著增长时可引入 FTS 或搜索索引。

从历史卡片进入的编辑页复用文字、图片和表格编辑器，但固定在所选日期，不会在跨午夜时跳转到今天；离开前仍会 flush 未保存 Draft。未来日期不能创建或打开日志。

阶段 5 的 JVM/Robolectric 测试与既有测试共 92 个，Android Compose 测试源码已编译。受当前 ADB 环境限制，设备仪器测试尚未执行。

阶段 6 新增 Provider、Skill、SecretStore 与生成用例测试 13 个；阶段 7 新增 37 个 Worker、调度、设置、周期补漏与自动/手动并发测试。当前 JVM/Robolectric 测试总数为 142 个，均已通过。Android Compose 测试代码可编译，设备仪器测试仍受 ADB 环境限制而未执行。

## 阶段 6：手动 AI 总结

总结页默认显示本周，可切换周报/月报、查看过去周期并阻止未来周期。Mock Provider 可离线演示完整流程。真实 Provider 调用 OpenAI Compatible 的非流式 `POST /v1/chat/completions`，请求 `response_format = json_object`，支持超时、网络不可用、限流、认证、模型不存在和服务端错误的去敏映射。

## 阶段 7：自动周报和月报

设置页可分别开启自动周报、自动月报、移动网络生成和完成通知。应用启动与设置变更会用 WorkManager 注册唯一的每日检查任务；检查任务只识别已结束的自然周（周一至周日）与自然月，并补查最近 4 个周、2 个月。每次最多安排 3 个周期，避免积压时突发请求。

每个周期的生成任务使用稳定唯一名称与 WorkSummary 的周期唯一约束，重复检查不会重复生成。真实 Provider 在未允许移动网络时仅使用非计费网络；Mock Provider 不要求网络。可重试网络、超时、限流和服务端错误使用 30 分钟指数退避且最多 3 次，永久配置或解析错误会结束为失败。通知只显示周期和成功/失败状态，不显示日志正文、图片说明、表格、模型响应或密钥。

自动总结沿用手动总结的 Provider、Skill、Repository 和 SourceHash；只发送允许 AI 处理的内容。自动任务和检查输入只保存总结类型、起止日期和触发来源，绝不保存 API Key、Authorization Header、日志正文、图片或表格 JSON。

`WorkSummarySkill` 的版本为 `work-summary-skill-v1`。它集中管理 System Prompt、输入构建、30,000 字符限制、输出 JSON 解析与 Markdown 格式化；首次 JSON 无法解析时仅发起一次 JSON 修复请求。模型输出的 `sourceDates` 会被限制为实际发送日志的日期，避免引用不存在的来源。

## 阶段 8：Markdown、完整备份与恢复

数据管理页可把单日日志、周报或月报通过 SAF 导出为 Markdown，也可创建 `.worklog-backup.zip` 完整备份。备份包含版本化 JSON、Manifest 和仍被数据库引用的私有图片；每个声明文件记录 size 与 SHA-256。图片字节不会重新编码，缺失附件会在预览中提示。API Key、Keystore 密钥/密文、调度运行状态、Worker UUID、SAF URI 和绝对路径不进入备份。

备份格式当前为 `worklog-ai-backup` v1。恢复会完整替换工作日志、内容块、附件元数据、总结、图片和非敏感设置；选择文件后先完成格式、白名单路径、重复 Entry、ZIP 资源限制、SHA/size、图片头和 DTO 关系预检，普通损坏备份不会修改当前数据。附件切换、Room 与设置恢复失败会补偿回滚；极端补偿失败由非敏感 restore journal 标记，并在下次启动保守修复。Scheduler 协调失败只产生 warning，不回滚已恢复的用户数据。

备份默认**未加密**，请只保存到可信位置。当前安全上限集中在 `BackupSafetyLimits`，包含归档大小、Entry 数、单文件/总解压大小、压缩比、JSON/附件大小、路径长度和记录数；它们用于拒绝 Zip Slip、重复 Entry 和 ZIP Bomb。大型真实备份及不同 OEM SAF Provider 的行为仍需要真机验收。

阶段 8 的强制工程回归命令为：

```powershell
.\gradlew.bat --offline --no-daemon :app:assembleDebug --rerun-tasks --no-build-cache
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --no-build-cache
.\gradlew.bat --offline --no-daemon :app:compileDebugAndroidTestKotlin --rerun-tasks --no-build-cache
.\gradlew.bat --offline --no-daemon :app:lintDebug --rerun-tasks --no-build-cache
.\gradlew.bat --offline --no-daemon :app:detekt :app:ktlintCheck --rerun-tasks --no-build-cache
```

阶段 8 封版时的强制回归实际执行 201 个 JVM/Robolectric 测试，failure/error/skipped 均为 0；Android Compose 测试源码共 16 个方法并编译通过。当时 ADB 不可用，相关设备流程随后已在阶段 9 的 Android 16 真机矩阵中补齐。

## 阶段 9：内部试用 Release

阶段 9 已建立本地 Git 基线，并在 HONOR PPG-AN00（Android 16 / API 36）真机完成 41/41 个 Instrumentation 测试及 TEXT、IMAGE、TABLE、历史搜索、Mock 周/月报、Keystore、SAF、通知深链、WorkManager、损坏备份、restore journal、跨午夜/时区、50×8 表格和 1,000 条日志矩阵。当前 JVM/Robolectric 基线为 213/213，其中包含恢复完成后必须重建 Today 目的地的回归测试。设备序列号、测试密钥和工作正文不进入报告。

用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试；该项未执行、不宣称通过，作为 0.1.0 内部试用风险接受。旧设备用户应重点反馈 Photo Picker/兼容选择器、SAF、通知和 OEM 后台调度。

内部 Release 为 versionName 0.1.0、versionCode 1，Release 启用 R8 与资源压缩，禁止明文网络和 Android 系统备份。签名 Keystore 必须位于仓库外，四个 `WORKLOG_RELEASE_*` 环境变量只在当前构建进程中存在。Debug 构建不需要 Release 密钥；Release 配置缺失时会安全失败。

在用户已准备仓库外 Keystore 后，可在本地交互执行：

```powershell
.\tools\release-build.ps1
```

脚本不会把密码写入命令行、文件或报告。最终构建完成并验证后运行 `tools/package-release.ps1`，把 APK、AAB、SHA-256、Release 报告和 R8 mapping 归档到已被 Git 忽略的 `release-artifacts/0.1.0/`。

最终提交后可交互运行 `tools/verify-clean-release.ps1`。它从本地仓库创建临时干净克隆，只写入临时 SDK 路径，强制无缓存执行 213 个 JVM 测试、Release Lint、APK/AAB 构建及签名验证；成功后清除签名环境变量和临时克隆，不复制 API Key、用户数据或构建缓存。

发布与试用文档：

- [PRIVACY.md](PRIVACY.md)：本地数据、AI、API Key、备份和通知隐私边界。
- [CHANGELOG.md](CHANGELOG.md)：0.1.0 功能和已知限制。
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)：签名、测试、协议、产物和风险清单。
- [INTERNAL_TESTING_GUIDE.md](INTERNAL_TESTING_GUIDE.md)：内部安装、使用和安全反馈方式。
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)：主要依赖版本与许可证。
- [设备验收报告](docs/release/0.1.0/DEVICE_VALIDATION_REPORT.md)：已执行设备矩阵和兼容性豁免。
