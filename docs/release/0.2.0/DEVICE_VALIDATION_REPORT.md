# WorkLog AI 0.2.0 设备验收报告

## 设备与范围

- 设备：HONOR PPG-AN00。
- Android：16 / API 36。
- 设备序列号不记录。
- Android 10～13 第二台真机和模拟器专项测试按用户明确要求未执行，不宣称通过。

## 自动化基线

- JVM/Robolectric：246 个，41 suites，failures/errors/skipped 均为 0。
- Android Instrumentation：51 个，failures/errors/skipped 均为 0。
- Debug Lint、Detekt、ktlint：通过。

## 0.2.0 验收矩阵

- [x] 0.1.0 Release 安装、v1 Preview/Restore 和升级前数据基线建立。
- [x] 使用同一 Release 证书将 0.1.0 覆盖升级至 0.2.0，应用数据未清除。
- [x] Room Migration 1→2，旧日志、附件、TABLE 和总结保留。
- [x] Todo 创建、四级优先级、四种状态、跨优先级排序、完成并记录、工作记录转待办和重启保持。
- [x] v1 Preview/Restore 与 v2 Todo Preview/Restore；v1 Todo 数为 0，v2 恢复 20 条 Todo。
- [x] v2 损坏备份的未来版本、重复 ID、非法优先级/状态、负排序、无效关联、计数不一致和 SHA 错误均受控拒绝，当前数据保持。
- [x] TEXT、IMAGE、TABLE、History、Search、Summary、WorkManager、通知深链、Markdown 和 Keystore 冒烟。
- [x] 浅色、深色、Dynamic Color 设计基线、1.5× 字体、横屏和无障碍排序入口。
- [x] 50 条 Todo 横屏大字体仍可滚动，主要导航和进度可读。
- [x] Logcat 未发现应用相关 FATAL、ANR、OOM、SerializationException 或 Room 校验失败。

## 性能与限制

阶段 10 已在同一设备验证 50 条 Todo、1,000 条日志和约 11.4 MB 模拟备份；0.2.0 Release 再次验证 50 条 Todo、1.5× 字体和横屏。冒烟结束时应用 TOTAL PSS 约 150 MB，未见阻断卡顿、ANR 或 OOM。其他 OEM、真实大型备份和长期后台调度继续观察。

完整备份默认未加密；Todo 不直接进入 AI。待办提醒、循环待办和子任务不属于 0.2.0。
