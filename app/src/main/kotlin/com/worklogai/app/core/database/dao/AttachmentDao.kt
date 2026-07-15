package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.worklogai.app.core.database.entity.AttachmentEntity

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY blockId ASC, createdAt ASC, id ASC")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE blockId = :blockId ORDER BY createdAt ASC, id ASC")
    suspend fun getByBlockId(blockId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun getById(attachmentId: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll(): Int

    @Query(
        "SELECT localPath FROM attachments " +
            "WHERE blockId = :blockId " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getPathsByBlockId(blockId: String): List<String>

    @Query(
        "SELECT attachments.localPath FROM attachments " +
            "INNER JOIN content_blocks ON attachments.blockId = content_blocks.id " +
            "WHERE content_blocks.entryId = :entryId " +
            "ORDER BY attachments.createdAt ASC, attachments.id ASC",
    )
    suspend fun getPathsByEntryId(entryId: String): List<String>
}
