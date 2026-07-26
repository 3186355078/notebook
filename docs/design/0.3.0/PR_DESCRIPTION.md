## 变更背景

0.2.0 已完成待办、工作记录联动和发布验收，但用户实际使用后认为页面仍存在卡片嵌套、信息层级不清、深浅色质感不足和页面语言不统一的问题。本 PR 对 Compose UI 做系统性重构，不改变业务语义、Room Schema 或备份协议。

## 视觉方向

- 低饱和青蓝品牌色、柔和靛蓝辅助色、暖灰白/深灰蓝背景。
- 通过排版、留白、Tonal Surface 和局部 Accent 组织层级。
- 统一 Typography、4–32dp 间距、8–20dp 圆角、克制 elevation 与 150–300ms 动效。
- 优先级与状态同时使用颜色、文字和图标。

## 主要页面变化

- App Shell：统一子页面 TopAppBar、背景和导航；顶级页面使用单一 Page Header，普通竖屏 NavigationBar，宽屏 NavigationRail。
- Today/Todo：紧凑日期概览、局部优先级 Accent、明确四状态图标、轻量拖动反馈。
- Editor：TEXT/IMAGE/TABLE 改为内容流和统一编辑容器。
- History：日期锚点与轻量时间线，摘要和 Todo 统计降为次级信息。
- Summary：阅读态优先，次要操作进入 Overflow，状态标签统一。
- Settings：连续分组设置行，不再每项独立大卡片。
- Data Management：统一导出/备份/恢复 Action Row 和结构化 Preview。
- Empty/Loading/Error：使用统一状态组件。

## 无障碍与响应式

- 标题 heading、进度 stateDescription、Todo 四状态文本与图标。
- 保留 Todo TalkBack 上移/下移/调整优先级动作。
- 主要触控目标至少 48dp。
- 1.3×/1.5× 字体继续使用滚动容器；宽屏/横屏切换 NavigationRail。
- Dynamic Color 不替代错误和优先级的非颜色语义。

## 测试结果

- JVM/Robolectric：249/249，42 suites，failures/errors/skipped = 0。
- Android Test 源码：编译通过。
- Debug 全量任务：assemble、lint、Detekt、ktlint 全部通过。
- 全量任务使用 `--rerun-tasks --no-build-cache`。
- Connected Android Test：54/54，failures/errors/skipped = 0。
- HONOR Android 16 / API 36 应用日志：FATAL/ANR/OOM = 0。
- 真机人工抽查 Today、History、Summary、Settings；截图仅保存在 Git 忽略的构建目录。

## 数据与协议

- Room version：2（未变化）。
- Schema 1/2：未变化。
- Migration：未变化。
- backupFormatVersion：2（未变化）。
- Todo、WorkEntry、Summary 和备份恢复业务语义未变化。

## 已知限制

- Android 10–13 第二设备/模拟器专项测试按用户既有豁免未执行。
- Dynamic Color 观感受设备壁纸色板影响。
- 其他 OEM SAF 与长期后台行为继续在内部试用中观察。

详细审计与验收见：

- `docs/design/0.3.0/UI_AUDIT.md`
- `docs/design/0.3.0/UI_REDESIGN_REPORT.md`
