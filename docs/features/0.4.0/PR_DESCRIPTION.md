## 背景

此前 Fixed Date Editor 在初始化时无条件 `getOrCreateEntry`，导致没有 WorkEntry 的历史日期无法作为合法空状态打开，也可能产生不应出现在 History 的空记录。本变更增加 History“补记录”流程。

## 使用流程

`History → 补记录 → 选择今天或更早日期 → Fixed Date Editor → 添加 TEXT / IMAGE / TABLE`

## 实现

- History 增加 Material 3 DatePicker 入口，并在 UI、ViewModel、UseCase 三层限制未来工作内容。
- Fixed Date Editor 支持不存在 WorkEntry 的 Empty 状态。
- 首个有效 TEXT、成功 IMAGE 或确认 TABLE 时才按需创建 WorkEntry。
- Entry、Block、Attachment 使用 Room 事务，失败不留下空 Entry；同日期复用现有唯一 Entry。
- 保留未来日期 Todo 规划和 Today 跨午夜语义。

## 联动

- 补录成功后 History 与 Search 立即可见。
- 已结束周/月 Summary 通过现有 SourceHash 机制显示 stale，旧 Summary 不被覆盖。
- 历史 Todo 完成并记录继续写入 Todo 的 scheduledDate。
- Backup v2 自动包含补录的普通 Entry/Block/Attachment 数据。

## 测试

- JVM/Robolectric：日期、Empty Editor、20 个空日期、TEXT/TABLE/IMAGE、事务回滚、History、Search/SourceHash、导航幂等。
- Compose/Instrumentation：补记录入口、DatePicker、空历史编辑器和三类内容入口。
- HONOR Android 16：真实 Room、Search、Backup/Restore 和关键 UI 回归。

## 数据协议

- Room version 仍为 2。
- Schema 1、2 不变。
- Migration 未新增。
- backupFormatVersion 仍为 2。
