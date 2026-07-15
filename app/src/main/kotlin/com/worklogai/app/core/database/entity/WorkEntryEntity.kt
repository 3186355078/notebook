package com.worklogai.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "work_entries",
    indices = [Index(value = ["entryDate"], unique = true)],
)
data class WorkEntryEntity(
    @PrimaryKey val id: String,
    val entryDate: LocalDate,
    val title: String?,
    val allowAiProcessing: Boolean,
    val isDeleted: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
