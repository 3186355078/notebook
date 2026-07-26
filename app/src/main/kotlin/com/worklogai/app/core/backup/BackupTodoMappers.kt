package com.worklogai.app.core.backup

import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate

internal fun TodoEntity.toBackup(): BackupTodoItem =
    BackupTodoItem(
        id = id,
        scheduledDate = scheduledDate.toString(),
        title = title,
        note = note,
        priority = priority.name,
        status = status.name,
        sortOrder = sortOrder,
        completionNote = completionNote,
        linkedContentBlockId = linkedContentBlockId,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        completedAt = completedAt?.toString(),
    )

internal fun BackupTodoItem.toEntity(): TodoEntity =
    TodoEntity(
        id = id,
        scheduledDate = LocalDate.parse(scheduledDate),
        title = title,
        note = note,
        priority = TodoPriority.valueOf(priority),
        status = TodoStatus.valueOf(status),
        sortOrder = sortOrder,
        completionNote = completionNote,
        linkedContentBlockId = linkedContentBlockId,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        completedAt = completedAt?.let(Instant::parse),
    )
