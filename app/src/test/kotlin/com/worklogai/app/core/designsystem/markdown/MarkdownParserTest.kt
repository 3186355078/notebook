package com.worklogai.app.core.designsystem.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class MarkdownParserTest {
    @Test fun `parses h1`() = assertHeading("# 标题", 1, "标题")

    @Test fun `parses h2`() = assertHeading("## 标题", 2, "标题")

    @Test fun `parses h3`() = assertHeading("### 标题", 3, "标题")

    @Test fun `parses h4`() = assertHeading("#### 标题", 4, "标题")

    @Test
    fun `parses paragraph lines into one readable paragraph`() {
        val block = parse("第一行\n第二行").blocks.single() as MarkdownBlock.Paragraph
        assertEquals("第一行 第二行", block.content.plainText())
    }

    @Test
    fun `parses bold`() {
        val content = paragraph("普通 **重点**")
        assertTrue(content.any { it is MarkdownInline.Strong })
        assertEquals("普通 重点", content.plainText())
    }

    @Test
    fun `parses italic`() {
        val content = paragraph("普通 *补充*")
        assertTrue(content.any { it is MarkdownInline.Emphasis })
        assertEquals("普通 补充", content.plainText())
    }

    @Test
    fun `parses bold italic`() {
        val strong = paragraph("***重要补充***").single() as MarkdownInline.Strong
        assertTrue(strong.children.single() is MarkdownInline.Emphasis)
    }

    @Test
    fun `parses unordered list`() {
        val list = parse("- A\n- B").blocks.single() as MarkdownBlock.ListBlock
        assertEquals(listOf("A", "B"), list.items.map { it.content.plainText() })
        assertTrue(list.items.all { it.ordinal == null })
    }

    @Test
    fun `parses ordered list without deleting numbers`() {
        val list = parse("1. A\n2. B").blocks.single() as MarkdownBlock.ListBlock
        assertEquals(listOf(1, 2), list.items.map { it.ordinal })
        assertTrue(parse("1. A\n2. B").toPlainText().contains("2. B"))
    }

    @Test
    fun `parses nested list up to three levels`() {
        val list = parse("- A\n  - B\n    - C\n        - D").blocks.single() as MarkdownBlock.ListBlock
        assertEquals(listOf(0, 1, 2, 3), list.items.map { it.depth })
    }

    @Test
    fun `parses checked task`() {
        val item = listItem("- [x] 完成")
        assertEquals(true, item.checked)
        assertEquals("完成", item.content.plainText())
    }

    @Test
    fun `parses unchecked task`() {
        val item = listItem("- [ ] 待办")
        assertEquals(false, item.checked)
        assertEquals("待办", item.content.plainText())
    }

    @Test
    fun `parses quote without marker`() {
        val quote = parse("> 风险提示").blocks.single() as MarkdownBlock.Quote
        assertEquals("风险提示", quote.content.plainText())
    }

    @Test
    fun `parses divider`() = assertTrue(parse("---").blocks.single() is MarkdownBlock.Divider)

    @Test
    fun `parses inline code`() {
        val code = paragraph("使用 `WorkManager`").filterIsInstance<MarkdownInline.InlineCode>().single()
        assertEquals("WorkManager", code.value)
    }

    @Test
    fun `parses fenced code and language`() {
        val code = parse("```kotlin\nval answer = 42\n```").blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.code)
    }

    @Test
    fun `parses link as visible label plus inert destination`() {
        val link = paragraph("[OpenAI](https://example.com)").single() as MarkdownInline.Link
        assertEquals("OpenAI", link.children.plainText())
        assertEquals("https://example.com", link.destination)
    }

    @Test
    fun `consumes balanced parentheses in inert link destination`() {
        val document = parse("[Safe label](javascript:alert(1))")
        val link = paragraph("[Safe label](javascript:alert(1))").single() as MarkdownInline.Link

        assertEquals("Safe label", document.toPlainText())
        assertEquals("javascript:alert(1)", link.destination)
    }

    @Test
    fun `plain text stays a paragraph`() = assertEquals("本周完成了 A、B、C。", parse("本周完成了 A、B、C。").toPlainText())

    @Test
    fun `malformed markdown remains readable and never crashes`() {
        val document = parse("###标题\n****\n-\n**")
        assertTrue(document.toPlainText().contains("###标题"))
        assertTrue(document.toPlainText().contains("-"))
        assertTrue(document.toPlainText().contains("**"))
    }

    @Test fun `empty string produces empty document`() = assertTrue(parse("").blocks.isEmpty())

    @Test
    fun `multiple blank lines do not create empty blocks`() {
        assertEquals(2, parse("A\n\n\nB").blocks.size)
    }

    @Test fun `supports Chinese content`() = assertEquals("完成接口联调", parse("完成接口联调").toPlainText())

    @Test
    fun `supports English content`() {
        assertEquals("Finished integration", parse("Finished integration").toPlainText())
    }

    @Test
    fun `supports mixed Chinese and English`() {
        assertEquals("完成 WorkManager 测试", parse("完成 WorkManager 测试").toPlainText())
    }

    @Test
    fun `HTML and JavaScript are treated as text`() {
        val html = "<script>alert('x')</script>"
        assertEquals(html, parse(html).toPlainText())
    }

    @Test
    fun `remote image is represented by alternative text only`() {
        val image =
            paragraph("![架构图](https://example.com/private.png)")
                .single() as MarkdownInline.ImageAlternative
        assertEquals("架构图", image.alternative)
        assertFalse(
            parse("![架构图](https://example.com/private.png)")
                .toPlainText()
                .contains("https://"),
        )
    }

    @Test
    fun `unclosed code fence is rendered safely as code`() {
        val code = parse("```\nunfinished").blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("unfinished", code.code)
    }

    @Test
    fun `escaped control markers remain literal text`() {
        assertEquals("*不是斜体*", parse("\\*不是斜体\\*").toPlainText())
    }

    @Test
    fun `parses fifty kilobytes without pathological delay`() {
        val markdown =
            buildString {
                while (length < 50 * 1024) append("## 小节\n\n- **完成** WorkManager 测试\n\n")
            }
        lateinit var document: MarkdownDocument
        val elapsed = measureTimeMillis { document = parse(markdown) }
        assertTrue(document.blocks.isNotEmpty())
        assertTrue("50KB Markdown parsed in ${elapsed}ms", elapsed < 2_000)
    }

    private fun parse(value: String) = MarkdownParser.parse(value)

    private fun paragraph(value: String): List<MarkdownInline> =
        (parse(value).blocks.single() as MarkdownBlock.Paragraph).content

    private fun listItem(value: String): MarkdownListItem =
        (parse(value).blocks.single() as MarkdownBlock.ListBlock).items.single()

    private fun assertHeading(
        markdown: String,
        level: Int,
        text: String,
    ) {
        val heading = parse(markdown).blocks.single() as MarkdownBlock.Heading
        assertEquals(level, heading.level)
        assertEquals(text, heading.content.plainText())
        assertNotNull(heading.content)
    }
}
