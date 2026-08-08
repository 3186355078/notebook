# WorkLog AI 0.4.0 设备验收报告

版本：`0.4.0`（versionCode 5）

设备：HONOR PPG-AN00

系统：Android 16 / API 36

设备序列号：不记录

## 构建与回归

| 检查 | 结果 |
| --- | --- |
| JVM/Robolectric | 275/275，44 suites，failures/errors/skipped = 0 |
| Android Instrumentation | 65/65，failures/errors/skipped = 0 |
| Lint Debug / Release | 通过，无 Error/Fatal |
| Detekt / ktlint | 通过 |
| R8 / shrinkResources | 启用并构建通过 |
| FATAL / ANR / OOM | 0 / 0 / 0 |

65 项 Instrumentation 使用 Debug 独立 applicationId 执行；签名 Release 随后执行覆盖升级、历史补录、Backup 和稳定性人工专项。

## 0.3.1 → 0.4.0 覆盖升级

- 0.3.1 APK：versionCode 4，Release applicationId `com.worklogai.app`，证书 SHA-256 与 0.4.0 一致。
- 升级前模拟数据：4 条 WorkEntry、6 个 ContentBlock、2 个 Attachment、2 份 WorkSummary、8 条 Todo，其中 2 条有 linkedContentBlockId。
- Backup v2 在 0.3.1 中完成预检与恢复；API Key 仅确认“已配置”，未读取或记录明文。
- 使用 `adb install -r` 覆盖安装 0.4.0/code 5 成功，未清除应用数据，也未执行新 Room Migration。
- 升级后原日期、TEXT、IMAGE、TABLE、Summary、Todo 统计、linked Todo、设置和 API Key“已配置”状态均保留。

## 空日期与按需创建

- 依次进入 10 个没有 WorkEntry 的历史日期并直接返回；History 未出现这些日期，新增 WorkEntry 为 0。
- 8 月 6 日补录 TEXT：首次有效文本后创建一个 WorkEntry 和一个 TEXT block，History 与 Search 立即命中，重启后保留。
- 8 月 2 日先取消 Photo Picker：未创建 WorkEntry；随后选择脱敏 PNG，图片处理完成后创建 WorkEntry、Attachment 和 IMAGE block。
- 7 月 20 日补录 2×2 TABLE：按需创建 WorkEntry，标题可检索，编辑内容持久化。
- 备份关系审计结果：重复 entryDate = 0，孤立 ContentBlock = 0，孤立 Attachment = 0。

## History、Search、Summary 与 Todo

- History 在补录后无需重启即可按日期倒序显示新记录；TEXT 和 TABLE 关键词可立即搜索，IMAGE 显示图片记录摘要。
- 在 7 月 8 日补录后，2026-07-06～07-12 周报显示“原记录已更新”，原 editedContent 保留。
- 在 7 月补录后，2026-07-01～07-31 月报显示“原记录已更新”，原 editedContent 保留。
- `allowAiProcessing=false`、当前周期和 SourceHash 边界由 JVM/Instrumentation 回归覆盖；禁止 AI 的内容不进入 Summary 输入。
- 在原本没有 WorkEntry 的 7 月 29 日完成历史 Todo 并选择“完成并记录”，只创建 scheduledDate 对应记录和 TEXT block；History 搜索命中，`linkedContentBlockId` 有效。
- 并发首次创建和同日期幂等由 Repository/UseCase 自动化测试覆盖，未修改 Room Schema。

## Backup v2

- 补录后创建的 0.4.0 Backup：formatVersion 2、databaseVersion 2，清单包含 9 条日志、10 个内容块、3 个附件、8 条待办和 3 份总结。
- 备份中未发现测试 API Key；API Key 和 Android Keystore 数据未进入 ZIP。
- 创建一个备份后临时 Todo，再恢复该备份：临时 Todo 被移除，补录 TEXT/IMAGE/TABLE、Todo links、Summary 和 API Key“已配置”状态保持。
- backupFormatVersion 保持 2，无协议升级。

## 导航与生命周期

- 连续点击“补记录”10 次只出现一个 DatePicker。
- 相同日期使用单实例导航，返回栈未出现重复 Fixed Date Editor。
- 8 月 4 日 Empty Editor 在横屏、恢复竖屏及切后台再回来后仍显示同一 targetDate；退出后未创建空记录。
- 未来日期、非法参数、进程重建、跨午夜和时区边界由自动化回归覆盖；ViewModel 与 UseCase 均拒绝未来 WorkEntry。

## 稳定性与限制

- 本轮 Logcat 未发现 FATAL、ANR、OOM、Room integrity error 或 SerializationException。
- Android 10～13 第二台真机或模拟器按用户明确要求未执行，不宣称通过。
- Dynamic Color 受壁纸影响；其他 OEM SAF Provider 和长期后台行为仍需内部观察。
- 完整备份默认未加密；无提醒、循环待办和子任务。
