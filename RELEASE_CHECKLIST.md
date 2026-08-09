# WorkLog AI 0.4.1 内部试用发布检查清单

## GitHub 与 Git

- [x] PR #4 已完成 AI 总结稳定性修复和 UI/UX 精修审查。
- [x] PR #4 已以普通 Merge Commit `4b6b603870e757724518375eb402fcbb6fc1ce54` 合入 `main`。
- [x] PR Base 为 `main`，Head 为 `feature/v0.4.1-ui-ux-polish`；未 force push，也未删除远程功能分支。
- [x] `v0.1.0-internal` 至 `v0.4.0-internal` 均保持原指向。
- [ ] 0.4.1 最终 Release 提交、`main` 推送和 `v0.4.1-internal` 推送将在最终 HEAD 重建验收后完成。

## 版本与测试

- [x] versionName：0.4.1；versionCode：6；Release applicationId：`com.worklogai.app`。
- [x] Room database version：2；backupFormatVersion：2；Schema 1/2 与 Migration 均未修改。
- [x] JVM/Robolectric：286/286，44 suites，failures/errors/skipped 均为 0。
- [x] Android 16 HONOR Debug Instrumentation：67/67；真实横屏 NavigationRail 专项：1/1；failures/errors/skipped 均为 0。
- [x] Debug Lint：0 errors、35 warnings；本轮关注的 UI/Accessibility 阻断项为 0。
- [x] Detekt、ktlint 通过。
- [ ] Release Lint 通过后勾选。
- [ ] R8/minification 与资源压缩确认启用。
- [ ] 0.4.0/code 5 使用同一证书覆盖升级到 0.4.1/code 6，数据与设置保持。

## 历史补录

- [x] History 提供“补记录”入口，Material 3 DatePicker 禁止未来日期。
- [x] 无 WorkEntry 的日期可进入 Empty Editor；10 个空日期直接退出后 History 新增记录为 0。
- [x] TEXT、IMAGE、TABLE 均在首个有效内容产生时按需创建 WorkEntry。
- [x] Photo Picker 取消不创建 WorkEntry；图片成功后 Attachment 与 IMAGE block 正常。
- [x] History 与 Search 即时更新，重启和 Backup v2 恢复后仍可读。
- [x] 已结束自然周和自然月的 Summary 显示“原记录已更新”，旧内容和 editedContent 保留。
- [x] 历史 Todo“完成并记录”写入 scheduledDate，并创建有效 `linkedContentBlockId`。
- [x] 快速重复打开补记录入口只存在一个 DatePicker；返回栈无重复编辑页。
- [x] 横竖屏和后台恢复期间 targetDate 保持不变。

## 数据与协议

- [x] Room database version：2；本版本无新 Migration，无 destructive migration。
- [x] Schema 1、Schema 2 均保留且字节未变化。
- [x] Schema 1 SHA-256：`4BF57A358800911B18E10DF66C105D4F513AC7DDC16F89E7CF9F00CE59E32E4C`。
- [x] Schema 2 SHA-256：`944C04F92F7633FCFCA2DB407588B850B981AC7B2E4541A0498505330D40862D`。
- [x] 显式 Migration 1→2 保持不变。
- [x] backupFormatVersion：2；补录数据继续使用既有 WorkEntry、ContentBlock 和 Attachment 协议。
- [x] Release Backup v2 Preview/Restore 通过；测试 API Key 未进入备份且恢复后“已配置”状态保留。
- [x] 备份关系审计：重复 entryDate、孤立 ContentBlock、孤立 Attachment、失效 Todo link 均为 0。

## 签名与 Release

- [x] Release Keystore 位于仓库外，alias 为 `worklog-ai-release`，算法 RSA 4096。
- [x] 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- [x] 用户已确认 Keystore 安全备份。
- [x] APK v2/v3 签名有效且不是 Debug 证书；AAB `jarsigner` 验证有效。
- [x] `debuggable=false`、`usesCleartextTraffic=false`、`allowBackup=false`。
- [x] 合并 Manifest 权限与 0.3.1 完全一致，仅保留应用功能及 WorkManager 所需权限。
- [x] APK/AAB 未发现 dev/debug/test 入口、测试 Key、Keystore、密码、私有路径或测试备份。
- [x] 最终 HEAD 的 APK/AAB SHA-256 与 R8 mapping 在忽略目录 `release-artifacts/0.4.0/` 的 `SHA256SUMS.txt` 和 `RELEASE_REPORT.txt` 中归档。

## 兼容性风险接受

> 用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行，不宣称通过，作为 0.4.0 内部试用风险接受。

- 完整矩阵仅在 HONOR PPG-AN00、Android 16 / API 36 真机执行；不记录设备序列号。
- Dynamic Color 受壁纸影响；其他 OEM SAF Provider、长期后台行为和外部 OpenAI Compatible 服务继续在内部试用期观察。
- 备份默认未加密；当前无待办提醒、循环待办或子任务。
