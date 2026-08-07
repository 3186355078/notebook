# 历史工作记录补录

## 使用场景

当某个日期没有创建过工作记录时，用户可以从 History 顶部的“补记录”入口选择该日期并直接进入固定日期编辑器。该功能面向遗漏记录的补充，不会预生成空日期，也不会改变 Today 的跨午夜行为。

## 入口与日期限制

- History 页头提供轻量“补记录”操作，不使用遮挡列表的 FAB。
- Material 3 DatePicker 允许选择今天或更早的合法日期，取消后保留原搜索、筛选和列表状态。
- 新的 `OpenBackfill` 动作在 ViewModel 再次拒绝未来日期。
- `CreateWorkContentForDateUseCase` 在业务层执行最终未来日期校验，防止绕过 UI 创建未来工作内容。
- 普通 Fixed Date 页面仍保留既有的未来 Todo 规划能力，但未来日期不会开放工作内容编辑。

## Empty Editor

不存在 WorkEntry 的日期是合法的编辑器状态。Fixed Date Editor 会显示目标历史日期、历史记录标识和空状态，并继续提供文字、图片、表格入口。

仅打开页面、旋转、切后台或直接返回都不会调用 `getOrCreateEntry`，因此不会向数据库写入空 WorkEntry，也不会污染 History。Fixed Date 的 `targetDate` 来自导航参数，页面生命周期内不跟随午夜或时区变化。

## 按需创建内容

内容创建统一经过 `CreateWorkContentForDateUseCase` 和 `WorkEntryRepository.createContentForDate`：

1. 在 Room 单个事务中查询目标日期；
2. 复用现有 WorkEntry，或在不存在时创建一个；
3. 创建首个或后续 ContentBlock；
4. IMAGE 同一事务写入 Attachment；
5. 返回完整 Entry 与已创建 Block。

`work_entries.entryDate` 继续使用现有唯一索引，Room 事务串行化同一数据库上的首次创建；连续 TEXT/TABLE/IMAGE 操作不会产生同日期重复 WorkEntry。块或附件插入失败时事务整体回滚，新日期不会残留空 Entry。

### TEXT

空历史日期点击“文字”只创建内存中的临时编辑块。首个非空 Draft 经过现有 600ms 自动保存边界后才创建 WorkEntry 与 TEXT Block。空白 Draft 离页前不入库；首次创建完成后编辑器原地从 Empty 转为 Content。

### IMAGE

Photo Picker 取消不会向 ViewModel 提交 URI，因此不创建 WorkEntry。图片导入成功后才执行 Entry、IMAGE Block、Attachment 的事务写入；文件处理或数据库写入失败时沿用文件补偿清理。

### TABLE

用户触发创建表格后，默认表格与目标日期 WorkEntry 在同一事务链路中创建。表格编辑和 600ms 保存策略保持原语义。

## 联动

- **History**：创建成功后 Room Flow 与页面 `ON_RESUME` 刷新列表，新日期按倒序出现；空日期不会出现。
- **Search**：TEXT、IMAGE caption、TABLE 继续使用现有索引/查询语义和 300ms 搜索防抖，无需重启。
- **Todo**：历史 Todo 的“完成并记录”继续按 Todo 的 `scheduledDate` 创建/复用 WorkEntry，并维护 `linkedContentBlockId`；不会误写 Today。
- **Summary**：已结束周/月的来源哈希基于当前符合 AI 条件的 Entry 重新计算。补录允许 AI 的内容会令已有 Summary stale，但不会覆盖旧内容或自动重新生成；`allowAiProcessing=false` 的 Entry 仍由 WorkSummarySkill 排除。
- **Backup**：历史补录只产生既有 WorkEntry、ContentBlock、Attachment 数据，继续由 Backup v2 保存和恢复，不升级协议，也不包含 API Key。

## 导航与生命周期

- DatePicker 使用单一可见状态，连续点击不会叠加多个选择器。
- Fixed Date 导航继续使用目标 route 比较与 `launchSingleTop`；相同日期不重复入栈，返回一次回到 History。
- Today 继续跟随 LocalDate；Fixed Date 始终使用导航 `targetDate`。
- 旋转、后台恢复、进程重建、深色模式、大字体和横屏不会把历史目标日期切换为今天。

## 测试范围

- 日期边界：过去、今天、未来、闰年、跨年和 UTC DatePicker 转换。
- Empty Editor：不存在 Entry、20 个空日期不入库、空白 TEXT 不入库。
- 内容：首个 TEXT/TABLE/IMAGE、失败回滚、软删除恢复、同日期复用。
- 联动：History、Search、Summary SourceHash、Todo 既有事务、Backup v2。
- 导航与 UI：补记录入口、DatePicker、历史空状态、TEXT/IMAGE/TABLE 操作和相同日期单实例。
- Android 设备集成：真实 Room、History Search、未来日期拒绝以及 Backup v2 恢复。

## 数据与协议

```text
Room version = 2
backupFormatVersion = 2
Schema 1/2 = 不变
Migration = 未新增
```
