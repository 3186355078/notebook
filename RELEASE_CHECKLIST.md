# WorkLog AI 0.3.1 内部热修复发布检查清单

## GitHub 与 Git

- [x] PR #2 已完成导航幂等、UI 精修、数据协议和敏感信息审查。
- [x] Draft PR #2 已转为 Ready，并以普通 Merge Commit 合入 `main`。
- [x] PR Base 为 `main`，Head 为 `feature/v0.3.0-ui-redesign`；未 force push、未自动删除分支。
- [x] PR #2 merge commit：`87f194d38b3f07fa389eb9d00348f8ffd17010df`。
- [x] `v0.1.0-internal`、`v0.2.0-internal`、`v0.3.0-internal` 未移动。
- [ ] 0.3.1 最终 Release 提交已推送到远程 `main`。
- [ ] `v0.3.1-internal` annotated Tag 指向最终 HEAD 并仅推送该 Tag。
- [ ] 最终提交前工作区、Git 空白和敏感信息扫描通过。

## 版本与测试

- [x] versionName：0.3.1；versionCode：4。
- [x] PR 合并前 JVM/Robolectric：254/254，42 suites，failures/errors/skipped 均为 0。
- [x] PR 合并前 Android 16 HONOR Debug Instrumentation：60/60，failures/errors/skipped 均为 0。
- [x] Debug Lint、Detekt、ktlint 通过。
- [x] main 版本转正后的 Debug 与 connected 全量回归通过：254/254 JVM，60/60 Instrumentation，failures/errors/skipped 均为 0。
- [ ] Release Lint、R8 和资源压缩构建通过。
- [ ] 0.3.0→0.3.1 覆盖安装、连续点击 10 次和最终 Release 核心冒烟通过。
- [ ] 最终 HEAD 的签名构建通过；签名配置仅使用仓库外 Keystore 和当前交互进程环境。

## 数据协议

- [x] Room database version：2；0.3.1 不新增 Migration。
- [x] Schema 1、Schema 2 均保留且字节未变化。
- [x] Schema 1 SHA-256：`4BF57A358800911B18E10DF66C105D4F513AC7DDC16F89E7CF9F00CE59E32E4C`。
- [x] Schema 2 SHA-256：`944C04F92F7633FCFCA2DB407588B850B981AC7B2E4541A0498505330D40862D`。
- [x] 显式 Migration 1→2 保持不变；未使用 destructive migration。
- [x] backupFormatVersion：2；v1/v2 Preview 与 Restore 语义保持不变。

## 签名与 Release

- [x] Release Keystore 位于仓库外，alias 为 `worklog-ai-release`，算法 RSA 4096。
- [x] 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- [x] 用户已确认 Keystore 在可信位置安全备份。
- [ ] APK v2/v3 签名、非 Debug 证书和 AAB `jarsigner` 验证通过。
- [ ] 最终 HEAD 的 APK/AAB SHA-256 已记录在 Git 忽略目录的 `SHA256SUMS.txt` 和 `RELEASE_REPORT.txt`。
- [ ] 最终 APK/AAB、SHA-256 和 R8 mapping 已归档到 Git 忽略目录且未进入 GitHub。

## Manifest 与安全

- [x] Release `debuggable=false`、R8 和资源压缩配置保持启用。
- [x] `usesCleartextTraffic=false`、`allowBackup=false`。
- [x] 应用源码主动声明仅 INTERNET、POST_NOTIFICATIONS；合并 Manifest 中仅保留 WorkManager 正常运行所需的依赖权限。
- [x] 不含传统存储、全部文件、相机、位置、通讯录、电话或精确闹钟权限。
- [ ] APK/AAB 不含 Debug 入口、受控 HTTP 地址、测试 Key、用户路径、测试备份或 `0.3.1-dev`。

## 发布材料

- [x] `VISUAL_ACCEPTANCE.md` 已记录浅色、深色、Dynamic Color、大字体、横屏和 TalkBack 结论。
- [x] CHANGELOG 与 INTERNAL_TESTING_GUIDE 已更新到 0.3.1。
- [x] PRIVACY 已复核；本版本未改变数据、AI 或备份隐私边界。
- [x] 最终 HEAD 的 SHA256SUMS 和 RELEASE_REPORT 已由封版脚本生成到 Git 忽略目录。
- [ ] 0.3.1 Device Validation Report 已完成，且不含设备序列号、密钥或工作正文。

## 风险接受

> 用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行，不宣称通过，作为 0.3.1 内部试用风险接受。

- 当前完整矩阵只在 Android 16 / API 36 的 HONOR PPG-AN00 真机执行。
- Dynamic Color 受壁纸和系统实现影响；其他 OEM SAF Provider、长期后台策略和外部 OpenAI Compatible 服务继续观察。
- 不提供提醒、循环待办或子任务；Todo 不直接进入 AI；备份默认未加密。
