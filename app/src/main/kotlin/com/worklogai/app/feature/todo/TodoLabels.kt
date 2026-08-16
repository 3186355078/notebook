package com.worklogai.app.feature.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.worklogai.app.core.designsystem.component.WorkLogStatusChip
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus

val TodoPriority.chineseLabel: String
    get() =
        when (this) {
            TodoPriority.URGENT -> "紧急"
            TodoPriority.HIGH -> "高"
            TodoPriority.MEDIUM -> "中"
            TodoPriority.LOW -> "低"
        }

val TodoStatus.chineseLabel: String
    get() =
        when (this) {
            TodoStatus.NOT_STARTED -> "未开始"
            TodoStatus.IN_PROGRESS -> "进行中"
            TodoStatus.DONE -> "已完成"
            TodoStatus.CANCELED -> "已取消"
        }

internal fun TodoPriority.higher(): TodoPriority? =
    when (this) {
        TodoPriority.URGENT -> null
        TodoPriority.HIGH -> TodoPriority.URGENT
        TodoPriority.MEDIUM -> TodoPriority.HIGH
        TodoPriority.LOW -> TodoPriority.MEDIUM
    }

internal fun TodoPriority.lower(): TodoPriority? =
    when (this) {
        TodoPriority.URGENT -> TodoPriority.HIGH
        TodoPriority.HIGH -> TodoPriority.MEDIUM
        TodoPriority.MEDIUM -> TodoPriority.LOW
        TodoPriority.LOW -> null
    }

@Composable
internal fun PriorityBadge(priority: TodoPriority) {
    val color = todoPriorityColor(priority)
    WorkLogStatusChip(
        label = priority.chineseLabel,
        containerColor = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}

@Composable
internal fun todoPriorityColor(priority: TodoPriority): Color =
    when (priority) {
        TodoPriority.URGENT -> MaterialTheme.colorScheme.error
        TodoPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        TodoPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        TodoPriority.LOW -> MaterialTheme.colorScheme.outline
    }
