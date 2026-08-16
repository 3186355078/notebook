# WorkLog AI 界面统一与降噪精修

## 范围与原则

本轮为纯视觉与交互呈现层调整：统一设计语言、降低视觉噪音、收拢重复表达。不改变任何功能、数据行为、导航结构、业务文案语义与配色值。版本号尚未递增，定版后可将本目录重命名为对应版本目录。

硬约束（全部保持）：

- `Color.kt` 色值不变，`WorkLogThemeTest` 锁定的 surface/secondary 断言原样通过；
- Today 宽屏内容宽度维持 (640, 840] 区间；
- 全部 testTag、contentDescription 与测试锁定文案保留（个别断言随交互降噪同步更新，见下）；
- 触控目标 ≥48dp；优先级与状态保持文字 + 图标双通道表达；
- 0.3.0/0.4.1 已定方向不回退：无卡片墙、顶级页无 TopAppBar、tonal surface、克制 elevation。

## 设计系统层（core/designsystem）

- 新增 `WorkLogOutlinedCard`：`surfaceContainerLowest + 1dp outlineVariant(0.5) 描边 + shapes.medium`，收编待办项与历史条目两处重复的手写卡片样式；
- 新增 `workLogFilledInputColors()`：合并历史搜索框与待办快速添加各自重复定义的填充式输入配色。输入语言规范固化为「表单类 = Outlined，快速输入 / 搜索 = 填充式」；
- 新增 `WorkLogIndicatorSize`（inline 16dp / compact 22dp / standard 30dp），替换全 app 五种转圈尺寸；
- `WorkLogEmptyState` 新增 `compact` 紧凑变体（`titleMedium` 标题、缩小图标与留白），用于区块级空态，与全屏空态区分。

## 今天页（editor + todo）

- 快速记录区：「快速记录」+「今日工作记录」背靠背双标题合并为单一区块头，描述合并为一句；三个添加按钮的 contentDescription 与行为不变；
- 三种内容块头部统一：文字块补「文字」SectionHeader（与图片/表格一致），移除仅由 Spacer 占位的头部行；
- 图片块：缺图回退从裸 Row 改为 tonal 容器居中样式；图片圆角从误用的 spacing token 改为 `MaterialTheme.shapes`；溢出菜单补齐前缀图标；
- 表格块（交互降噪重点）：
  - 单元格固定 120dp 列宽、placeholder 提示列名，替代浮动 label 堆叠；新增 `table_column_name_*` testTag；
  - 每列「删除列」文字按钮改为列名下方居中的小图标按钮，每行「删除行」文字按钮改为行尾小图标按钮，按钮数量不再随行列数膨胀；
  - 表头行加 `surfaceContainerHigh` tonal 底色，形成真正的表格视觉；表头与正文列严格对齐；
  - 「添加一行/一列」按钮降低内边距高度。
- 不支持块卡片：Material3 `Card` 统一为 `WorkLogContentSurface`；
- 待办日期步进器：ISO 日期串改为「M月d日 EEEE」中文格式，今天额外显示「今天 ·」前缀，非今天日期提供「回到今天」快捷入口；
- 待办行降噪：优先级 chip 文案由「紧急优先级/高优先级」简化为「紧急/高」（侧色条与配色保留）；溢出菜单按「编辑 / 状态 / 优先级 / 操作」用分隔线分为四组，状态项补齐状态图标；
- 「显示已完成和已取消」按钮补展开/收起图标；待办空态复用 `WorkLogEmptyState(compact)`，加载转圈统一 22dp；
- 隐私说明文案（「待办仅保存在本地……」）作为固定表述保持不变。

## 历史页

- 模式切换 TabRow → SegmentedButton，与总结页统一（四个文案不变）；
- 「选择日期」从旧版 `android.app.DatePickerDialog`（View 体系）改为 Material3 DatePicker，与补录日期选择器同款；周期导航按钮权重与总结页统一（TextButton）；
- 条目卡统一为 `WorkLogOutlinedCard`，左侧色条从固定 68dp 改为 `IntrinsicSize.Min` 自适应内容高度；条目间距 8dp → 12dp；
- 「加载更多」内的转圈约束为 16dp，不再撑高按钮；搜索框接入共享输入配色。

## 总结页

- Markdown 正文（阅读态与编辑预览态）包入 `WorkLogContentSurface` 容器，修复正文裸贴背景与整页卡片语言的断裂；
- 加载态复用 `WorkLogLoadingState` 居中展示；
- 「暂无可总结的工作记录」分支由无样式纯文本改为紧凑空态组件；
- 「前往设置」并入操作按钮行，不再游离在按钮行之外。

## 设置页

- 「外观」区不可点击的假 ActionRow（含「自动」chip 与前进箭头位）改为纯信息行；
- 两种开关行实现合一（ProviderModeControl 复用 SettingSwitchRow），分区末尾不再悬挂分隔线；
- Toast 反馈改为 Snackbar（屏内 SnackbarHost），与其他页面反馈形式一致；
- AI 配置区以「服务配置」「安全」两个小节标签分组（超时时间归入服务配置，API Key 归入安全）；
- 「保存设置」为主按钮、「测试连接」降为次级 OutlinedButton；按钮内转圈统一 16dp；
- 「下次检查由系统调度，时间不保证精确」并入自动总结分区描述，减少常驻小字行数；区块间距 20dp → 16dp 与其他页对齐。

## 数据管理页

- 三个 Markdown 导出入口使用不同图标（当日日志 Article / 周报 Description / 月报 CalendarMonth）；
- 导出日期从手敲 yyyy-MM-dd 改为只读字段 + Material3 日历选择（仍通过 ExportDateChanged 写入同一状态）；
- 错误/警告从孤立 chip 改为统一的「图标 + 文字」状态行（error / 中性两档），「操作已取消」同样收纳；
- 恢复确认弹窗：删除重复的连字符长句，统计信息以五个 chip 分两行完整呈现；弹窗间距 token 化。

## 随同更新的测试断言

- `TodayTodoSectionTest`：优先级 badge 断言「紧急优先级/高优先级」→「紧急/高」；
- `VisualAcceptanceCaptureTest`：快速记录截图滚动锚点「快速记录」→「今日工作记录」；
- `TodayScreenContentTest`：表格列名断言由 label 文本「列名」改为 `table_column_name_column` testTag（label 已改为 placeholder，仅在列名为空时显示）；
- `SettingsContentTest`：外观行摘要大字体换行断言同步新文案。

## 验证

- `:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`、`:app:lintDebug`、`:app:detekt`、`:app:ktlintCheck` 全部通过；
- Android 16 真机（HONOR PPG-AN00）执行受影响的仪器测试：Today / Todo / History / Summary / Settings / DataManagement 内容测试与 WorkLogThemeTest 共 40/40 通过；
- 视觉截图矩阵（`VisualAcceptanceCaptureTest`）与导航视觉测试、WorkLogAppTest 通过，37 张截图已重新生成至 Git 忽略的 `app/build/ui-audit/`；
- 完整 connectedDebugAndroidTest 首轮出现 4 个与本次改动无关的环境性失败：3 个 `DeviceControlledHttpWorkerIntegrationTest` 用例因受控 AI 服务器未启动（127.0.0.1:18080 连接拒绝），1 个横屏截图用例因重复运行产生 MediaStore 同名文件冲突。启动 `tools/controlled-ai-server/server.py` 并 `adb reverse`、清理设备上旧截图目录后，该 5 个用例单独复跑全部通过；
- 视觉验收截图重复运行前需清理设备 `Pictures/WorkLogAI-UI-Review-0.3.0/` 目录，属既有测试基建限制，与本次变更无关。
