# WorkLog AI 架构

## 1. 架构目标

WorkLog AI 的主链路是“按日记录 → 本地持久化 → 历史检索 → 可选生成周/月总结”。架构优先级依次是：数据不丢失、离线可用、隐私边界清晰、代码易于验证，最后才是功能扩展速度。

当前 0.1.0 内部试用版已完成阶段 1～9，0.2.0-dev 正在实施阶段 10。本文同时描述新增每日待办、Room 1→2 Migration、备份格式 v2 和设计系统边界。

明确不属于 MVP 的能力包括账号、团队协作、聊天、考勤、审批、复杂项目管理、云同步、OCR、图片识别、语音转文字、会员和广告。

## 2. 总体结构

MVP 使用单 `app` Gradle Module，通过 package 保持边界。这样能控制初始化复杂度，同时保留未来拆分 Module 的清晰接缝。

```text
Compose UI
    ↓
ViewModel / UI state
    ↓
Use case（仅在跨多个仓库或包含明确业务规则时引入）
    ↓
Repository ─────────→ Room / DataStore / private FileStore
    │
    └───────────────→ AiSummaryProvider（仅生成总结时）

WorkManager ────────→ 同一组 use case / repository，不直接依赖 UI
```

依赖只能沿箭头方向流动。UI 不直接访问 DAO、文件路径、网络 DTO 或数据库 JSON；Worker 不调用 ViewModel；AI Provider 不读写 Room。

## 3. Package 边界

```text
com.worklogai.app
  app/
    navigation/       顶层路由与导航模型
    MainActivity      单 Activity 宿主
    WorkLogApplication  Hilt Application
    WorkLogApp        应用导航与顶层 Scaffold
    di/               Hilt 绑定与平台对象
  core/
    common/           结果类型、时间与调度抽象
    model/            跨层领域模型
    database/         Room、DAO、entity、mapper
    datastore/        非敏感设置
    designsystem/     主题和复用 Compose 组件
    network/          Retrofit/OkHttp 基础设施
    security/         SecretStore 与 Keystore 实现
    file/             私有附件存储和导入导出
  feature/
    editor/           今日和指定日期编辑
    history/          时间线、筛选与搜索
    summary/          周报/月报查看和编辑
    settings/         必要设置
  ai/
    model/            Provider 请求/响应模型
    provider/         Mock 与 OpenAI Compatible 实现
    skill/            WorkSummarySkill
    parser/           容错 JSON 解析
  worker/             周/月自动检查与生成协调
```

只有出现明确收益时才拆 Gradle Module，例如构建时间明显恶化、多人并行边界频繁冲突、需要独立发布/复用，或依赖隔离无法再靠 package 和可见性保证。

## 4. 持久化与领域模型

日期字段表示自然日期，统一按 `LocalDate` 语义处理并通过 TypeConverter 存为 epoch day；审计时间表示瞬间，统一存 epoch milliseconds。自然周固定为周一至周日。所有范围查询使用包含首尾日期的闭区间。

领域模型与 Room Entity 明确分离：`core/model` 和 `core/repository` 不依赖 Room，Entity、DAO、Relation 和 mapper 只存在于 `core/database`。Repository 将关系聚合映射为领域模型；UI 与后续 ViewModel 不得访问 Entity、DAO 或数据库 JSON。

### WorkEntry

| 字段 | 计划类型 | 约束与含义 |
| --- | --- | --- |
| id | String UUID | 主键；由可替换的 `IdGenerator` 生成 |
| entryDate | epoch day / LocalDate | 唯一索引；同一天只有一条主日志 |
| title | String? | 可选，不阻塞快速记录 |
| createdAt | epoch milliseconds / Instant | 创建时间，不随编辑变化 |
| updatedAt | epoch milliseconds / Instant | 成功持久化后更新 |
| allowAiProcessing | Boolean | 默认值在产品实现时显式确定，生成前再次过滤 |
| isDeleted | Boolean | 软删除标记；同日再次编辑恢复原记录 |

自动保存状态是由待写变更、保存任务和错误派生的瞬时 UI 状态，不作为 `WorkEntry` 持久化字段，避免数据库中的状态与真实写入结果不一致。

### ContentBlock

| 字段 | 计划类型 | 约束与含义 |
| --- | --- | --- |
| id | String UUID | 主键 |
| entryId | String | 外键指向 WorkEntry，`ON DELETE CASCADE` |
| blockType | TEXT / IMAGE / TABLE | 使用稳定字符串或 TypeConverter |
| blockOrder | Int | `(entryId, blockOrder)` 索引；按 `blockOrder`、`createdAt`、`id` 稳定排序 |
| textContent | String? | TEXT 内容或可选辅助文本 |
| structuredContent | String? | 类型化 codec 管理的 JSON，UI 不直接处理 |
| createdAt / updatedAt | Long | 审计时间 |

TEXT 的项目符号与待办项保持轻量文本语义，不在 MVP 引入复杂富文本 AST。TABLE 使用类型化 `TableContent`，列和行均有稳定 ID、单元格按列 ID 映射；codec 使用 Kotlin Serialization，忽略未知字段并验证重复 ID、未知单元格和最大 20 列/100 行。非法 JSON 映射为受控数据错误。

### Attachment

| 字段 | 计划类型 | 约束与含义 |
| --- | --- | --- |
| id | String UUID | 主键 |
| blockId | String | 外键指向 ContentBlock，`ON DELETE CASCADE` |
| localPath | String | APP 私有目录下的路径；本阶段仅保存元数据，不保存二进制数据或临时 URI |
| mimeType | String | 经过允许列表和真实内容检查 |
| fileSize | Long | 压缩后大小 |
| width / height | Int | 图片尺寸 |
| caption | String? | 唯一可发送给文本模型的图片内容 |
| createdAt | Long | 创建时间 |

数据库级联只能删除附件元数据。删除业务由 Repository 在删除块或物理清理日志前返回待清理路径；后续 FileStore 再协调真实文件删除。DAO 不操作文件系统，避免数据库事务静默删除磁盘文件。

### WorkSummary

| 字段 | 计划类型 | 约束与含义 |
| --- | --- | --- |
| id | String UUID | 主键 |
| summaryType | WEEKLY / MONTHLY | 周报或月报 |
| periodStart / periodEnd | Long / LocalDate | 规范化自然周期 |
| status | PENDING / GENERATING / SUCCESS / FAILED | 可恢复状态机 |
| sourceHash | String | 规范化输入的 SHA-256 |
| aiProvider / modelName | String? | 生成来源，不含密钥 |
| originalContent | String? | AI 成功结果 |
| editedContent | String? | 用户修改版本 |
| createdAt / updatedAt / generatedAt | Long | 审计时间 |
| errorMessage | String? | 面向用户且去敏的错误，不存堆栈/请求体 |

对 `(summaryType, periodStart, periodEnd)` 建唯一约束，同一周期维护一条聚合记录；`sourceHash` 判断源日志是否变化。Repository 用显式 insert/update 的事务化语义保存周期，禁止 `REPLACE` 掩盖错误。重新生成状态机、AI 调用和自动任务属于后续阶段。

### Entity 关系、删除与范围语义

`work_entries` → `content_blocks` → `attachments` 通过外键串联，两个外键均建索引并使用 `ON DELETE CASCADE`。普通日志查询固定过滤 `isDeleted = true`；软删除只改标记，按同一天再次创建时恢复原行，保留其 ID 与关联内容。物理清理删除 WorkEntry 后由数据库级联删除块和附件元数据。

所有自然日期范围使用包含两端的闭区间：`periodStart <= entryDate <= periodEnd`。`LocalDate` 在 Room 中存储为 epoch day，`Instant` 存储为 epoch milliseconds；不混用时区相关日期、秒级和毫秒级时间戳。

### 数据库版本与 Schema

`WorkLogDatabase` 当前为版本 `2`，名称为 `worklog_ai.db`。Schema 1 与 Schema 2 同时纳入版本控制；`Migration(1, 2)` 只创建 `todo_items`、四个索引和指向 `content_blocks` 的 `ON DELETE SET NULL` 外键。Migration 不重建既有表，不使用 `fallbackToDestructiveMigration()`。

### DailyTodo

`todo_items` 以 `scheduledDate` 归属自然日，优先级为 `URGENT/HIGH/MEDIUM/LOW`，状态为 `NOT_STARTED/IN_PROGRESS/DONE/CANCELED`。状态不是布尔值；`sortOrder` 只表达同日同优先级的手动顺序，每次持久化重排都归一化为从 0 开始的连续整数。默认查询显式采用“未完成在前、优先级、sortOrder、createdAt、id”的稳定顺序。

`linkedContentBlockId` 可空并引用 TEXT 内容块。删除工作记录块时数据库将链接置空而不删除待办；删除待办也不删除已经形成的历史工作记录。`CompleteTodoAndRecordUseCase` 在单个 Room 事务中创建/恢复当天 WorkEntry、追加 TEXT 块并绑定待办，避免部分提交。重新同步会创建新块、更新链接但不静默改写旧事实。

待办 Repository 与工作日志 Repository 分离；跨域联动只存在于 UseCase。Todo 内容不进入 WorkSummarySkill、Provider 连接测试、Worker Data、通知或生产日志。只有用户主动同步后形成的 ContentBlock 才按既有 AI 同意边界参与总结。

### AppSettings

非敏感设置保存在 DataStore：Provider 类型、Base URL、模型名、自动周/月报开关、总结语言、外观、压缩质量、移动网络策略。API Key 不属于 `AppSettings`，只能通过 `SecretStore` 接口访问。

## 5. 关键设计决策

### ADR-001：Local First

Room 是日志、块、附件元数据和总结的单一事实来源。编辑先本地完成；网络和 AI 不参与日志写入事务，失败不能回滚或覆盖日志。

Repository 是 Room 的唯一上层入口：读操作通过 `Flow` 可观察，单次读写为 `suspend`，多表写入使用 `RoomDatabase.withTransaction`。基础设施异常会映射为封闭的 `DataError`，不将 SQLite 原始错误文本传递给 UI。

### ADR-002：单 Module、package-first

MVP 不为形式拆分大量 Gradle Module。业务接口和 Kotlin 可见性先控制边界，达到第 3 节所述条件后再平滑拆分。

### ADR-003：简单块编辑器

TEXT、IMAGE、TABLE 使用独立类型模型和稳定排序，不实现富文本编辑器。表格 JSON 只存在于数据库/备份边界，由 codec 转为类型化对象。

`reorderBlocks` 只接受该日志全部块 ID 的完整排列；拒绝重复、遗漏或跨日志 ID，并在单一事务中规范化为从 0 开始的连续顺序，避免部分更新。

### ADR-004：自动保存

`TodayScreen` 只派发 `TodayAction` 并渲染 `TodayUiState`；`TodayViewModel` 使用 `WorkEntryRepository` 读写领域模型。每个 TEXT 块、IMAGE 说明和 TABLE 块有按“日期 + blockId”隔离的草稿与保存 Job，默认 600 ms debounce。草稿优先于 Repository Flow 返回的持久化基线，因此旧数据不会回退编辑框。

同一块的已启动写入不被新输入并发覆盖：写入成功后检查草稿版本；若草稿已变化，继续串行保存最新文本。失焦、`ON_STOP`、页面离开、结构操作与日期切换会 flush 等待中的草稿。失败保留草稿与失败状态，不向 UI 传递 SQLite 原始错误；用户重试时始终保存最新草稿。

日期变化先 flush 旧日期，再取消旧观察并加载/观察新日期。草稿按日期隔离，旧日期的失败草稿不会污染新日期。`ViewModel.onCleared()` 不作为唯一保存保障。

TEXT、IMAGE 和 TABLE 块通过上移/下移调用完整 ID 列表的 `reorderBlocks`。空白文字块直接删除；有内容的块会先确认。删除失败时保留 UI 数据。图片删除先提交数据库删除、再尽力删除私有文件；无法删除的文件由保守的孤儿清理能力在后续时机处理。

### ADR-005：私有附件存储

Photo Picker URI 只作为一次性输入。`AttachmentFileStore` 在 IO dispatcher 上校验可读图片、采样解码、按 EXIF 方向旋转、将最长边限制为 2048 px，并以 JPEG(85) 或 PNG 写入临时文件后原子改名至 `files/attachments/images/`。数据库保存 `images/<随机 ID>.<扩展名>` 相对路径。Coil 按显示尺寸加载缩略图；原文件缺失时显示“图片文件已不存在”，不能让页面崩溃。文件导入成功而数据库事务失败时由 ViewModel 补偿删除文件；孤儿清理只扫描该受控目录，绝不扫描目录之外的位置。

### ADR-006：简单表格编辑

`TableContentEditor` 复用既有类型化 `TableContent`，不让 Compose 直接解析 JSON。默认表格为 2 列 × 2 行，行/列均使用 ID 生成器产生稳定 ID；每行以列 ID 为键保存单元格。删除列同时移除该列的全部 cells；最少保留 1 行、1 列，最多 50 行、8 列。手机端表格区域可横向滚动，整个表格作为一个 Draft 串行保存，避免标题或旧单元格写入覆盖最新结构。

`AndroidAttachmentFileStoreTest` 在 Robolectric 的应用私有目录中使用临时 JPEG/PNG 验证导入、缩放、EXIF 旋转、实际内容头校验、路径边界、删除和孤儿清理。透明 PNG 的断言验证其输出仍为 PNG 且可解码；具体像素 alpha 的最终行为仍由设备图形栈负责。

FileStore 的失败测试通过仅供数据层使用的 `AttachmentFileStoreTestDependencies` 注入输出流、编码、原子移动、删除和可控时钟。正式导入使用临时文件、文件长度校验与原子移动；任一写入、编码或移动失败都会删除临时文件并返回不含绝对路径的受控错误。相对路径必须以 `images/` 开头，拒绝绝对路径、反斜杠、空白路径和目录穿越；孤儿清理只扫描受控图片目录，保留被引用文件与未过期临时文件，重复执行保持幂等。

图片说明与 TABLE 都以“日期 + blockId”为键维护整体版本化 Draft。保存成功前，Draft 优先于 Repository Flow 的持久化基线；保存失败保留最新 Draft，重试只提交最新版本。排序、删除、日期切换、ON_STOP 和页面销毁前都会 flush；删除块会先取消其等待任务，避免旧写入重新出现已删除的内容。

### ADR-007：密钥和日志安全

`SecretStore` 使用 Android Keystore 中别名为 `worklog_ai_api_key_v1` 的 AES/GCM 密钥保护 API Key。普通 DataStore 仅保存 Base64 密文与独立 IV；密钥材料不进入 Room、BuildConfig、Git、普通备份或日志。保存、读取、覆盖、删除均通过 `SecretStore`，设置页不回显已保存的明文。OkHttp 不启用正文级日志；错误映射不携带 Authorization Header、完整请求/响应、日志正文、URI 或绝对路径。

### ADR-008：AI 解耦与手动总结

UI 只依赖 `GenerateWorkSummaryUseCase` 和 Summary ViewModel。`AiSummaryProvider` 负责供应商协议；`MockAiSummaryProvider` 用于离线演示和测试；`OpenAiCompatibleSummaryProvider` 仅发起非流式 `POST /v1/chat/completions`，默认仅允许 HTTPS URL。`WorkSummarySkill` 负责 `work-summary-skill-v1` System Prompt、输入构建、30,000 字符长度控制、JSON 解析、一次 JSON 修复重试与 Markdown 格式化。Provider、Skill、Room 和 UI 互不直接耦合。

MVP 只发送时间范围内 `allowAiProcessing = true` 的日志文字、图片说明和表格标题/列名/单元格；不发送图片二进制、文件路径、URI、UUID 或密钥。输入按日期和块顺序规范化，空内容与完全重复内容会被移除。模型输出的 `sourceDates` 只保留实际输入日志日期，非法 JSON、空响应、超时、限流和服务端错误均映射为去敏的用户文案。

手动生成以 `(summaryType, periodStart, periodEnd)` 锁串行化，先保存 `PENDING` / `GENERATING` 状态，再保存 `SUCCESS`。`originalContent` 保存规范化 AI JSON，`editedContent` 保存用户可编辑 Markdown；失败只更新状态和受控错误，保留上一版成功内容。`sourceHash` 仅基于实际纳入 Skill 的日志计算，用于提示总结已过期。取消生成会恢复原有总结或留下 `PENDING`，不会清空本地日志。

### ADR-009：自动总结幂等、约束和隐私

WorkManager 只做每日周期性“检查”，不承诺精确分钟。检查任务使用稳定唯一名称；每个生成任务按 `summaryType + periodStart + periodEnd` 使用稳定唯一 work 名称，并由 WorkSummary 的同周期唯一约束和 `SummaryGenerationMode.AUTOMATIC` 共同保证幂等。自动模式绝不覆盖 `SUCCESS`、`GENERATING` 或 `FAILED` 的既有周期；手动模式仍保留显式重新生成语义。

检查只处理已结束的自然周（周一至周日）和自然月。DataStore 记录最近评估的周期边界与调度版本：周最多补 4 个、月最多补 2 个，单次检查最多安排 3 个，按由旧到新顺序安排。应用启动、设置变化和手动“立即检查”都会复用同一个唯一检查入口；禁用对应类型会取消该类型生成并重置其补漏基线。

真实 Provider 的生成 Worker 使用 `CONNECTED` 或（用户未允许移动网络时）`UNMETERED` 网络约束，并同时要求电量非低和存储非低；Mock Provider 不要求网络。网络、超时、限流和服务端错误使用 30 分钟指数退避、最多 3 次，永久输入/配置/解析错误直接结束。Worker 输入只有类型、起止日期和 `AUTO` 来源，绝不含 API Key、Authorization、日志正文、图片路径、图片说明或表格 JSON。

完成通知使用私有通知通道与稳定哈希 ID，只显示周报/月报的周期与成功/失败状态，并深链到对应总结页。通知、日志和异常文案不含工作内容、文件路径、模型原始响应或密钥。通知权限和自动发送范围由设置页明确控制。

Worker 通过应用级 `WorkLogWorkerFactory` 获取 Hilt 注入依赖；它不依赖 ViewModel。当前构建环境无法获取 `androidx.hilt:hilt-work`，因此采用这个受控的应用级 factory；职责保持与官方 Hilt Work 集成等价，未来可在依赖可用时平滑替换。

### ADR-010：恢复必须原子化

备份协议固定 `formatName = worklog-ai-backup`，当前 `formatVersion = 2`，与 Room database version 独立。v2 新增 `data/todo_items.json`、`todoCount` 与集中式 `maxTodos`；v1 仍可读取并规范化为 `todos = emptyList()`。ZIP 只允许 Manifest、协议声明的固定 data JSON 和 Manifest 声明的 `attachments/images/` 文件。Entry 名先按 `/` 规范化并拒绝绝对路径、盘符、反斜杠、控制字符、`..`、超长路径和白名单外路径；规范化后按大小写不敏感比较，任何重复 Entry 都拒绝。Manifest 中每个文件的实际流 size 与小写 SHA-256 都必须匹配，未声明或缺失的核心文件均拒绝。

Todo DTO 预检校验唯一 ID、日期、长度、枚举、非负 sortOrder、DONE/completedAt 一致性以及可选内容块链接。未知链接在修改正式数据前拒绝；恢复插入顺序为 WorkEntry、ContentBlock、Attachment、WorkSummary、Todo，回滚快照也包含 Todo。

`BackupSafetyLimits` 集中定义归档大小、Entry 数、单 Entry/总解压大小、压缩比、JSON/附件大小、路径长度和各 DTO 记录数。Reader 即使面对未知 Header size 也按实际读取字节计数，并在越界时立即停止；目录 Entry 计入总 Entry 数。完整 payload 在正式修改前校验日期与业务唯一键、块引用与顺序、TABLE 1×1～50×8、附件只关联 IMAGE、安全相对路径、图片头/MIME，以及自然周/自然月 Summary 周期。

恢复顺序是：读取旧 Room 与非敏感设置快照，解压并校验 staging，将正式附件目录原子改名为 old，再将 staging 切为正式目录，随后在单个 Room `withTransaction` 中删除旧数据并按 WorkEntry、ContentBlock、Attachment、WorkSummary 顺序插入，最后以单次设置提交恢复非敏感配置。真实 Room 测试通过事务内检查点验证每个阶段抛错或取消时旧四张表与外键关系完整。数据库或设置失败后恢复旧 Room、旧设置和 old 附件；只有三者都成功才返回普通已回滚失败，否则返回 `RecoveryRequired`，保留可恢复目录与 journal，绝不声称原状态完整。

restore journal 只记录随机 restoreId、阶段、内部 staging/old 目录名、数据库/设置是否替换和 Scheduler 待协调标记，不含正文、图片说明、API Key、Provider 密文、SAF URI 或绝对路径。应用启动时先运行保守修复：PREPARED 清理 staging，ATTACHMENTS_SWITCHED 恢复 old；数据库状态无法证明或 journal 损坏时保留数据并报告需要修复，不盲目删除 old。SETTINGS_REPLACED/COMPLETED 会幂等协调 Scheduler 并清理 journal。Scheduler 属于可重建派生状态，失败不回滚已成功恢复的用户数据，而返回 warning 并在下次启动重试；取消保持 `CancellationException`，仅在已切换正式状态时用最小 `NonCancellable` 区间补偿。

恢复成功事件不会复用恢复前的 Today 导航目的地。导航会以 `inclusive = true` 弹出旧 Today 再创建安全根页面，使旧 ViewModel、旧 Repository Flow 和旧 Draft 生命周期结束；否则数据库替换期间的旧观察流可能把一次瞬时失败保留在首页。`RestoreNavigationTest` 固定验证该导航选项。

应用级 WorkerFactory 以 `Provider` 延迟解析包含 WorkManager 的调度依赖，避免 Application 字段注入期间递归初始化 WorkManager。未建立初始 Git 提交时，最终空白检查使用 `git add -N .` 让 `git diff --check` 覆盖全部未跟踪源码，再用 `git reset` 清除 intent-to-add；此流程不创建提交或推送。

### ADR-011：Release 构建与签名边界

内部试用版固定为 versionName 0.1.0、versionCode 1。Release `debuggable=false`，启用 R8 优化和资源压缩，并继续使用 `usesCleartextTraffic=false`、`allowBackup=false` 与完全排除系统备份/设备迁移的 data extraction rules。Debug-only Activity、受控 HTTP 服务、恢复暂停点、批量数据生成器和 Android Test 只存在于 `debug`、`test`、`androidTest` 或 `tools`，Release 源集不引用这些入口。

Release Keystore 位于仓库之外。Gradle 只从当前进程的 `WORKLOG_RELEASE_STORE_FILE`、`WORKLOG_RELEASE_STORE_PASSWORD`、`WORKLOG_RELEASE_KEY_ALIAS` 和 `WORKLOG_RELEASE_KEY_PASSWORD` 读取签名配置；缺失变量或无效文件会在 Release 打包前以不含口令/路径的受控错误停止，Debug 构建则完全不依赖这些变量。校验任务只把缺失变量名称和非敏感文件路径建模为任务输入，口令不进入任务输入或 Configuration Cache。签名值不写入 Gradle 文件、BuildConfig、资源、报告或 Git。`tools/release-build.ps1` 只在交互式终端把 SecureString 临时转换给子进程，并在 finally 中清除环境变量和 BSTR。

APK 使用 v2/v3 签名，AAB 使用 JAR 签名。最终提交后必须重新 clean 构建、用官方 `apksigner`/`jarsigner` 验证，并通过 `tools/package-release.ps1` 把 APK、AAB、SHA256SUMS、发布报告和 R8 mapping 复制到 Git 忽略的 `release-artifacts/<version>/`。发布产物、mapping、Keystore 和密码永不进入版本控制；本地 Tag 不自动 push。

### ADR-012：每日待办边界与工作记录联动

Room v2 新增 `todo_items`，原有四张业务表不重建。Todo 使用独立领域模型、Repository 与 UseCase：四级优先级和四种状态均为枚举，`sortOrder` 只表达同日期未完成项的稳定手动顺序，批量重排与日期迁移在单事务中规范化为连续非负序号。`linkedContentBlockId` 是可空外键，删除内容块时使用 `SET NULL` 保留 Todo。

“完成并记录”在一个 Room 事务内取得或创建对应日期 WorkEntry、追加 TEXT ContentBlock 并绑定 Todo；已绑定项拒绝重复同步。重新同步会明确创建新的工作记录，不静默改写旧事实。文字块转 Todo 保留原工作记录，未来 Todo 可以规划但不能提前写入未来日志。Todo 内容本身不进入 AI、WorkManager Data、通知、Provider 测试或生产日志；只有用户主动生成的工作记录块才沿用工作日志的 AI 授权边界。

备份协议 v2 新增 `data/todo_items.json`、`todoCount` 与 `maxTodos`，恢复前校验 Todo ID、日期、长度、枚举、排序、完成时间和 TEXT 块链接。Reader 继续接受 v1 并规范化为空 Todo 列表。Room Migration 1→2 只创建 Todo 表、索引和外键，Schema 1 永久保留。

界面使用统一的 Material 3 Design Token。Today 页按日期概览、紧凑待办、快速记录和工作内容组织；快速记录工具栏属于 LazyColumn 内容，不覆盖表格结构按钮。拖动只维护短期 UI 顺序，结束时一次性持久化；TalkBack 使用上移、下移和优先级调整动作代替手势。Dynamic Color 在 Android 12+ 可用，回退色、深色、1.5× 字体和横屏共享相同组件语义。

## 6. 错误模型

数据、文件、网络、AI 和导入导出错误在基础设施边界映射为封闭的领域错误；UI 只显示自然、可操作的中文文案。异常堆栈仅供本地调试，并且日志参数不得含工作日志正文、密钥、Authorization Header 或完整 AI 原始响应。

AI 原始返回可在 `WorkSummary` 中为用户本地排查保留，但不得写入系统日志、分析服务或崩溃上报。解析器必须处理代码围栏、额外文本、缺字段、空数组和非法 JSON。

## 7. 并发与性能

- DAO、文件与网络操作全部在主线程之外执行。
- Repository 对外返回 `Flow`，界面按生命周期收集。
- 输入高频事件先更新局部状态，debounce 后批量写入。
- 历史列表查询只投影列表所需字段，使用 `LazyColumn` 和分页/限量策略。
- 表格单元格按稳定 key 和局部状态更新，避免整页重组。
- AI 请求是可取消的挂起操作，不阻塞 UI；超时和重试次数有上限。

## 8. 测试策略

- 纯 Kotlin 单元测试：日期范围、codec、输入构建、hash、解析、debounce、Repository 和幂等规则。
- Robolectric JVM Room 测试：内存数据库上的外键、级联、范围查询、周期唯一约束与 Repository 事务；无需设备。
- Room 仪器测试：后续 Migration、跨版本升级和设备 SQL 行为。
- Compose UI Test：启动、块编辑、自动保存、表格、图片状态、历史、Mock AI 成功和失败。
- Worker 测试：约束、重试、周期检查和重复执行。
- 每个阶段至少运行构建、单元测试、相关仪器测试编译、Android Lint、Detekt 和 ktlint；无法运行设备测试时必须明确区分“已编译”和“已执行”。
- 阶段 9 使用 Android 16 / API 36 的 HONOR 真机执行 41 个 Instrumentation 方法和人工 SAF、Keystore、通知、Worker、生命周期及性能矩阵。Android 10～13 第二设备/模拟器按用户明确要求未执行，作为内部试用风险记录，绝不写成通过。
- 阶段 10 使用 `--rerun-tasks --no-build-cache` 执行 246 个 JVM/Robolectric 测试和 51 个 Android 16 真机 Instrumentation 方法；新增覆盖 Migration、Todo Repository/UseCase/ViewModel、Backup v1/v2、Markdown、50 条 Todo、无障碍排序及设备级 v2 恢复。最终 XML 的 failures/errors/skipped 均为 0。

## 阶段 5：历史日志架构

历史功能以 `WorkHistoryRepository` 作为只读边界：编辑器继续通过 `WorkEntryRepository` 读取完整聚合，历史列表只接收 `WorkEntrySummary`，因此 UI 不接触 Room Entity、表格 JSON、附件路径或大图。Room 的 `@Transaction` 关系分页查询一次取得一页 Entry 及其块/附件关系，避免逐卡片查询造成的 N+1。

统一的可见性规则会过滤软删除、空 Entry 与仅空白文字；IMAGE 与 TABLE 本身都视为有效记录。摘要优先选择排序后的首条非空文字，并压缩空白、按代码点安全截断；无文字时使用图片、表格标题或 Entry 标题作为稳定后备文案。损坏 TABLE JSON 在历史映射与搜索时局部隔离，列表降级为表格记录而非整体失败。

`WorkPeriodCalculator` 使用包含首尾日期的闭区间，自然周固定为周一至周日，月份由 `YearMonth` 计算。最近与搜索以 30 条分页；范围视图受日、周、月限制。搜索以 300 ms 防抖和请求版本号取消旧请求，在 Repository 的 IO dispatcher 中解析 TABLE 内容，匹配标题、TEXT、图片说明、表格标题、列名和单元格，从不匹配 UUID、MIME 或路径。个人本地数据规模之外的 FTS 留待性能需求明确后实现。

历史编辑路由仅传递 ISO-8601 日期。固定日期的 `TodayViewModel` 不响应跨午夜的 `DateChanged`，今天入口保留原有自动跟随行为。Draft 按日期和块 ID 隔离；生命周期停止、页面离开和返回历史列表前都会 flush，历史页面恢复时刷新摘要。
