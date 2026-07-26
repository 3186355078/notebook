package com.worklogai.app.core.export

import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus

internal fun StringBuilder.appendTodo(todo: DailyTodo) {
    val marker =
        when (todo.status) {
            TodoStatus.DONE -> "[x]"
            TodoStatus.CANCELED -> "[-]"
            TodoStatus.NOT_STARTED,
            TodoStatus.IN_PROGRESS,
            -> "[ ]"
        }
    val priority =
        when (todo.priority) {
            TodoPriority.URGENT -> "紧急"
            TodoPriority.HIGH -> "高"
            TodoPriority.MEDIUM -> "中"
            TodoPriority.LOW -> "低"
        }
    appendLine("- $marker [$priority] ${todo.title.markdownInline()}")
    todo.completionNote?.takeIf(String::isNotBlank)?.let { note ->
        appendLine("  - 完成备注：${note.markdownInline()}")
    }
}

private fun String.markdownInline(): String =
    trim()
        .replace("\r\n", " ")
        .replace("\n", " ")
        .replace("\r", " ")
        .let { value ->
            buildString {
                value.forEach { character ->
                    if (character in MARKDOWN_INLINE_SPECIAL_CHARACTERS) append('\\')
                    append(character)
                }
            }
        }

private val MARKDOWN_INLINE_SPECIAL_CHARACTERS =
    setOf(
        '\\',
        '`',
        '*',
        '_',
        '{',
        '}',
        '[',
        ']',
        '<',
        '>',
        '(',
        ')',
        '#',
        '+',
        '-',
        '.',
        '!',
        '|',
    )
