# WorkLog AI 0.3.0 设备验收报告

验收日期：2026-07-30

## 环境与范围

- 设备：HONOR PPG-AN00。
- 系统：Android 16 / API 36。
- 设备序列号不记录。
- 用户明确跳过 Android 10～13 第二台真机和模拟器专项兼容测试；该项未执行、不宣称通过。
- 0.3.0 只重构 UI/UX；Room version 2、Migration 1→2、Schema 1/2 和 backupFormatVersion 2 均未变化。

## 视觉验收

- 用户结论：视觉通过。
- Today、Todo、History、Summary、Settings 和 Data Management 的脱敏对比材料已完成。
- 浅色、深色、Dynamic Color、1.3×/1.5× 字体、横屏 NavigationRail 和 TalkBack 语义已纳入验收。
- 1.5× 字体、深色和 2800×1264 横屏组合下，NavigationRail 与主要 Today 内容正常显示，无主要操作遮挡。
- 本地响应式截图在人工检查后删除，不提交 Git。

详细视觉结论见 `docs/design/0.3.0/VISUAL_ACCEPTANCE.md`。

## 自动化基线

- JVM/Robolectric：249/249，42 suites。
- JVM failures/errors/skipped：0/0/0。
- Android 16 Debug Instrumentation：58/58。
- Instrumentation failures/errors/skipped：0/0/0。
- Debug Lint、Detekt、ktlint：通过。
- Release Lint：0 Error/Fatal；34 条非阻断 Warning。

## Release 构建与签名

- versionName：0.3.0。
- versionCode：3。
- R8/minification：开启。
- shrinkResources：开启。
- APK 签名：v2/v3 通过，v1 关闭。
- 签名算法：RSA 4096。
- 证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- AAB JAR 签名验证：通过。
- Release 内容扫描未发现 Android Test、受控 HTTP 地址、测试 Key、Keystore、私有 Windows 路径或 `0.3.0-dev`。

最终 APK/AAB 的文件大小和 SHA-256 由最终 HEAD 构建写入 Git 忽略目录中的 `release-artifacts/0.3.0/RELEASE_REPORT.txt`。

## 0.2.0 → 0.3.0 覆盖升级

- 0.2.0 Release APK SHA-256：`9861AA45E8CB01A010DB41C5A20961E5326F936E30603F3F8223AAA2B6A90149`。
- 0.2.0 与 0.3.0 使用同一签名证书。
- 0.2.0 versionCode 2 安装后，以 `adb install -r` 覆盖安装 0.3.0 versionCode 3。
- 覆盖安装成功，未卸载 0.2.0、未清除其应用数据。
- 升级后模拟 Todo、TEXT 工作记录、IMAGE、2×2 TABLE 和 History Todo 统计均可读取。
- Summary 周报/月报切换与周期导航正常；测试数据中没有已生成 Summary，空集合在升级后保持为空。
- 设置数据保持可读取；另以明显假值验证 Release Keystore 存取，重启后显示“API Key（已配置）”且不回显完整值。
- Room 未执行新 Migration，未出现 data integrity 或 Migration FATAL。

## Release UI 冒烟

| 区域 | 结果 |
| --- | --- |
| 冷启动、Today 与返回栈 | 通过 |
| Todo 展示、状态、完成进度与已同步标志 | 通过 |
| TEXT 自动保存与重启读取 | 通过 |
| IMAGE 卡片、图片说明与大图入口 | 通过 |
| TABLE 2×2、标题、列和横向结构 | 通过 |
| History 时间线、搜索入口与 Todo 统计 | 通过 |
| Summary 周/月切换、周期导航与生成入口 | 通过 |
| Settings 分组、Mock/真实 Provider 表单 | 通过 |
| Android Keystore 假 Key 保存、重启与不回显 | 通过 |
| Data Management、未加密提示和 API Key 排除提示 | 通过 |
| WorkManager 初始化与 JobScheduler 注册 | 通过 |
| 深色、1.5× 字体、横屏 NavigationRail | 通过 |

备份协议和 Restore 业务逻辑未在 0.3.0 改动；其 v1/v2 自动化与阶段 11 Release 真机基线继续通过。本轮在 Release 包中复核 Data Management、备份安全文案和恢复入口，没有重新执行完整损坏备份矩阵。

## 稳定性与性能

- FATAL：0。
- ANR：0。
- OOM：0。
- 未发现 SerializationException、Room integrity、Worker 初始化、ClassNotFound 或 NoSuchMethod 关键错误。
- 冒烟时应用 TOTAL PSS 约 73 MB，未见阻断卡顿。

## 已知限制

- Android 10～13 未做第二设备或模拟器专项验收。
- Dynamic Color 受设备壁纸和系统实现影响。
- 其他 OEM 的 SAF Provider 和长期后台行为仍需内部试用观察。
- 不提供待办提醒、循环待办或子任务。
- Todo 不直接进入 AI。
- 完整备份默认未加密。
