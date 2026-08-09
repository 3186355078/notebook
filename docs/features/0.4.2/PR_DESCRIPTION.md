# 变更背景

Summary 数据本身是 Markdown，但 0.4.1 阅读页按普通字符串显示，导致 `#`、`**`、列表标记和代码围栏直接暴露。本 PR 将 Markdown 保留为数据格式，并为阅读态增加原生 Compose 富文本展示。

## 主要变化

- 新增轻量块级/行内 `MarkdownParser` 与可测试的文档模型；
- 新增 `WorkLogMarkdown` 原生 Compose Renderer；
- 支持 H1-H4、段落、粗体、斜体、列表、三级嵌套、任务项、引用、分隔线、行内/块代码和视觉链接；
- Summary 阅读态使用 Rich Markdown，编辑态继续显示原始 Markdown；
- 编辑页增加“编辑 / 预览”切换；
- 生成失败、stale、truncated、恢复原文、复制和 Markdown 导出保持原语义。

## 安全与数据

- 无 WebView、HTML/JavaScript 执行、远程图片加载或链接自动访问；
- 无第三方运行时依赖；
- Room version 2、Schema、Migration、backupFormatVersion 2 均不变化；
- Backup、复制和导出继续使用 Markdown 原文；
- Manifest 权限不增加。

## 无障碍与性能

- Heading 提供 TalkBack 标题语义，任务项提供只读状态描述；
- 使用现有 Material 3 主题适配深色、Dynamic Color 和大字体；
- `remember(markdown)` 缓存解析结果；
- JVM 覆盖 50KB 内容解析，Compose 覆盖长内容滚动和状态恢复。

## 验证

- JVM/Robolectric：319/319，45 suites，failures/errors/skipped 0/0/0；
- Parser 新增：30 项，包含 50KB 内容与安全降级；
- Connected Android Test：89/89，failures/errors/skipped 0/0/0；
- Markdown/Summary 真机定向：24/24；
- 横屏 NavigationRail：1/1；
- Lint Debug：0 errors、35 warnings（无新增阻断无障碍项）；
- Detekt、ktlint、Android Test 编译：通过；
- HONOR PPG-AN00，Android 16 / API 36：FATAL/ANR/OOM 0/0/0。

真机全量回归使用仓库自带受控 AI 测试服务，并在结束后关闭服务与 ADB reverse。测试未记录设备序列号、Authorization、Prompt 或工作正文。
