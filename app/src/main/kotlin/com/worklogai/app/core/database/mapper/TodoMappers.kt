package com.worklogai.app.core.database.mapper

import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.model.DailyTodo

fun TodoEntity.toDomain(): DailyTodo =
    DailyTodo(
        id = id,
        scheduledDate = scheduledDate,
        title = title,
        note = note,
        priority = priority,
        status = status,
        sortOrder = sortOrder,
        completionNote = completionNote,
        linkedContentBlockId = linkedContentBlockId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
    )

fun DailyTodo.toEntity(): TodoEntity =
    TodoEntity(
        id = id,
        scheduledDate = scheduledDate,
        title = title,
        note = note,
        priority = priority,
        status = status,
        sortOrder = sortOrder,
        completionNote = completionNote,
        linkedContentBlockId = linkedContentBlockId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
    )
