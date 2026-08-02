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

| 检查 | 结果 |
| --- | --- |
| 覆盖升级 | 0.3.0/code 3 使用同一签名覆盖安装到 0.3.1/code 4，安装成功且未清除应用数据 |
| Todo | 升级前后均为 10 条；3 条已完成、1 条进行中、1 条已取消，四级优先级样本保留 |
| 工作记录 | 升级前后均为 5 条，包含 TEXT、模拟 IMAGE 与 2×2 TABLE；随后为解绑专项主动删除 1 条模拟 TEXT |
| Summary | Mock 周报和月报各 1 份，升级后仍可读取 |
| 设置与密钥 | Mock Provider、自动周报开启/月报关闭及 API Key“已配置”状态保留；未读取或输出密钥明文 |
| WorkManager | `jobscheduler` 中存在应用任务证据，升级后调度可见 |
| 连续点击 | “查看记录”连续点击 10 次，只出现 1 个目标编辑页、1 次 linked block 定位和 1 次轻量高亮 |
| 返回栈 | 页面左上返回 1 次回到 Today；返回后再次点击可正常进入 |
| 生命周期 | 横竖屏切换未新增页面；进程回收后仅恢复原有单个编辑页，返回 1 次回到 Today |
| linked block 缺失 | 删除 Todo08 的纯模拟关联记录后，Todo 保留、“查看记录”入口消失，另外 2 条有效链接保留 |
| 无障碍 | “查看记录”48dp 单一语义、连续激活和重组覆盖由 60/60 Instrumentation 验证；阶段 14A TalkBack 验收结论保持有效 |
| UI 模式 | 阶段 14A 已通过浅色、深色、Dynamic Color、1.5× 字体和横屏；本轮 Release 专项复验竖屏、横屏与导航状态保持 |
| 稳定性 | FATAL / ANR / OOM / Room integrity error / SerializationException / Navigation error 均为 0 |

## 升级数据摘要

- WorkEntry/ContentBlock：Today 显示 5 条工作记录，升级后数量与内容类型保持。
- Attachment：模拟图片可读取，源文件来自脱敏测试画面，不含真实用户图片。
- WorkSummary：周报、月报各 1 份，升级后读取成功。
- Todo：10 条，其中升级时 3 条与工作记录关联；解绑专项后剩余 2 条有效关联。
- 设置：Mock Provider 开启，自动周报开启，自动月报关闭。
- API Key：仅记录“已配置”状态；测试假值未写入报告、日志或 Git。

## Release 冒烟边界

- 0.3.1 生产代码与通过 60/60 Instrumentation 的 PR #2 完全一致；版本准备提交仅修改版本号和发布文档。
- 本轮签名 Release 重点复验覆盖升级、Todo/记录/Summary/设置保留、导航幂等、linked block 解绑、横竖屏、进程回收和 WorkManager 调度。
- Backup v2、Restore、History、Search、通知深链、Markdown 与 Keystore 的完整业务矩阵沿用阶段 14A/0.3.0 已通过基线；最终 HEAD 安装后执行核心入口冒烟，不重复破坏性全量恢复矩阵。

## 数据与协议

- Room database version = 2。
- Schema 1/2 与 0.3.0 字节一致。
- 无新 Migration，无 destructive migration。
- backupFormatVersion = 2。

## 兼容性边界

用户明确跳过 Android 10～13 第二台真机和模拟器兼容测试。该项未执行、不宣称通过，作为 0.3.1 内部试用风险接受。
