# WorkLog AI 0.3.1 UX 修复记录

## 连续点击“已同步记录”产生重复页面

### 问题

Todo 完成并同步为工作记录后，快速连续点击“已同步记录”入口，会在首个导航完成前重复压入相同固定日期编辑页。用户需要多次返回，且页面可能出现闪烁或重复定位。

### 根因

问题同时存在于入口和目标两侧：

1. 点击入口没有导航进行中状态，同一帧的多次点击都会发出事件。
2. 目标路由未比较当前日期与 `linkedContentBlockId`，且未启用 `launchSingleTop`。
3. linked block 定位参数未被明确设计为一次性消费，存在被生命周期重建重复处理的风险。

这不是数据库重复写入问题，也不是 Todo 与 ContentBlock 关系错误。修复没有采用固定 500ms 延迟掩盖竞态。

### 修复

- `TodayTodoViewModel` 在发送导航事件前同步设置导航锁；失败、返回或定位完成后释放。
- 导航事件通过无 replay 的 `Channel` 发送，重组不会重放。
- `WorkLogApp` 对 entry route 使用 `launchSingleTop`，并拒绝当前 route、日期、linked block 完全相同的目标。
- `TodayViewModel` 一次读取并移除 `SavedStateHandle` 中的 linked block 参数。
- linked block 存在时滚动并短暂高亮一次；不存在时给出不含内部路径的轻量提示。
- Todo 行中的入口改为独立 48dp “查看记录”按钮，不与父容器共享点击语义；打开期间显示“正在打开”并禁用。

### 测试

- 单击、快速双击和连续激活 10 次。
- 只产生一个 ViewModel 导航事件。
- 相同目标不重复入栈，不同 linked block 仍可定位。
- 返回一次回到 Today，返回后可再次打开。
- 父 Todo 行与链接入口不重复响应。
- linked block 存在和不存在。
- 重组、旋转和 Activity 重建不重复导航。
- TalkBack 入口只朗读一次完整动作描述。

### 真机结果

设备：HONOR PPG-AN00，Android 16 / API 36；不记录设备序列号。

专项 Instrumentation 1/1 通过；随后使用相同 Debug APK、Android Test APK 和官方 AndroidJUnitRunner 执行全量 60/60，failures/errors/skipped = 0。连续激活 10 次只打开一个目标页面，一次返回回到 Today；返回后可以再次进入，Activity 重建没有自动重复导航。FATAL/ANR/OOM 和重复导航均为 0。

Gradle connected 任务曾在 HONOR 系统安装确认阶段超时并显示 0 tests；这不是测试失败。手动确认官方 ADB 安装后，相同测试包由 AndroidJUnitRunner 完整执行 60 个方法并全部通过。

### 回归影响

- Room version 保持 2。
- Schema 1/2、Migration 和 backupFormatVersion 2 均未变化。
- Todo 状态、排序、完成并记录及外键 `SET NULL` 语义未变化。
- 导航参数不包含 URI、文件路径、Todo 正文或工作正文。
