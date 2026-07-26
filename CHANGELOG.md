# 变更日志

## 0.2.0 - 内部试用版（2026-07-26）

### 新增

- 每日优先级待办，支持紧急、高、中、低四级优先级。
- 未开始、进行中、已完成、已取消四种状态，以及完成备注。
- 同优先级和跨优先级手动排序；TalkBack 提供上移、下移和优先级调整替代操作。
- 完成待办时可选择同步为当天工作记录，TEXT 工作记录也可由用户主动转为待办。
- 昨日或更早未完成待办可手动迁移；历史日期显示待办完成统计。
- 备份协议 v2，新增 Todo 数据、`todoCount` 和 `maxTodos`，并继续支持恢复 v1 备份。

### 改进

- 建立统一 Material 3 颜色、Typography、间距、圆角和轻量动效体系。
- 刷新 Today、History、Summary、Settings、Data Management 和底部导航。
- 完善深色模式、Android 12+ Dynamic Color、大字体、横屏和无障碍语义。
- 快速记录改为列表内紧凑工具栏，不再覆盖 TABLE 结构操作。

### 数据升级

- Room database version 从 1 升级到 2，新增 `todo_items` 表、索引及可空工作记录链接。
- Migration 1→2 只新增 Todo 结构，保留原日志、图片、TABLE、总结和设置。
- backupFormatVersion 从 1 升级到 2；0.1.0 的 v1 备份恢复后 Todo 为空。

### 已知限制

- 不提供待办提醒、循环待办或子任务。
- 完整备份默认未加密。
- Android 10～13 第二设备/模拟器专项兼容测试按用户要求未执行。
- 其他 OEM 的 SAF Provider 和长期后台行为仍需观察。
- Todo 不直接进入 AI；只有主动同步为允许 AI 处理的工作记录后才可能参与总结。

## 0.1.0 - 内部试用版（2026-07-22）

### 新增

- 每日 TEXT、IMAGE 和 TABLE 工作记录，支持自动保存、失败重试和离开前 flush。
- 最近历史、日/周/月范围、30 条分页、关键词搜索和固定日期编辑。
- Mock 与 OpenAI Compatible Provider，手动周报/月报、编辑、恢复原文和 SourceHash 过期提示。
- WorkManager 自动周报/月报、幂等调度、约束、重试和结果通知深链。
- 单日日志、周报和月报 Markdown SAF 导出。
- 带 Manifest、SHA-256、资源限制、关系校验、补偿与启动修复的完整 ZIP 备份恢复。
- Android Keystore 保护的 API Key、本地深色模式和字体缩放适配。

### 安全与质量

- Room database version 1，备份协议 `worklog-ai-backup` version 1。
- 213 个 JVM/Robolectric 测试和 41 个 Android 16 真机 Instrumentation 测试通过。
- Release 启用 R8、资源压缩、禁止明文网络、禁用 Android 系统备份，并使用仓库外密钥签名。

### 已知限制

- 完整备份默认未加密。
- Android 10～13 第二台真机或模拟器专项兼容测试按用户要求未执行。
- 其他 OEM 的 SAF Provider 和长期后台调度行为仍需观察。
- OpenAI Compatible 服务的接口细节、模型能力、限额和计费可能不同。
- 大于当前约 11.4 MB 的真实大型备份仍需持续观察设备性能。
