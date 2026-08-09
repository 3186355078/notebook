# WorkLog AI 0.4.2 设备验收报告

## 环境

- 设备：HONOR PPG-AN00。
- 系统：Android 16 / API 36。
- 设备序列号：不记录。
- Release applicationId：`com.worklogai.app`。
- 升级路径：正式签名 0.4.1/code 6 → 0.4.2/code 7 覆盖安装。
- 签名证书 SHA-256：`15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801`。

## 自动化结果

- JVM/Robolectric：320/320，45 suites，failures/errors/skipped 为 0/0/0。
- Connected Android Test：89/89，failures/errors/skipped 为 0/0/0。
- Markdown/Summary 定向：24/24。
- 横屏 NavigationRail 专项：1/1。
- Lint Debug/Release：0 errors、35 warnings；Detekt 与 ktlint 通过。
- 已知编译警告：`LocalClipboardManager` 为上游弃用 API，本版本未扩大范围替换。

## 覆盖升级与旧 Summary

- 0.4.1 正式 APK 的 SHA-256、versionName/versionCode、applicationId 和签名证书均在安装前核对。
- `adb install -r` 覆盖安装成功，未卸载、未清除数据，Room version 保持 2，未执行新 Migration。
- 升级前准备了标准 Markdown、普通文本、editedContent、stale、20KB+ 长内容和畸形 Markdown 六类模拟 Summary。
- 升级后无需重新生成或数据迁移：标准 Markdown 直接富文本显示，普通文本作为段落显示，editedContent 继续优先，stale 状态和旧内容同时保留，畸形内容安全降级。
- API Key 只验证“已配置”状态，不读取、不记录、不回显明文。

## Markdown 阅读专项

- H1–H4：层级正确，阅读态不显示 `#` 控制符。
- 粗体/斜体：样式正确，不显示 `**` 或 `*` 控制符。
- 列表：无序、有序、一层嵌套及 Task List 均保持顺序和缩进；Task List 只读，不暴露为可操作 Todo。
- 引用：显示轻量 Tonal Surface 和 Accent Bar，不显示 `>` 控制符。
- 代码：行内代码和 fenced code 使用等宽字体；代码块支持横向滚动。
- 分隔线：`---` 渲染为 Divider。
- Link：只显示链接文字，不可点击；带嵌套括号的 destination 完整消费，不泄漏尾随标记。
- Remote Image：不请求网络，仅显示安全替代文本。
- HTML/JavaScript：作为普通文本或不可执行 destination 处理，不执行脚本，不启动浏览器。
- malformed Markdown：不崩溃，无法识别部分按普通文本显示。

## Summary 业务回归

- 阅读态使用既有 `editedContent ?: originalContent` 最终内容选择规则。
- Edit 显示完整 Markdown 源码；Preview 使用同一 Rich Renderer；保存后阅读态即时刷新。
- Restore Original 恢复生成原文并立即重新渲染。
- Copy 与 Export Markdown 继续输出 Markdown 原文；导出文件不是 HTML 或渲染树。
- Failed + old content、stale、truncated 与 generating 状态保持原业务语义。
- Backup v2 中仅保存原始 Summary 字段；Restore 后 Rich Renderer 正常，backupFormatVersion 保持 2。

## 视觉、无障碍与性能

- 浅色、深色、Dynamic Color、1.5× 字体和横屏均完成检查。
- Heading 使用 heading semantics；TalkBack 不朗读 Markdown 控制符，列表顺序自然，Link 不误报为 Button。
- 52KB 模拟 Markdown 完成首次打开、滚动、Edit→Preview、横竖屏及前后台切换检查；无明显阻塞。该结论为真机体验检查，不是严格性能 benchmark。
- Today、Todo、History、历史补录、Search、Settings、Data Management、Backup 和 WorkManager 完成快速冒烟。
- Todo“查看记录”导航幂等与 History“补记录”空日期不建库规则保持正常。

## 稳定性与安全

- FATAL / ANR / OOM：0 / 0 / 0。
- Room integrity errors / SerializationException：0 / 0。
- 未新增 Manifest 权限；未使用 WebView；未执行 HTML/JavaScript；未加载远程 Markdown 图片。
- 测试数据均为明显模拟内容；报告不包含 API Key、工作正文、私有路径或设备序列号。

## 已知限制

- Markdown Table、Footnote、可点击 Link、远程图片、Code Block Copy 和复杂语法高亮暂不支持。
- Android 10～13 未进行第二台真机或模拟器专项，不宣称通过。
- Dynamic Color 受壁纸影响；其他 OEM SAF Provider 与长期后台行为仍需内部观察。
