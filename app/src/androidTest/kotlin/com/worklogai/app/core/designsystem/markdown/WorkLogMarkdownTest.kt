package com.worklogai.app.core.designsystem.markdown

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class WorkLogMarkdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headingsHideControlMarkersAndExposeHeadingSemantics() {
        setMarkdown("# 工作总结\n\n## 本周完成\n\n### 技术工作\n\n#### 备注")

        composeRule.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        composeRule.onNode(hasText("本周完成") and headingMatcher()).assertIsDisplayed()
        composeRule.onNode(hasText("备注") and headingMatcher()).assertIsDisplayed()
    }

    @Test
    fun boldAndItalicHideControlMarkers() {
        setMarkdown("**重点工作** 与 *补充说明*")

        composeRule.onNodeWithText("重点工作 与 补充说明").assertIsDisplayed()
        composeRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }

    @Test
    fun unorderedListUsesReadableBullets() {
        setMarkdown("- 完成接口\n- 修复问题")

        composeRule.onAllNodesWithText("•", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("修复问题").assertIsDisplayed()
    }

    @Test
    fun orderedListKeepsOrdinalMarkers() {
        setMarkdown("1. 开发\n2. 测试")

        composeRule.onNodeWithText("1.", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2.", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun taskListIsReadOnlyAndAnnouncesState() {
        setMarkdown("- [x] 已完成\n- [ ] 未完成")

        composeRule.onNodeWithContentDescription("已完成").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("未完成").assertIsDisplayed()
        composeRule.onAllNodesWithText("[x]", substring = true).assertCountEquals(0)
    }

    @Test
    fun quoteHidesQuoteMarker() {
        setMarkdown("> 测试环境存在风险")

        composeRule.onNodeWithText("测试环境存在风险").assertIsDisplayed()
        composeRule.onAllNodesWithText(">", substring = true).assertCountEquals(0)
    }

    @Test
    fun codeBlockHidesFenceAndShowsCode() {
        setMarkdown("```kotlin\nval answer = 42\n```")

        composeRule.onNodeWithText("val answer = 42").assertIsDisplayed()
        composeRule.onAllNodesWithText("```", substring = true).assertCountEquals(0)
    }

    @Test
    fun inlineCodeShowsOnlyCodeValue() {
        setMarkdown("使用 `WorkManager` 调度")

        composeRule.onNodeWithText("使用  WorkManager  调度").assertIsDisplayed()
        composeRule.onAllNodesWithText("`", substring = true).assertCountEquals(0)
    }

    @Test
    fun linkShowsLabelWithoutOpeningOrExposingDestination() {
        setMarkdown("[OpenAI](https://example.com)")

        composeRule.onNodeWithText("OpenAI").assertIsDisplayed()
        composeRule.onAllNodesWithText("https://", substring = true).assertCountEquals(0)
    }

    @Test
    fun remoteImageShowsAlternativeWithoutNetworkAddress() {
        setMarkdown("![示意图](https://example.com/image.png)")

        composeRule.onNodeWithText("[图片：示意图]").assertIsDisplayed()
        composeRule.onAllNodesWithText("https://", substring = true).assertCountEquals(0)
    }

    @Test
    fun htmlIsVisibleTextAndNeverInterpreted() {
        setMarkdown("<script>alert('x')</script>")

        composeRule.onNodeWithText("<script>alert('x')</script>").assertIsDisplayed()
    }

    @Test
    fun rendersInDarkColorScheme() {
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { WorkLogMarkdown("## 深色标题\n\n> 深色引用") }
        }

        composeRule.onNodeWithText("深色标题").assertIsDisplayed()
        composeRule.onNodeWithText("深色引用").assertIsDisplayed()
    }

    @Test
    fun rendersWithDynamicLikeColorScheme() {
        composeRule.setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Color(0xFF326A72)),
            ) {
                WorkLogMarkdown("[链接](https://example.com) 与 `代码`")
            }
        }

        composeRule.onNodeWithText("链接 与  代码 ").assertIsDisplayed()
    }

    @Test
    fun supportsOnePointFiveFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MaterialTheme { WorkLogMarkdown("## 大字体\n\n- 列表仍可读") }
            }
        }

        composeRule.onNodeWithText("大字体").assertIsDisplayed()
        composeRule.onNodeWithText("列表仍可读").assertIsDisplayed()
    }

    @Test
    fun parsedReadingSurvivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { MaterialTheme { WorkLogMarkdown("## 恢复后标题\n\n**内容**") } }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("恢复后标题").assertIsDisplayed()
        composeRule.onNodeWithText("内容").assertIsDisplayed()
        composeRule.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }

    @Test
    fun longDocumentCanComposeWithinScrollableViewport() {
        val markdown =
            buildString {
                repeat(200) { append("## 第${it + 1}节\n\n- 内容 ${it + 1}\n\n") }
            }
        composeRule.setContent {
            MaterialTheme { WorkLogMarkdown(markdown, Modifier.height(600.dp)) }
        }

        composeRule.onNodeWithText("第1节").assertIsDisplayed()
    }

    private fun setMarkdown(markdown: String) {
        composeRule.setContent { MaterialTheme { WorkLogMarkdown(markdown) } }
    }

    private fun headingMatcher(): SemanticsMatcher =
        SemanticsMatcher("has heading semantics") { node -> SemanticsProperties.Heading in node.config }
}
