# WorkLog AI 0.3.0 UI 审计

审计日期：2026-07-26
审计基线：`v0.2.0-internal` / `b6cf237aaf4aa6d4303abec907867eead93e5157`

## 审计方法

本次审计结合现有 Compose 源码、Android 16 HONOR 真机视觉基线和既有 1.0×/1.3×/1.5×、横竖屏、深浅色测试结果。审计覆盖空数据、少量数据、50 条 Todo、50×8 TABLE、键盘、Dynamic Color 和 TalkBack 语义。业务数据、Room 和备份协议不在本轮调整范围。

## 全局问题

| 页面/区域 | 当前问题 | 影响 | 改进方向 | 涉及功能逻辑 | 验收方法 |
| --- | --- | --- | --- | --- | --- |
| Design System | 颜色仅定义少量角色，Typography 使用 Material 默认值，缺少 20dp、状态色、Elevation、组件默认值 | 页面只能各自补样式，品牌感和灰阶层级不足 | 完整定义浅/深色语义色、字体层级、4–32dp 间距、8–28dp 圆角和轻量动效 | 否 | 主题单测、浅/深/Dynamic 截图 |
| App Shell | 顶部栏、背景和底部导航均使用 Material 默认外观；横屏仍是拉伸后的底栏 | 页面与导航割裂，宽屏空间浪费 | 统一 Surface、TopAppBar、Snackbar；手机使用 NavigationBar，宽屏使用 NavigationRail | 否 | 竖屏/横屏 Compose 测试与真机 |
| 页面容器 | WorkLogSection、Todo、Editor、History 同时大量使用 Card/Surface | 嵌套容器过多，内容像独立模块拼接 | 页面级内容流、低层级 tonal surface、分隔和留白代替大卡片 | 否 | 逐页人工验收 |
| 状态组件 | Empty、Loading、Error 分散实现，图标、文案和操作密度不一致 | 状态切换缺乏统一语言 | 建立 WorkLogEmptyState、LoadingState、ErrorState、StatusChip 和 ActionRow | 否 | 各状态 Compose 测试 |
| 响应式 | 没有 Window/宽度判断；横屏仅放大可用宽度 | 横屏信息密度失衡，导航占据底部高度 | 使用宽度阈值切换 NavigationRail；内容限制最大阅读宽度 | 否 | 横屏和大字体测试 |
| 无障碍 | Todo 已有自定义排序动作，但页面标题、进度和部分图标语义不统一 | TalkBack 顺序和状态理解不稳定 | 统一 heading、stateDescription、进度和 48dp 触控目标 | 否 | Semantics 测试与 TalkBack 人工验收 |

## 页面审计

| 页面 | 当前问题 | 影响 | 改进方向 | 涉及功能逻辑 | 验收方法 |
| --- | --- | --- | --- | --- | --- |
| Today | 日期仅为普通标题；Todo、快速记录和内容块视觉权重接近；首屏缺少一眼可读的当日概览 | 高频主线不突出，进入页面后需要扫描多个区域 | 建立紧凑日期 Header、完成率/进行中/工作记录一行概览、今日重点和内容流 | 否 | 空/少量/50 Todo，浅深色与 1.5× |
| Todo | 外层 Card 内再放 Card；每项控件多，优先级圆点和同步 Chip 抢占标题空间 | 列表显重、信息密度低，50 条时滚动负担大 | 左侧局部优先级 Accent、扁平列表、明确四状态图标、小型标签、拖动 tonal 提升 | 否 | 四状态/四优先级/拖动/TalkBack |
| Work Editor | TEXT/IMAGE/TABLE 都是默认 Card + OutlinedTextField；块标题和更多操作不统一 | 更像表单而非笔记内容流 | 统一 WorkLogContentSurface 和块标题栏，降低边框，突出正文和保存状态 | 否 | TEXT/IMAGE/TABLE、键盘和 50×8 |
| History | 搜索、Tab、周期控件上下堆叠；每条记录是等权 Card | 时间轴感弱，日期不是视觉锚点 | 搜索和筛选整合，日期列/时间线 Accent，记录项使用轻量 Surface | 否 | 最近/日周月/搜索/分页/空结果 |
| Fixed Date Editor | 与 Today 共用内容但缺少明确的历史日期上下文 | 用户容易把历史编辑误认为 Today | Header 明确固定日期和“历史记录”辅助标签 | 否 | Fixed Date、跨午夜、大字体 |
| Summary | 所有状态、说明、内容和按钮按顺序平铺；成功后操作过多 | 阅读体验被控件打断 | 顶部周期控制、统一状态面板、正文阅读面、主操作 + Overflow 次要操作 | 否 | Empty/Generating/Success/Stale/Failed |
| Settings / AI Settings | 每组都包 Surface，每项内部结构不统一；危险操作与普通操作同权 | 页面碎片化，配置层级不清 | 分组标题 + 连续设置行；统一图标、摘要、尾控件和危险操作样式 | 否 | Mock/真实配置/API Key/自动总结 |
| Data Management | 导出、备份、恢复都使用大按钮和 Section 容器；安全信息散落 | 主次操作不清，恢复风险信息难扫描 | 图标化 ActionRow、结构化 Preview 指标、统一安全提示和严重状态 | 否 | v1/v2 Preview、busy、RecoveryRequired |
| Dialog / Bottom Sheet | Todo 编辑使用 AlertDialog 承载长表单，1.5× 时高度紧张 | 大字体和横屏滚动体验一般 | 统一 28dp 顶部形状、可滚动内容和清晰主次按钮；保留现有对话语义 | 否 | 1.5×、横屏、焦点与取消 |
| Snackbar | 使用默认 SnackbarHost，缺少统一视觉和语义 | 与品牌界面割裂 | 由 App Shell 提供统一 tonal Snackbar | 否 | 成功/失败消息语义 |
| Splash / Icon | 当前启动体验偏默认，图标未与新品牌层级协同 | 第一印象与应用内部不一致 | 复核现有本地 Vector，必要时仅调整品牌背景和自有 Vector | 否 | 冷启动、浅深背景 |

## 模式与边界结论

- 浅色：当前背景与 Card 对比过弱，层级主要依赖容器边界。
- 深色：背景接近黑色，Surface 灰阶角色不足，嵌套 Card 更明显。
- Dynamic Color：完全采用系统色，可能削弱青蓝品牌识别；需保留状态色语义和排版层级。
- 1.3×/1.5×：核心功能可用，但长 Dialog、横向按钮行和 Summary 操作容易拥挤。
- 横屏：没有 NavigationRail 或阅读宽度约束，内容被简单拉宽。
- 大数据：LazyColumn 使用正确，但 Todo 内层固定高度列表形成嵌套滚动；视觉重构应减少单项高度，不改变持久化策略。
- 键盘：编辑器依赖 Scaffold resize，主要输入可用；需避免新增浮动控件遮挡。
- TalkBack：Todo 排序基础较好；新增视觉标签必须继续提供文本和状态语义，不能只靠颜色。

## 验收重点

1. Today 首屏能在一次扫视中识别日期、完成率、重点 Todo 和快速记录入口。
2. Todo/History/Editor 不再呈现连续高权重卡片墙。
3. Summary 成功态以阅读为主，Settings 以分组连续列表为主。
4. 普通竖屏使用 NavigationBar，宽屏横屏使用 NavigationRail。
5. 浅色、深色和 Dynamic Color 都保持优先级及错误/成功语义。
6. 1.5× 字体、横屏、TalkBack、50 条 Todo 和 50×8 TABLE 不退化。
7. Room Schema、业务状态、备份协议和 Release Tag 保持不变。
