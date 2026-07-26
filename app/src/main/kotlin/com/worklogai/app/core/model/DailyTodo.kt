package com.worklogai.app.core.model

import java.time.Instant
import java.time.LocalDate

enum class TodoPriority {
    URGENT,
    HIGH,
    MEDIUM,
    LOW,
}

enum class TodoStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
    CANCELED,
}

data class DailyTodo(
    val id: String,
    val scheduledDate: LocalDate,
    val title: String,
    val note: String?,
    val priority: TodoPriority,
    val status: TodoStatus,
    val sortOrder: Int,
    val completionNote: String?,
    val linkedContentBlockId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
)

const val TODO_TITLE_MAX_LENGTH = 200
const val TODO_NOTE_MAX_LENGTH = 4_000
const val TODO_COMPLETION_NOTE_MAX_LENGTH = 4_000

internal val TodoStatus.isIncomplete: Boolean
    get() = this == TodoStatus.NOT_STARTED || this == TodoStatus.IN_PROGRESS
