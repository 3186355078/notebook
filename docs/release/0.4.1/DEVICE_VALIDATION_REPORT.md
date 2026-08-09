# WorkLog AI 0.4.1 设备验收报告

版本：`0.4.1`（versionCode 6）

设备：HONOR PPG-AN00

系统：Android 16 / API 36

设备序列号：不记录

## 构建与回归

| 检查 | 结果 |
| --- | --- |
| JVM/Robolectric | 286/286，44 suites，failures/errors/skipped = 0 |
| Android Instrumentation | 67/67，failures/errors/skipped = 0 |
| 横屏 NavigationRail 专项 | 1/1，通过 |
| Lint Debug / Release | 0 errors、35 warnings；无新增 UI/Accessibility blocker |
| Detekt / ktlint | 通过 |
| R8 / shrinkResources | 启用并构建通过 |
| FATAL / ANR / OOM | 0 / 0 / 0 |

Instrumentation 使用独立 Debug applicationId 执行；签名 Release 随后完成覆盖升级、UI、历史补录、Todo 导航和 Backup v2 人工专项。

## 0.4.0 → 0.4.1 覆盖升级

- 正式 0.4.0 APK SHA-256 为 `AEC7FC0D7A9C38400A28830A664E8D8ADB899E1C6845733A1B58946356B90642`，versionCode 5，applicationId 为 `com.worklogai.app`。
- 0.4.0 与 0.4.1 均由同一非 Debug 证书签名，证书 SHA-256 为 `15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。
- 使用 `adb install -r` 覆盖安装 0.4.1/code 6 成功；首次安装时间保持不变，未卸载、未清除数据，也未执行新 Room Migration。
- 升级前建立的 Today TEXT、IMAGE、TABLE、8 条 Todo、linked Todo、既有周报和设置在升级后可继续读取；History、Search 和 Summary 状态正常。
- API Key 仅确认“已配置”状态保持，未读取、显示或记录明文；WorkManager 在系统状态中保持注册并可正常 reconcile。

## UI 与响应式

- 浅色、深色与 Android Dynamic Color 下完成 Today、Todo、History、Summary、Settings 和 Data Management 检查。
- 深色模式未发现默认紫色泄漏；Surface、Container、禁用控件、状态标签和错误语义保持可读。
- 1.5× 字体下核心导航和操作仍可见，正文及列表可滚动，无关键控件遮挡。
- 横屏采用 NavigationRail 和最大 840dp 内容宽度，未发现横向溢出、过窄正文列或无意义大面积留白。
- Todo 状态与优先级不只依赖颜色；关键点击区域保持 48dp，TalkBack 排序与“查看记录”语义由 67 项真机测试覆盖。

## 历史补录与搜索

- History 的“补记录”入口可打开 Material 3 DatePicker；2026-08-10 等未来日期在 2026-08-09 验收时为禁用状态。
- 选择没有记录的 2026-08-07 可进入合法 Empty Editor；直接返回后 History 不出现该日期，证明未创建空 WorkEntry。
- 再次进入该日期创建 `release_backfill_041_text`，首次有效文本保存后 History 立即出现该日期，Search 在 300ms 防抖后命中，返回栈只需一次。
- IMAGE Cancel/Success、TABLE、targetDate 生命周期以及空日期批量不入库由 67 项 Instrumentation 与 286 项 JVM/Robolectric 回归覆盖；本轮未改变历史补录业务语义。

## Todo 导航

- 在最终签名候选 APK 中完成 Todo 并同步工作记录后，“查看记录”入口可定位到对应工作记录页。
- 将入口滚动到可点击区域后连续点击 10 次，只出现一个工作记录目标页；返回一次即回到应用主导航。
- 未发现重复 Destination、重复页面、导航异常、FATAL、ANR 或 OOM。

## Backup v2

- 0.4.1 Release 创建的测试备份为 formatVersion 2，包含 13 条日志、18 个内容块、13 条待办、4 个附件和 5 份总结。
- ZIP 包含 `data/todo_items.json` 及既有数据文件和附件；文件名中未发现 API Key 项，API Key 明确不进入备份。
- Preview 正确显示上述数量和“API Key 不会从备份恢复，也不会被删除”；使用同一备份执行恢复后应用正常返回，业务数据可继续访问。
- 恢复过程 Logcat：Room integrity error、SerializationException、FATAL、ANR、OOM 均为 0。

## 签名、包内容与稳定性

- APK v2/v3 签名通过，v1 未启用；AAB 通过 `jarsigner`。当前环境无独立可执行 bundletool CLI，bundletool validate 未执行。
- Release applicationId 为 `com.worklogai.app`；`debuggable=false`、`usesCleartextTraffic=false`、`allowBackup=false`。
- APK/AAB 扫描未发现 `0.4.1-dev`、`.debug` applicationId、Android Test 类、测试/真实 API Key、Keystore、密码、私有路径或测试备份。
- R8 mapping、seeds、usage、configuration 只归档到 Git 忽略的本地 Release Artifact 目录。

## 已知限制

- Android 10～13 第二台真机或模拟器按用户明确要求未执行，不宣称通过。
- Dynamic Color 会受壁纸影响；其他 OEM SAF Provider 与长期后台调度仍需内部观察。
- 外部 OpenAI Compatible 服务的响应结构和时延可能不同；`LocalClipboardManager` 仍有上游弃用警告。
- 完整备份默认未加密；当前无 Todo 提醒、循环 Todo 或子任务。
