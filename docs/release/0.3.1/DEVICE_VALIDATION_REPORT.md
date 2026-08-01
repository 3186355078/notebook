# WorkLog AI 0.3.1 设备验收报告

版本：`0.3.1`（versionCode 4）
设备：HONOR PPG-AN00
系统：Android 16 / API 36
设备序列号：不记录

## 验收范围

- 0.3.0 签名 Release 覆盖升级到 0.3.1。
- Todo“查看记录”连续点击 10 次的导航幂等、linked block 定位、高亮和返回栈。
- 旋转、进程重建、后台恢复、TalkBack 与 linked block 缺失。
- Today、Todo、TEXT、IMAGE、TABLE、History、Search、Summary、Settings、Data Management、WorkManager、通知深链、Keystore、Markdown、Backup v2 和 Restore。
- 浅色、深色、Dynamic Color、1.5× 字体和横屏。

## Debug 回归基线

| 检查 | 结果 |
| --- | --- |
| JVM/Robolectric | 254/254，42 suites，failures/errors/skipped = 0 |
| Android Instrumentation | 60/60，failures/errors/skipped = 0 |
| 连续激活 10 次 | 只打开一个目标，返回一次回到 Today |
| Activity 重建 | 不自动重复导航 |
| FATAL / ANR / OOM | 0 / 0 / 0 |

## 签名 Release 验收

> 本节在最终 0.3.1 签名 APK 覆盖安装和真机专项完成后填写；未执行项不写成通过。

- [ ] 0.3.0→0.3.1 覆盖安装，versionCode 3→4，签名一致。
- [ ] WorkEntry、ContentBlock、Attachment、WorkSummary、Todo、设置和 API Key 配置状态保留。
- [ ] 快速点击“查看记录” 10 次：导航 1 次，linked block 定位 1 次，高亮 1 次。
- [ ] 返回一次回到 Today，再次打开正常。
- [ ] 旋转、进程恢复、TalkBack 和 linked block 缺失场景受控。
- [ ] 浅色、深色、Dynamic Color、1.5× 和横屏通过。
- [ ] FATAL / ANR / OOM / Room integrity error / SerializationException 均为 0。

## 数据与协议

- Room database version = 2。
- Schema 1/2 与 0.3.0 字节一致。
- 无新 Migration，无 destructive migration。
- backupFormatVersion = 2。

## 兼容性边界

用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行、不宣称通过，作为 0.3.1 内部试用风险接受。
