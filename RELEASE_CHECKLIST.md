# WorkLog AI 0.1.0 内部发布检查清单

## Git

- [x] 分支为 `main`。
- [x] 初始基线提交为 `e52a7f7e50aa4ce30ee6539114ea67d69447ebd1`。
- [x] 关卡 B 硬化提交为 `ca35567`。
- [ ] 发布准备提交和本地 `v0.1.0-internal` annotated Tag 已创建。
- [x] 未 push，未修改远程，未上传应用商店。
- [ ] 最终工作区、Git 空白和敏感信息扫描通过。

## 测试与构建

- [x] JVM/Robolectric：213/213，failures/errors/skipped 均为 0。
- [x] Android 16 HONOR Instrumentation：41/41，failures/errors/skipped 均为 0。
- [x] Debug Lint、Detekt、ktlint 通过。
- [x] Release Lint：0 errors；保留 29 个不阻断 warning。
- [x] Release APK/AAB 已由用户完成一次签名构建。
- [x] R8 和资源压缩启用。
- [ ] 最终提交后强制无缓存 Release 构建和干净克隆构建通过。

## 数据协议

- [x] Room database version：1。
- [x] Schema：`app/schemas/com.worklogai.app.core.database.WorkLogDatabase/1.json`。
- [x] Schema SHA-256：`4BF57A358800911B18E10DF66C105D4F513AC7DDC16F89E7CF9F00CE59E32E4C`。
- [x] Migration：无；未使用 destructive migration。
- [x] backupFormatVersion：1；完整备份恢复和损坏归档矩阵通过。

## 签名

- [x] Release Keystore 位于仓库外并由用户交互式创建。
- [x] alias：`worklog-ai-release`。
- [x] 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- [x] RSA 4096，自签名证书有效至 2053-12-05。
- [x] 用户确认已在可信位置保存额外安全备份。
- [x] APK v2/v3 签名验证通过，非 Debug 证书；AAB `jarsigner` 验证通过。
- [x] 密码未进入聊天、Git、Gradle 文件、BuildConfig、资源或报告。

## Release 产物

- [ ] 最终提交对应的 APK、AAB、SHA-256 和 R8 mapping 已生成并归档。
- [x] `release-artifacts/`、APK、AAB 和 R8 输出均被 Git 忽略。
- [ ] Release 真机完整冒烟和最终日志检查通过。

## Manifest 与权限

- [x] `debuggable=false`、`usesCleartextTraffic=false`、`allowBackup=false`。
- [x] 无传统存储、全部文件访问、相机、位置、通讯录、电话或精确闹钟权限。
- [x] 主动权限为 INTERNET、POST_NOTIFICATIONS；WorkManager 依赖额外合并 WAKE_LOCK、ACCESS_NETWORK_STATE、RECEIVE_BOOT_COMPLETED 和 FOREGROUND_SERVICE。
- [x] Launcher Activity 以外的应用组件未无保护导出；AndroidX 组件按库声明和权限保护。

## 发布材料

- [x] `PRIVACY.md`、`CHANGELOG.md`、`INTERNAL_TESTING_GUIDE.md`、`THIRD_PARTY_NOTICES.md`。
- [x] Bug Report 模板和设备验收报告。
- [ ] 最终 `SHA256SUMS.txt` 与 `RELEASE_REPORT.txt` 已生成到忽略目录。

## 风险接受

> 用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行，不宣称通过，作为 0.1.0 内部试用风险接受。

- 当前完整矩阵只在 Android 16 / API 36 的 HONOR PPG-AN00 真机执行。
- 旧 Android 用户需要重点反馈 Photo Picker/兼容选择器、SAF、通知和 WorkManager 后台任务。
- 备份默认未加密；其他 OEM 的文件提供器和长期后台策略继续在内部试用期观察。
