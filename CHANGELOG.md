# 变更日志

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
