# WorkLog AI 0.2.0 内部发布检查清单

## Git

- [x] 阶段 10 功能分支以 `--no-ff` 合入 `main`：`761340b`。
- [x] 版本准备提交已创建：`07a7b67`。
- [x] 最终 Release 提交和 `v0.2.0-internal` annotated Tag 作为本轮最后两个本地 Git 动作执行。
- [x] `v0.1.0-internal` 未移动。
- [x] 未 push、未修改远程、未上传应用商店。
- [x] 最终工作区、Git 空白和敏感信息扫描通过。

## 版本与测试

- [x] versionName：0.2.0；versionCode：2。
- [x] JVM/Robolectric：246/246，41 suites，failures/errors/skipped 均为 0。
- [x] Android 16 HONOR Debug Instrumentation：51/51，failures/errors/skipped 均为 0。
- [x] Debug Lint、Detekt、ktlint 通过。
- [x] Release Lint 和 R8 Release 真机冒烟通过。
- [x] 最终 HEAD 的签名构建和干净克隆 Release 构建纳入封版验收。

## 数据协议

- [x] Room database version：2。
- [x] Schema 1 保留，Schema 2 已提交。
- [x] Schema 2 SHA-256：`944C04F92F7633FCFCA2DB407588B850B981AC7B2E4541A0498505330D40862D`。
- [x] 显式 Migration 1→2；未使用 destructive migration。
- [x] backupFormatVersion：2；Reader 继续支持 v1。
- [x] 0.1.0 Release 与 v1 备份已验证签名、版本、Preview 和 Restore。
- [x] 0.1.0→0.2.0 Release 覆盖升级、v1 Restore 和 v2 Todo Restore 真机通过。

## 签名与 Release

- [x] Release Keystore 位于仓库外，alias 为 `worklog-ai-release`，算法 RSA 4096。
- [x] 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- [x] 用户已确认 Keystore 在可信位置安全备份。
- [x] APK v2/v3 签名、非 Debug 证书和 AAB `jarsigner` 验证通过。
- [x] 最终 APK/AAB SHA-256 记录在 Git 忽略目录的 `SHA256SUMS.txt` 和 `RELEASE_REPORT.txt`；签名构建不要求字节级可复现。
- [x] Release APK/AAB、SHA-256 和 R8 mapping 归档到 Git 忽略目录，不进入 Git。

## Manifest 与安全

- [x] Release `debuggable=false`、R8 和资源压缩启用。
- [x] `usesCleartextTraffic=false`、`allowBackup=false`。
- [x] 应用源码主动声明仅 INTERNET、POST_NOTIFICATIONS；合并 Manifest 另含 WorkManager 所需 WAKE_LOCK、ACCESS_NETWORK_STATE、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE 及 AndroidX 非导出动态接收器权限。
- [x] 不含传统存储、全部文件、相机、位置、通讯录、电话或精确闹钟权限。
- [x] APK/AAB 不含 Debug 入口、受控 HTTP 地址、测试 Key、用户路径、测试备份或 `0.2.0-dev`。

## 发布材料

- [x] CHANGELOG、PRIVACY、INTERNAL_TESTING_GUIDE、THIRD_PARTY_NOTICES 和 Bug Report 模板已更新。
- [x] 0.2.0 Device Validation 与 Migration Validation 报告已建立。
- [x] SHA256SUMS 和 RELEASE_REPORT 由封版脚本生成到 Git 忽略目录。

## 风险接受

> 用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行，不宣称通过，作为 0.2.0 内部试用风险接受。

- 当前完整矩阵只在 Android 16 / API 36 的 HONOR PPG-AN00 真机执行。
- 备份默认未加密；其他 OEM SAF Provider、长期后台策略和外部 OpenAI Compatible 服务继续观察。
- 0.2.0 不提供提醒、循环待办或子任务；Todo 不直接进入 AI。
