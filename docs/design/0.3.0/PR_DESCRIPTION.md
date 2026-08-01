## 变更背景

本分支基于已发布并合入 `main` 的 0.3.0 UI 设计继续做 0.3.1 定向精修，同时修复 Todo“已同步记录”连续点击产生重复页面的问题。原 Draft PR #1 已合并，PR #2 已以 Merge Commit 合入 `main`；不重写历史，不修改 0.3.0 Release Tag。

## 视觉方向

- 低饱和青蓝品牌色、柔和靛蓝辅助色、暖灰白/深灰蓝背景。
- 通过排版、留白、Tonal Surface 和局部 Accent 组织层级。
- 统一 Typography、4–32dp 间距、8–20dp 圆角、克制 elevation 与 150–300ms 动效。
- 优先级与状态同时使用颜色、文字和图标。

## 主要页面变化

- Today：日期与状态合并为单一视觉锚点，概览压缩为一行，减少顶部留白和重复标题。
- Todo：提高列表密度，弱化完成/取消项与拖动装饰；快速添加支持 IME Done。
- 已同步记录：改为轻量“查看记录”入口，打开期间禁用；同一目标在入口、事件和导航栈三层保证幂等。
- Editor：linked ContentBlock 只滚动和高亮一次，缺失时显示受控提示。
- History：继续弱化时间线与容器边界，保持日期锚点和可读摘要。
- Summary：生成状态更稳定，失败提示不覆盖既有成功内容。
- Settings：保持连续分组，不新增卡片墙。
- Data Management：长任务状态、警告和错误使用统一紧凑反馈，页面结构不跳动。
- Dialog/Bottom Sheet：Todo 相关浮层互斥，防止连续操作叠加。

## 重复导航修复

- 根因：入口没有同步 in-flight 状态，目标路由也没有比较当前日期和 linked block。
- 入口：`TodayTodoViewModel` 在发送事件前同步锁定，返回、失败或定位完成后释放。
- 事件：使用无 replay 的 `Channel`，不保存到 `SavedStateHandle`。
- 路由：使用 `launchSingleTop`，相同 route、日期和 `linkedContentBlockId` 不再次入栈。
- 定位：linked block 参数一次读取并移除；存在时只高亮一次，不存在时安全降级。

## 无障碍与响应式

- 标题 heading、进度 stateDescription、Todo 四状态文本与图标。
- 保留 Todo TalkBack 上移/下移/调整优先级动作。
- 主要触控目标至少 48dp。
- 1.3×/1.5× 字体继续使用滚动容器；宽屏/横屏切换 NavigationRail。
- Dynamic Color 不替代错误和优先级的非颜色语义。

## 测试结果

- JVM/Robolectric：254/254，42 suites，failures/errors/skipped = 0（原 249 项全部保留，新增 5 项）。
- Android Test 源码：编译通过。
- Android Instrumentation：60/60，failures/errors/skipped = 0；原基线未减少，并新增 linked-record 真机集成方法。
- assembleDebug、lintDebug、Detekt、ktlintCheck：全部通过。
- 所有最终任务使用 `--rerun-tasks --no-build-cache`。
- 真机专项覆盖连续激活 10 次、返回栈、重建和再次进入。
- HONOR Android 16 / API 36 日志：FATAL/ANR/OOM = 0，重复导航 = 0。

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
- `docs/design/0.3.0/VISUAL_ACCEPTANCE.md`
- `docs/design/0.3.0/UX_FIXES.md`
