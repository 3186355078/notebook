# WorkLog AI 0.1.0 → 0.2.0 Migration 验收报告

## 发布包

- 0.1.0 APK SHA-256：`A5E775CF1BFEB87D480A2BC554109DA9C815822A1ECE897CB6BC11852B4AA274`
- 真机升级与 R8 冒烟使用的 0.2.0 预封版 APK SHA-256：`13EC3624EA7328A7717DC200B8AE8D4FF0CA0BAA0C8CF27CA1E93C7F8FA5A317`
- 最终分发 APK SHA-256：以 Git 忽略目录中的 `SHA256SUMS.txt` 和 `RELEASE_REPORT.txt` 为准。
- 签名证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`
- applicationId：`com.worklogai.app`
- 0.1.0 v1 备份 SHA-256：`94712F097BBAFE3833D42BD0D8B60E2FDAD5209B52E4EE46EFA893FD8E5B8A34`

## 数据摘要

报告只记录数量和状态，不记录工作正文、图片内容、API Key 或设备序列号。

| 项目 | 升级前 0.1.0 | 升级后 0.2.0 | 结果 |
| --- | ---: | ---: | --- |
| WorkEntry | 4 | 4 | 保留 |
| ContentBlock | 5 | 5 | 保留 |
| Attachment | 1 | 1 | 保留且图片可读 |
| WorkSummary | 2 | 2 | 保留 |
| Todo | 表不存在 | 0 | 新表为空且可用 |

## 迁移结论

- Room version：1 → 2。
- Migration：显式 `Migration(1, 2)`。
- destructive migration：0。
- [x] 0.1.0 Release 已安装，v1 备份 Preview 与 Restore 成功。
- [x] 升级前历史、图片、TABLE、周报/月报和 Keystore“已配置”状态已建立。
- [x] 覆盖安装成功，versionCode 从 1 升至 2，未卸载或清除应用数据。
- [x] TEXT、JPEG、透明 PNG、TABLE 和 Summary 保留。
- [x] editedContent、SourceHash、非敏感设置和 Keystore“已配置”状态保留。
- [x] 升级后 Todo 初始为空；随后正常创建、排序、切换状态、完成并记录。
- [x] WorkManager 初始化和 Scheduler reconcile 正常，无 WorkerFactory 初始化错误。
- [x] v1 备份恢复后 Todo 为空；v2 恢复 20 条 Todo，其中 2 条完成、1 条进行中、1 条绑定工作记录。
- [x] v2 备份 SHA-256：`A06E8EFDD4828AF5A7A8332E4DA8E256AA661C002ADA6803034DD1AFF847D434`。
- [x] 无 Migration FATAL、ANR 或 OOM。
