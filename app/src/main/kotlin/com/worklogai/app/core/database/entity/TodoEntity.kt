package com.worklogai.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "todo_items",
    foreignKeys = [
        ForeignKey(
            entity = ContentBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedContentBlockId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["scheduledDate"]),
        Index(value = ["scheduledDate", "status"]),
        Index(value = ["scheduledDate", "priority", "sortOrder"]),
        Index(value = ["linkedContentBlockId"]),
    ],
)
data class TodoEntity(
    @PrimaryKey val id: String,
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
