# WorkLog AI 开发计划

## 状态说明

- `[x]` 已实现并通过本阶段验证
- `[ ]` 尚未开始或未满足退出条件

每轮只推进一个明确阶段。任何阶段必须在相关测试通过、文档更新、无敏感信息后才能标记完成；不能通过删除测试、降低规则或留下核心 TODO 来完成验收。

## 阶段 1：项目初始化

状态：`[x]` 已完成

- [x] Gradle Kotlin DSL、Version Catalog 和可校验 Wrapper
- [x] Kotlin、Compose、Material 3、Hilt、Room 等兼容依赖基线
- [x] 单 Activity 和 Hilt Application
- [x] “今天 / 历史 / 总结”底部导航与设置入口
- [x] 简洁浅色/深色 Compose 主题和中文资源
- [x] JUnit 与 Compose UI Test 基线
- [x] Android Lint、Detekt、ktlint 配置
- [x] README、ARCHITECTURE、DEVELOPMENT_PLAN
- [x] Debug APK 编译验证

阶段 1 不实现业务持久化、内容编辑、AI 请求或备份。页面空状态用于验证导航和主题，不伪装成已完成的功能。

## 阶段 2：数据库和 Repository

状态：`[x]` 已完成

范围：

- [x] 定义 `WorkEntryEntity`、`ContentBlockEntity`、`AttachmentEntity`、`WorkSummaryEntity`。
- [x] 实现日期/时间/枚举 TypeConverter、外键、唯一约束和必要索引。
- [x] 实现按日、日期范围、稳定块排序、附件路径和总结周期 DAO 查询。
- [x] 定义领域模型与 mapper，DAO Entity 不泄漏到 UI。
- [x] 实现 `WorkEntryRepository` 与 `WorkSummaryRepository` 的 Room 实现。
- [x] 创建数据库版本 1，并导出 schema JSON 到 `app/schemas/`。
- [x] 实现受控数据错误映射，不记录日志正文或敏感路径。
- [x] 实现 `TableContentCodec` 和稳定的 `SummarySourceHasher`。

已完成验证：

- [x] 创建、更新、软删除和恢复工作日志。
- [x] 内容块排序、删除和附件路径交接。
- [x] 日期范围查询包含起止日期。
- [x] 日志/内容块物理删除后的级联元数据处理。
- [x] 总结周期唯一约束与 Repository 保存、编辑和状态更新。
- [x] 表格 JSON、类型转换和源数据 hash 的纯 Kotlin 测试。
- [x] 内存 Room schema 版本与数据库约束测试。

退出条件：已满足。真实 Room 测试在 JVM/Robolectric 上执行；设备仪器测试将在可用 ADB 环境中执行，当前阶段仅保证 Android Test 编译通过。

## 阶段 3：今日记录与文字块

状态：`[x]` 已完成并通过离线 Gradle 验证

- [x] 今天页通过 `WorkEntryRepository` 加载或创建当天日志。
- [x] TEXT 块新增、编辑、删除、上移/下移排序。
- [x] `TodayViewModel`、UI State、Action、Event 与领域 UI Model。
- [x] 每块独立 600 ms debounce 自动保存及本地 Draft 合并。
- [x] 失焦、页面离开、`ON_STOP`、排序、删除和日期切换 flush。
- [x] 保存中、已保存、失败重试状态；失败保留 Draft。
- [x] 同一块串行保存，防止旧写入覆盖新文本。
- [x] 日期切换前 flush 并隔离旧日期草稿。
- [x] IMAGE/TABLE 只读占位卡片和组件 Preview。

已完成验证：27 个 JVM/Robolectric 测试（其中 TodayViewModel 为 12 个）、Debug APK、Android Test 源码编译、Lint、Detekt、ktlint 和工作区检查均已通过。设备仪器测试仍待 ADB 环境恢复后执行。

## 阶段 4：图片和表格

状态：`[x]` 已完成并通过工程验证与阶段 4 测试矩阵验收。

- [x] Android Photo Picker，不申请大范围存储权限。
- [x] `AttachmentFileStore` 将图片采样、EXIF 旋转、压缩并原子复制到 `files/attachments/images/`，Room 仅保存相对路径。
- [x] Coil 缩略图、大图查看、说明和文件缺失状态。
- [x] 图片块 + 附件元数据通过 Repository 单事务创建；数据库失败时补偿删除刚导入的文件；删除块后尽力清理私有文件。
- [x] 使用已完成的 `TableContent` 与 `TableContentCodec` 构建表格编辑 UI。
- [x] 默认 2×2，限制最大 50 行、8 列，支持标题、行列增删和单元格编辑。
- [x] 表格和图片说明共享块级 Draft、600 ms debounce、flush 与失败保留机制。
- [x] Robolectric FileStore 测试覆盖 JPEG/PNG、缩放、EXIF、格式校验、路径安全、删除与孤儿清理。

已完成工程验证：70 个 JVM/Robolectric 测试、Debug APK、Android Test 源码编译、Lint、Detekt、ktlint 和工作区检查均已通过。阶段 4 新增 FileStore 16 个测试、TodayViewModel 14 个阶段 3 基线测试加 14 个图片/表格测试、表格编辑器 9 个测试和 Repository 图片事务 4 个测试。Android Compose 测试代码已编译；设备仪器测试仍待 ADB 环境恢复后执行，未宣称已运行。

验收覆盖：表格 round-trip/损坏 JSON/边界限制、文件复制与原子改名失败、临时文件清理、路径穿越、文件丢失、图片删除与数据库失败补偿、图片说明 Draft、表格整体 Draft 和 Android Compose 测试代码编译。

## 阶段 5：历史记录

状态：`[x]` 已完成并通过工程验证。

- [x] 最近有效日志倒序列表、稳定摘要与“加载更多”（初始/每页 30 条）。
- [x] 日、周、月闭区间查询；自然周固定为周一至周日，未来日期不可打开。
- [x] 标题、TEXT、图片说明和 TABLE 内容的 300 ms 防抖搜索。
- [x] 固定日期编辑页复用现有编辑器；今天跨午夜行为与历史固定日期隔离。
- [x] JVM/Robolectric 历史 Repository、摘要、日期范围和 ViewModel 测试，以及 Android Compose 测试代码编译。

实际工程验证：92 个 JVM/Robolectric 测试通过；`assembleDebug`、Android Test 编译、Lint、Detekt、ktlint 与工作区检查通过。设备仪器测试仍待 ADB 环境恢复后执行。

## 阶段 6：AI Provider、WorkSummarySkill 与手动周报/月报

状态：`[x]` 已完成并通过工程验证。

实际工程验证：105 个 JVM/Robolectric 测试通过（阶段 1～5 的 92 个既有测试与阶段 6 新增 13 个测试）；`assembleDebug`、Android Test 源码编译、Lint、Detekt、ktlint 与工作区检查均已通过。Android Compose 测试代码已编译；设备仪器测试仍待 ADB 环境恢复后执行。

- [x] `AiSummaryProvider`、`MockAiSummaryProvider`、`OpenAiCompatibleSummaryProvider`，非流式 OpenAI Compatible 请求与受控错误映射。
- [x] 设置页的 Mock 开关、HTTPS Base URL、模型、超时、API Key 密码输入、保存、删除与连接测试。
- [x] Keystore-backed `SecretStore`：API Key 仅以 AES-GCM 密文与 IV 写入 DataStore，支持覆盖和删除。
- [x] `WorkSummarySkill`：`work-summary-skill-v1` Prompt、输入构建、30,000 字符限制、JSON 模型、解析、一次修复重试与 Markdown 格式化。
- [x] 只提取允许 AI 处理的日志；图片只发送说明文字；`sourceDates` 仅保留实际输入日期。
- [x] 手动自然周/自然月选择、未来周期限制、PENDING/GENERATING/SUCCESS/FAILED、重新生成确认、用户编辑、恢复原始版本和复制。
- [x] 原始 JSON、用户 Markdown、去敏失败消息与 sourceHash 过期提示；失败保留上一版成功内容。
- [x] Provider、Skill、SecretStore、生成用例和 Compose 内容测试源码。

不包含：自动 WorkManager 总结、Markdown/SAF 导出、备份恢复、云同步或真实设备仪器执行。

## 阶段 7：自动总结任务

状态：`[x]` 已完成并通过工程验证

- [x] 每日唯一 WorkManager 检查任务；检查已结束自然周和自然月，不承诺精确执行时间。
- [x] 自动周报/月报、移动网络和完成通知开关；首次启用需要用户确认自动发送范围内允许处理的内容。
- [x] 周期规范化、稳定唯一 work 名称、WorkSummary 周期唯一约束和 SummaryGenerationMode.AUTOMATIC 幂等保护。
- [x] 周补漏最多 4 个、月补漏最多 2 个，单次检查最多安排 3 个周期；调度状态仅存 DataStore，不修改 Room schema。
- [x] 生成任务使用网络、电量和存储约束；可重试错误 30 分钟指数退避、最多 3 次；永久失败不无限重试。
- [x] 成功/最终失败的私有通知与总结深链；通知不含工作内容、图片说明、表格或密钥。
- [x] Worker、调度、设置、启动协调、状态存储、周期补漏、手动/自动并发和深链的 JVM/Robolectric 测试。

工程实现使用应用级 Hilt 注入的 `WorkLogWorkerFactory` 创建 Worker。由于当前环境无法下载 `androidx.hilt:hilt-work`，未引入该可选依赖；该工厂仍把 Worker 构造与 UI 分离，且不影响后续切换到官方 Hilt Work 集成。

实际工程验证：142 个 JVM/Robolectric 测试通过（阶段 1～6 的 105 个既有测试与阶段 7 新增 37 个测试）；`assembleDebug`、Android Test 源码编译、Lint、Detekt、ktlint 与工作区检查均通过。Android Compose 测试代码仅完成编译；设备仪器测试仍待 ADB 环境恢复后执行。

## 阶段 8：备份、恢复和质量收尾

状态：`[x]` 已完成并通过完整备份恢复安全矩阵及强制全量回归。

- [x] 单日日志、周报和月报 Markdown 导出，包含表格转义与安全文件名。
- [x] `worklog-ai-backup` v1 ZIP、版本化 DTO、Manifest、SHA-256、size 与相对路径白名单。
- [x] 重复/规范化冲突 Entry、未知大小流级计数、ZIP Bomb、记录数和字段长度限制。
- [x] WorkEntry、ContentBlock、Attachment、TABLE 与 WorkSummary 关系预检。
- [x] staging/old 附件切换、真实 Room 单事务替换、非敏感设置恢复和补偿回滚。
- [x] Scheduler 失败作为可重建 warning；严重补偿失败返回 `RecoveryRequired`。
- [x] 非敏感 restore journal 与启动修复；API Key、SAF URI、正文和绝对路径不进入 journal。
- [x] Storage Access Framework 导入导出与数据管理状态机、深色模式和无障碍测试源码。

最终工程验证：201 个 JVM/Robolectric 测试全部通过（阶段 1～7 基线 142 个，阶段 8 新增 59 个）；Debug APK、Android Test 源码编译、Lint、Detekt、ktlint 和未跟踪文件全覆盖检查均通过。完整回归从已删除构建目录开始，并对每个 Gradle 分项使用 `--rerun-tasks --no-build-cache`。Android Compose 测试源码共 16 个方法并成功编译；ADB 不可用，因此设备仪器测试未执行。

Room database version 与备份协议版本继续分别为 1，Schema 未变化且不需要 Migration。备份默认未加密，API Key 和 Keystore 数据不备份。阶段 8 后停止新增主功能，转入 ADB/真机验收、签名、隐私说明与内部试用准备。

## 每轮固定验证

根据改动范围执行以下命令，并在汇报中逐条写出真实结果：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:detekt
.\gradlew.bat :app:ktlintCheck
git diff --check
```

有可用设备或模拟器时，再执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

测试汇报必须区分：

- 已执行并通过。
- 仅成功编译，未在设备执行。
- 因明确环境限制未执行。

## 增强功能候选

以下内容不进入 MVP 阶段范围，只有核心流程稳定并有真实需求证据后才评估：月度超长日志分段总结和二次汇总、多模态图片理解、PDF 导出、自有服务端代理、可选云备份。账号、团队协作、聊天、考勤、审批、复杂看板、会员和广告不在当前产品方向内。
