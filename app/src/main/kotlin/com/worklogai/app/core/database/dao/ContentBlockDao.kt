package com.worklogai.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.relation.ContentBlockWithAttachmentsEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
@Suppress("TooManyFunctions") // One explicit operation per required block persistence action.
interface ContentBlockDao {
    @Query("SELECT * FROM content_blocks ORDER BY entryId ASC, blockOrder ASC, createdAt ASC, id ASC")
    suspend fun getAll(): List<ContentBlockEntity>

    @Query(
        "SELECT * FROM content_blocks " +
            "WHERE entryId = :entryId " +
            "ORDER BY blockOrder ASC, createdAt ASC, id ASC",
    )
    suspend fun getByEntryId(entryId: String): List<ContentBlockEntity>

    @Query(
        "SELECT * FROM content_blocks " +
            "WHERE entryId = :entryId " +
            "ORDER BY blockOrder ASC, createdAt ASC, id ASC",
    )
    fun observeByEntryId(entryId: String): Flow<List<ContentBlockEntity>>

    @Query("SELECT * FROM content_blocks WHERE id = :blockId LIMIT 1")
    suspend fun getById(blockId: String): ContentBlockEntity?

    @Transaction
    @Query("SELECT * FROM content_blocks WHERE id = :blockId LIMIT 1")
    suspend fun getWithAttachments(blockId: String): ContentBlockWithAttachmentsEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(block: ContentBlockEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(blocks: List<ContentBlockEntity>)

    @Update
    suspend fun update(block: ContentBlockEntity)

    @Delete
    suspend fun delete(block: ContentBlockEntity)

    @Query("DELETE FROM content_blocks WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String): Int

    @Query("DELETE FROM content_blocks")
    suspend fun deleteAll(): Int

    @Query("SELECT COALESCE(MAX(blockOrder), -1) + 1 FROM content_blocks WHERE entryId = :entryId")
    suspend fun nextBlockOrder(entryId: String): Int

    @Query(
        "UPDATE content_blocks " +
            "SET blockOrder = :blockOrder, updatedAt = :updatedAt " +
            "WHERE id = :blockId AND entryId = :entryId",
    )
    suspend fun updateOrder(
        entryId: String,
        blockId: String,
        blockOrder: Int,
        updatedAt: Instant,
    ): Int

    @Transaction
    suspend fun updateOrders(
        entryId: String,
        orderedBlockIds: List<String>,
        updatedAt: Instant,
    ) {
        orderedBlockIds.forEachIndexed { index, blockId ->
            check(updateOrder(entryId, blockId, index, updatedAt) == 1)
        }
    }
}
