# WorkLog AI 0.1.0 设备验收报告

验收日期：2026-07-22

## 设备矩阵

| 类型 | 设备 | Android / API | 分辨率 | 语言 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 真实设备 | HONOR PPG-AN00 | Android 16 / API 36 | 1264×2800 | zh-Hans-CN | 完整 Debug 关卡 B 与签名 R8 Release 冒烟通过 |

报告不记录设备序列号。

> 用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行，不宣称通过，作为 0.1.0 内部试用风险接受。

## 自动化基线

- JVM/Robolectric：213/213；failures 0，errors 0，skipped 0，37 suites。
- Android 16 connected Instrumentation：41/41；failures 0，errors 0，skipped 0。
- Android Test 编译、Lint、Detekt、ktlint 和强制无缓存构建均通过。

## Debug 真机功能矩阵

- TEXT：自动保存、后台 flush、强停重启恢复通过。
- IMAGE：Photo Picker 取消/选择、JPEG、透明 PNG、EXIF 旋转、2048px 限制、预览、说明和重启读取通过。
- TABLE：1×1～50×8、横纵滚动、软键盘、连续输入、后台 flush 和重启持久化通过。
- 历史：30 条分页、65 条以上数据、日/自然周/月、跨年/闰年、搜索和固定日期编辑通过。
- Mock 周报/月报：已结束周期、编辑、恢复原文、复制、取消、重试、SourceHash 和 Markdown 导出通过。
- Keystore：AES-GCM 保存/读取/删除、重启、备份排除和恢复后保留通过。
- 通知：拒绝/授权、PRIVATE、通知槽位复用、前台/后台/冷启动深链、失败和非法参数安全回退通过。
- WorkManager：唯一调度、CONNECTED/UNMETERED、电量/存储约束、429/timeout/5xx retry、401/403 不 retry、开关跳过、重启和 Doze 通过。
- SAF：正常 Markdown、完整备份恢复及 Manifest/SHA/size/Zip Slip/重复 Entry/关系错误/ZIP Bomb 损坏矩阵通过。
- 恢复 journal：ATTACHMENTS_SWITCHED、DATABASE_REPLACED 和启动修复通过。
- 生命周期：旋转、深色模式、1.3 倍字体、后台、强停、跨午夜、Asia/Shanghai、America/Denver、UTC 时区切换和设备重启通过。

## 性能与规模

- 自动化数据集：1,000 WorkEntry、5,000 ContentBlock、200 Attachment 元数据、500 WorkSummary。
- 验证最近 30 条、第二页去重、TEXT/图片说明/TABLE 搜索、备份、Preview、Restore 以及恢复后查询。
- 50×8 表格与约 11.4 MB 模拟备份通过真实设备与 SAF 流程验证。
- 未观察到阻断 ANR、OOM 或 FATAL；长期 OEM 后台稳定性和更大真实备份继续在内部试用期观察。
- 性能用例记录近似耗时和 PSS，但不设不可靠的跨设备硬性毫秒承诺。

## Release R8 冒烟

- 签名 Release 已全新安装；两次冷启动 `TotalTime` 约 143 ms 和 158 ms。
- TEXT、IMAGE、TABLE、历史、搜索、Mock 周报/月报、Keystore 和 WorkManager 已验证。
- Markdown 日志/周报/月报通过系统 Downloads 导出并检查内部数据排除。
- 完整备份已通过 SAF 创建，包含 7 个协议条目和 1 个受控图片条目；Preview、完整替换、恢复后的历史/图片/表格/周报/月报读取及强停重启均通过。
- Release 自动周报实际发布 PRIVATE 通知“周报已生成”，正文仅包含 7月13日—7月19日周期、不包含工作正文；从通知栏点击后准确打开 WEEKLY 对应周期。
- 恢复完成后首次回到 Today 曾出现一次可重试的加载失败；根因为导航复用恢复前 Today ViewModel，已改为弹出旧目的地后重建并新增 JVM 回归测试。最终签名包已再次通过 SAF Preview/Restore：首次返回 Today 无加载失败，历史立即显示 3 个有效日期，图片/表格记录与已恢复周报可读取，强停重启后数据仍保持。
- 当前未观察到应用进程 FATAL、ANR、OOM、Room、Hilt、WorkerFactory、Serialization 或 Keystore 异常。

## 已知限制

- Android 10～13 专项兼容矩阵未执行。
- 只完整验证了系统 Downloads Provider；其他 OEM/第三方 SAF Provider 行为仍需反馈。
- HONOR 长期后台调度受系统电池策略影响，需要内部试用观察。
- 完整备份未加密；大型真实 ZIP 的设备性能继续观察。
- 外部 OpenAI Compatible 服务在协议细节、模型和限额方面可能不同。
