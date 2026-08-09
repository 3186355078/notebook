# Summary Markdown 富文本阅读模式

## 目标与数据模型

WorkLog AI 继续把 Markdown 作为 Summary 的唯一内容格式：AI 生成、`generatedContent`/`editedContent` 存储、用户编辑、复制和 `.md` 导出均保留原文。正常阅读时，界面才把最终选择的 `editedContent ?: generatedContent` 解析为富文本；渲染结果不写入数据库，也不参与 SourceHash。

```text
AI / 编辑器 → Markdown 原文 → Repository / Backup / Export
                              ↓
                       阅读态 Parser
                              ↓
                    原生 Compose Renderer
```

Room database version 继续为 2，Schema 1/2、Migration 和 backupFormatVersion 2 均不变化。

## Parser

`MarkdownParser` 使用两阶段轻量解析：

1. 按行识别标题、段落、列表、引用、分隔线和 fenced code block；
2. 在可包含富文本的块中逐字符识别粗体、斜体、行内代码、链接和图片替代文字。

实现没有使用全篇巨型正则，也不引入第三方 Markdown/HTML/WebView 依赖。不规范或未闭合语法按普通文本或安全代码块降级，解析错误不会修改或记录 Summary 正文。

## 支持语法

- `#`～`####` 标题，阅读时使用受控的 Material 3 标题层级；
- 普通段落；
- `**粗体**`、`*斜体*` 和 `***粗斜体***`；
- 无序、有序列表；
- 最多三级视觉缩进的嵌套列表，更深层级收敛为第三级；
- `- [x]` / `- [ ]` 只读任务列表；
- `>` 引用；
- `---` 分隔线；
- 行内代码和 fenced code block；
- 链接的可读标签；
- Markdown 图片的替代文字。

第一版不实现 Markdown 表格、脚注、复杂语法高亮、远程图片、可交互 checkbox 或可点击链接。无法识别的语法按普通文本展示。

## Renderer 与视觉

`WorkLogMarkdown` 使用原生 Compose 和现有 WorkLog Design System：

- 标题复用 `titleLarge` / `titleMedium` / `titleSmall`，不会与页面 Top Bar 竞争；
- 正文使用 `bodyLarge` 和舒适行高；
- 内容直接位于页面 Surface，通过 Typography、间距和 Divider 建立层级，不为每个小节创建 Card；
- 引用使用轻量 Tonal Surface 与左侧品牌色条；
- 代码使用 Monospace 和 `surfaceContainerHigh`，代码块允许横向滚动；
- 所有颜色来自 `MaterialTheme.colorScheme`，适配浅色、深色和 Dynamic Color；
- 解析结果由 `remember(markdown)` 缓存，普通重组不会重复解析全文。

## 阅读、编辑与预览

- **阅读**：渲染最终 Summary Markdown，不显示 `#`、`**` 等控制符；
- **编辑**：`OutlinedTextField` 保留并编辑完整 Markdown 原文；
- **预览**：编辑过程中切换到同一个 `WorkLogMarkdown`，不创建第二套格式；
- **保存**：仍只把 Markdown Draft 写入 `editedContent`；
- **恢复 AI 原文**：沿用原业务流程，成功后由相同 Renderer 重新展示；
- **复制**：保持 0.4.1 既有语义，复制 Markdown 原文；
- **导出**：继续导出 Markdown，不导出 HTML、AnnotatedString 或 Compose 展示文本。

Generating、Failed、Stale 和 Truncated 状态保持独立。重新生成失败但存在旧内容时，错误提示与旧 Rich Markdown 同时显示。

## Backup 与兼容性

Backup v2 继续保存 WorkSummary 的原始内容字段。恢复后不需要数据迁移，阅读页根据恢复出的原文重新解析。无 Markdown 的旧 Summary 会显示为普通段落；不规范 Markdown 会安全降级，不会导致页面崩溃。

## 安全

- HTML 和 `<script>` 作为普通文本显示，不执行 HTML 或 JavaScript；
- 不使用 WebView；
- 链接只做视觉表达，不自动打开 URL；
- Markdown 图片只显示替代文字，不请求远程资源；
- Renderer 不新增网络、存储或其他 Manifest 权限；
- Parser 错误不输出 Summary 正文、路径、URI 或 AI 凭据；
- API Key、WorkManager、Summary Prompt、SourceHash 和 Backup 安全语义均不变化。

## 无障碍与响应式

- Heading 使用 Compose `heading()` 语义，TalkBack 朗读标题内容而非井号；
- 任务项朗读“已完成”或“未完成”，但不声明为可操作 checkbox；
- 不可点击链接不声明为 Button；
- 列表顺序与视觉顺序一致；
- 1.3×/1.5× 字体下不裁剪，页面保持纵向滚动；
- Code Block 是唯一允许横向滚动的内容；
- 深色和 Dynamic Color 均使用主题语义色，不使用硬编码纯黑或纯白。

## 性能与测试

JVM 测试覆盖块级/行内语法、中文/英文混排、畸形输入、HTML 与远程图片隔离，以及 50KB Markdown 解析。Compose 测试覆盖语法标记隐藏、Heading 语义、列表、任务、引用、代码、深色、Dynamic Color、1.5× 字体、长文滚动、编辑/预览、状态恢复、stale 和失败后旧内容保留。

性能边界：普通、20KB 与 50KB Summary 不在每次重组重新解析；50KB Parser 测试使用宽松的 2 秒上限防止出现病态复杂度。最终回归为 JVM/Robolectric 319/319、Connected 89/89，HONOR Android 16 冷启动及测试过程中 FATAL/ANR/OOM 均为 0。
