# WorkLog AI 0.3.0 UI 深度重构报告

版本：`0.3.1`（versionCode 4）
基线：`v0.3.0-internal`
设备：HONOR PPG-AN00，Android 16 / API 36（不记录设备序列号）

## 视觉方向

本轮采用“现代效率工具”方向：低饱和青蓝作为关键动作与选中状态，柔和靛蓝作为辅助语义，暖灰白与深灰蓝作为浅色/深色背景。页面通过排版、留白和内容层级组织信息，不依赖连续高阴影卡片。

- 品牌色：低饱和青蓝，Dynamic Color 开启时继续保留错误、优先级和状态的文本/图标语义。
- 背景：浅色为暖中性灰白，深色为深灰蓝，不使用纯黑。
- 字体：统一 Material 3 display/headline/title/body/label 层级，减少页面硬编码字号。
- 间距：4、8、12、16、20、24、32dp。
- 圆角：8、12、16、20dp，Bottom Sheet 顶部 28dp。
- 阴影：普通内容使用 tonal surface；只在拖动和浮层阶段提高 elevation。
- 动效：保留 150–300ms 的状态、排序和浮层反馈，不增加无限或装饰性动画。
- 图标：继续使用项目内 Material 图标和本地 Vector，不引入网络或来源不明资产。

## 应用框架

- 保留 edge-to-edge，并统一页面背景、子页面 TopAppBar、滚动后的 tonal 顶栏和系统栏衔接。
- 四个顶级页面由内容区 Page Header 承担标题，避免 App Shell 与页面重复显示标题；工作记录、周期总结和数据管理等子页面继续使用统一返回顶栏。
- 普通手机竖屏使用 NavigationBar；可用宽度达到 720dp 时切换为 NavigationRail。
- 顶级入口仍为“今天、历史、总结、设置”，路由和返回栈语义未变化。
- Empty、Loading、Error、Section Header、Status Chip、Action Row 和内容 Surface 使用统一组件。

## 页面重构

### Today 与 Todo

- 日期 Header 集中展示日期、待办完成数、进行中数量、工作记录数和保存状态。
- Todo 外层不再包大卡片；每项改为低层级 Surface，并用 4dp 侧边 Accent 表示优先级。
- 未开始、进行中、已完成、已取消分别使用明确图标与文本语义，优先级不只靠颜色。
- 拖动仅提高当前项的 tonal/shadow elevation；持久化与排序业务逻辑保持不变。
- 快速记录入口保持文字、图片、表格三项紧凑工具条，工作记录继续使用 LazyColumn 内容流。

### Work Editor

- TEXT、IMAGE、TABLE 统一使用轻量内容 Surface，减少默认 Card 和重边框。
- 图片使用统一圆角，缺失文件状态和大图入口保持清晰。
- TABLE 显示行列摘要和横向滚动提示，50×8 限制及编辑语义保持不变。
- 保存状态仍是页面级反馈，不为每个块重复增加全局状态。

### History

- 新增“工作回顾”内容 Header，搜索、周期筛选与记录列表形成一致层级。
- 日期成为列表视觉锚点；条目使用轻量时间线 Accent，而非连续高权重卡片。
- 内容摘要、内容块统计和待办完成度保持次级信息，分页和搜索数据流未变化。

### Summary

- 顶部集中周报/月报切换、周期和 AI 数据说明。
- 成功态正文进入独立阅读 Surface；stale/truncated 使用小型状态标签。
- 生成、重试、编辑保留为主要操作；复制和恢复 AI 原文进入 Overflow。
- Empty、Generating、Failed 和保留旧结果的语义未变化。

### Settings

- 页面按外观、AI 服务、自动总结、数据管理、隐私与安全、关于连续分组。
- 设置项统一标题、摘要、尾部控件和轻量分隔，避免每项独立大卡片。
- API Key 仍只显示配置状态，不回显完整密钥；危险操作不使用大面积红色背景。

### Data Management

- 导出、完整备份、恢复改为统一 Action Row。
- 顶部集中展示“备份未加密”和“API Key 不包含在备份中”。
- Preview 使用日志、待办、附件和总结的结构化摘要；RecoveryRequired 不暴露路径或内部异常。

## 响应式、主题与无障碍

- 浅色与深色分别调校 Surface、outline、container 和正文灰阶。
- Dynamic Color 继续由 Android 12+ 系统色驱动；错误、优先级、状态仍有图标和文本兜底。
- 1.3×/1.5× 字体下，长内容通过滚动容器和最多两行摘要保持主要操作可达。
- 横屏/宽屏切换 NavigationRail，避免把底部导航简单拉伸。
- Todo 保留“上移、下移、提高优先级、降低优先级”自定义无障碍动作。
- 标题提供 heading 语义，进度提供 stateDescription，主要触控区域保持至少 48dp。

## 性能

- Todo、工作记录和 History 继续使用 LazyColumn。
- 拖动期间不写 Room，结束后才提交排序。
- UI 重组不访问 DAO，不新增主线程文件 I/O，不改变图片解码数据流。
- 50 条 Todo、50×8 TABLE、1000 条历史的既有测试入口和数据策略保持不变。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| JVM/Robolectric | 254/254，42 suites，failures/errors/skipped = 0 |
| Android Test 编译 | 通过 |
| assembleDebug | 通过 |
| lintDebug | 通过 |
| Detekt | 通过 |
| ktlintCheck | 通过 |
| 强制执行 | `--rerun-tasks --no-build-cache` |
| Android Instrumentation | 60/60，failures/errors/skipped = 0 |
| 真机稳定性日志 | FATAL/ANR/OOM = 0 |

## 真机视觉验收

- 设备：HONOR PPG-AN00，Android 16 / API 36；不记录序列号。
- Today：移除顶层重复标题后，日期、待办进度、快速记录和工作记录在首屏形成单一阅读顺序。
- History：搜索、最近/日/周/月筛选与空状态层级清晰，底部导航不遮挡内容。
- Summary：周/月切换、周期、来源说明与主要生成操作层级明确，成功态次要操作位于 Overflow。
- Settings：外观、AI 服务和后续分组采用连续设置行，避免卡片墙。
- 当前设备的 Dynamic Color 呈低饱和蓝紫色；品牌动作、错误、优先级与状态仍由文本和图标提供非颜色语义。
- 1.3×、1.5×、横屏、NavigationRail、TalkBack 排序和 50 条 Todo 场景由 Instrumentation 覆盖。
- 截图使用空白或受控模拟数据，仅保存在 Git 忽略的 `app/build/reports/ui-validation/`，不提交仓库。

## 数据与安全

- Room version 保持 2，Schema 1/2 未修改，Migration 未修改。
- backupFormatVersion 保持 2。
- 不新增权限，不改变 Todo、WorkEntry、Summary 或备份业务语义。
- 不提交 APK、AAB、Keystore、密码、API Key、数据库、图片或测试备份。
- `v0.1.0-internal`、`v0.2.0-internal` 不移动、不覆盖。

## 已知限制

- 本轮只在 HONOR Android 16 / API 36 执行真机验收；Android 10–13 专项兼容测试仍按用户既有豁免不执行。
- Dynamic Color 的最终观感受设备壁纸色板影响。
- 其他 OEM 的 SAF Provider 和长期后台限制仍需内部试用观察。
- 应用图标与 Splash 已复核并保留本地 Vector 方案；本轮重点放在高频页面和应用框架，没有引入新品牌图片资产。
- Compose 的 `LocalClipboardManager` 仍有一项上游弃用警告，不影响当前复制功能或本轮验收。

## 第二轮 UI/UX 精修（0.3.1）

第二轮在既有 0.3.0 设计系统上做定向收敛，不建立新的视觉体系，也不改变 Todo、WorkEntry、Summary、Room 或备份协议的业务语义。

- Today：日期成为单一视觉锚点，状态文案改为“今天还有 X 项待办/今天的待办已完成”，概览压缩为一行完成数、进行中数和工作记录数。
- Todo：减小未完成项的纵向留白，已完成和已取消项采用更紧凑的排版；非活跃项不再持续展示备注；拖动时才显示强调边框。
- 快速添加：支持键盘 IME Done；有效输入时才执行添加，不使用成功 Snackbar 中断连续录入。
- 已同步状态：从次要按钮感过强的“已同步记录”改为 48dp 可触达的轻量“查看记录”，包含唯一 TalkBack 描述和明确的打开中状态。
- Work Editor：关联记录只滚动和高亮一次；高亮使用短时 tonal 边框，不闪烁、不阻止编辑。
- History：时间线 Accent、内容背景和内边距进一步弱化，日期仍为锚点，摘要与统计保持次级。
- Summary：生成状态使用固定尺寸局部进度；失败状态不再覆盖已有成功正文；隐私和计数信息使用统一弱化色。
- Settings：继续复用统一 Action Row 和 Section 节奏，没有增加逐项卡片。
- Data Management：运行状态、警告和错误统一为紧凑状态组件，长任务保持原页面结构，避免阶段切换时大幅跳动。
- Dialog/Bottom Sheet：Todo 编辑、完成、迁移和删除入口改为互斥呈现，避免同一帧叠加多个浮层。

## 交互反馈规范

- 可点击入口保留 Material pressed/ripple 状态，禁用时降低强调度。
- 导航和长任务在开始前同步锁定入口，完成、失败或返回后明确释放；不以任意固定延迟作为唯一防重手段。
- 自动保存、排序和折叠不弹成功 Snackbar；复制、导出、恢复、删除撤销和同步成功继续使用可控反馈。
- AI、Backup、Restore 的既有 busy 状态在协程启动前设置，避免同一帧连续点击创建多个任务。

## “已同步记录”重复导航修复

### 根因

旧实现直接从 Todo 行的链接入口发起导航，没有入口级 in-flight 状态；路由也没有对同一日期和同一 ContentBlock 做目标幂等判断。快速点击会在首个目标进入返回栈前连续调用 `navigate()`，从而压入多个相同页面。一次性定位信息若由可重放状态承载，还可能在重组或进程重建后再次被消费。

### 修复

- `TodayTodoViewModel` 在发送事件前同步设置 `navigatingLinkedTodoId`，同一导航尚未结束时拒绝后续入口。
- 导航事件使用无 replay 的 `Channel`；事件包含日期和 `linkedContentBlockId`，不包含 URI、路径或工作正文。
- `WorkLogApp` 使用 `launchSingleTop`，并在导航前比较当前 route、日期和 linked block；相同目标直接忽略。
- `TodayViewModel` 从 `SavedStateHandle` 一次读取并移除 linked block 参数，防止重组、旋转或进程恢复自动重复定位。
- 目标 ContentBlock 存在时只滚动和高亮一次；不存在时显示受控提示。
- 返回 Today 或导航失败后释放入口锁，用户可以再次正常打开。

### 自动化覆盖

- ViewModel 快速请求只产生一次导航事件，返回后可再次进入。
- 路由生成、`launchSingleTop` 和相同目标判断。
- linked block 存在、缺失和参数一次性消费。
- Compose 入口禁用、唯一点击语义。
- Android 真机集成测试连续激活 10 次，只进入一个目标；返回一次回到 Today；Activity 重建不自动重复导航。

真机专项和全量 AndroidJUnitRunner 均已执行：连续激活 10 次只进入一个目标页面，一次返回回到 Today，Activity 重建没有自动重复导航；全量 60/60，failures/errors/skipped = 0。

PR #2 已于 2026-08-02 由 Draft 转为 Ready，并以普通 Merge Commit `87f194d38b3f07fa389eb9d00348f8ffd17010df` 合入 `main`。合并过程未 force push，未改动 Room Schema、Migration 或 Backup 协议。
