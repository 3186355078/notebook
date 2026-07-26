package com.worklogai.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.worklogai.app.core.database.converter.RoomTypeConverters
import com.worklogai.app.core.database.converter.TodoTypeConverters
import com.worklogai.app.core.database.dao.AttachmentDao
import com.worklogai.app.core.database.dao.ContentBlockDao
import com.worklogai.app.core.database.dao.TodoDao
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.dao.WorkSummaryBackupDao
import com.worklogai.app.core.database.dao.WorkSummaryDao
import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity

@Database(
    entities = [
        WorkEntryEntity::class,
        ContentBlockEntity::class,
        AttachmentEntity::class,
        WorkSummaryEntity::class,
        TodoEntity::class,
    ],
    version = WorkLogDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class, TodoTypeConverters::class)
abstract class WorkLogDatabase : RoomDatabase() {
    abstract fun workEntryDao(): WorkEntryDao

    abstract fun contentBlockDao(): ContentBlockDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun workSummaryDao(): WorkSummaryDao

    abstract fun workSummaryBackupDao(): WorkSummaryBackupDao

    abstract fun todoDao(): TodoDao

    companion object {
        const val DATABASE_NAME = "worklog_ai.db"
        const val VERSION = 2
    }
}
