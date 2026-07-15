package com.worklogai.app.core.database.repository

import android.database.sqlite.SQLiteException
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.relation.ContentBlockWithAttachmentsEntity
import com.worklogai.app.core.database.relation.WorkEntryWithContentEntity
import com.worklogai.app.core.history.HistorySummaryInput
import com.worklogai.app.core.history.WorkEntrySummary
import com.worklogai.app.core.history.WorkHistoryPage
import com.worklogai.app.core.history.WorkHistoryRepository
import com.worklogai.app.core.history.buildHistorySummary
import com.worklogai.app.core.model.ContentBlockType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Converts Room aggregates into small history summaries. It deliberately handles an invalid
 * table payload as an unreadable table rather than making every history result unavailable.
 */
class OfflineWorkHistoryRepository
    @Inject
    constructor(
        private val workEntryDao: WorkEntryDao,
        private val tableContentCodec: TableContentCodec,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WorkHistoryRepository {
        override suspend fun getRecentEntries(
            limit: Int,
            offset: Int,
        ): DataResult<WorkHistoryPage> =
            queryPage(limit, offset) { relations ->
                relations.mapNotNull { it.toHistorySummary(tableContentCodec) }
            }

        override suspend fun getEntriesInRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): DataResult<List<WorkEntrySummary>> {
            if (startDate > endDate) return invalidRequest()
            return onDatabase {
                workEntryDao
                    .getActiveWithContentInDateRange(startDate, endDate)
                    .mapNotNull { it.toHistorySummary(tableContentCodec) }
                    .sortedByDescending(WorkEntrySummary::date)
            }
        }

        override suspend fun searchEntries(
            query: String,
            limit: Int,
            offset: Int,
        ): DataResult<WorkHistoryPage> {
            val normalizedQuery = query.normalizeForSearch()
            if (normalizedQuery.isEmpty()) return DataResult.Success(WorkHistoryPage(emptyList(), null))
            return queryPage(limit, offset) { relations ->
                relations
                    .filter { it.matches(normalizedQuery, tableContentCodec) }
                    .mapNotNull { it.toHistorySummary(tableContentCodec) }
            }
        }

        private suspend fun queryPage(
            limit: Int,
            offset: Int,
            transform: (List<WorkEntryWithContentEntity>) -> List<WorkEntrySummary>,
        ): DataResult<WorkHistoryPage> {
            if (limit <= 0 || offset < 0) return invalidRequest()
            return onDatabase {
                val relations = workEntryDao.getRecentActiveWithContent(limit, offset)
                WorkHistoryPage(
                    entries = transform(relations),
                    nextOffset = (offset + relations.size).takeIf { relations.size == limit },
                )
            }
        }

        private suspend fun <T> onDatabase(block: suspend () -> T): DataResult<T> =
            withContext(ioDispatcher) {
                try {
                    DataResult.Success(block())
                } catch (_: SQLiteException) {
                    DataResult.Failure(DataError.Storage)
                }
            }

        private fun invalidRequest(): DataResult.Failure =
            DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_ORDERING))
    }

private fun WorkEntryWithContentEntity.toHistorySummary(codec: TableContentCodec): WorkEntrySummary? {
    val sortedBlocks = blocks.sortedWith(HISTORY_BLOCK_ORDER)
    val textContents =
        sortedBlocks
            .filter {
                it.block.blockType == ContentBlockType.TEXT
            }.mapNotNull { it.block.textContent }
    val imageCount = sortedBlocks.count { it.block.blockType == ContentBlockType.IMAGE }
    val tableBlocks = sortedBlocks.filter { it.block.blockType == ContentBlockType.TABLE }
    val tableTitles =
        tableBlocks.map { block ->
            block.block.structuredContent
                ?.let(codec::decode)
                ?.getOrNull()
                ?.title
        }
    return buildHistorySummary(
        HistorySummaryInput(
            entryId = entry.id,
            date = entry.entryDate,
            title = entry.title,
            updatedAt = entry.updatedAt,
            textContents = textContents,
            imageCount = imageCount,
            tableCount = tableBlocks.size,
            tableTitles = tableTitles,
        ),
    )
}

private fun WorkEntryWithContentEntity.matches(
    query: String,
    codec: TableContentCodec,
): Boolean {
    if (entry.title.matches(query)) return true
    return blocks.any { relation -> relation.matches(query, codec) }
}

private fun ContentBlockWithAttachmentsEntity.matches(
    query: String,
    codec: TableContentCodec,
): Boolean =
    when (block.blockType) {
        ContentBlockType.TEXT -> block.textContent.matches(query)
        ContentBlockType.IMAGE -> attachments.any { attachment -> attachment.caption.matches(query) }
        ContentBlockType.TABLE ->
            block.structuredContent
                ?.let(codec::decode)
                ?.getOrNull()
                ?.let { table ->
                    table.title.matches(query) ||
                        table.columns.any { it.name.matches(query) } ||
                        table.rows.any { row -> row.cells.values.any { it.matches(query) } }
                } ?: false
    }

private fun String?.matches(query: String): Boolean = this?.normalizeForSearch()?.contains(query) == true

private fun String.normalizeForSearch(): String = trim().replace(SEARCH_WHITESPACE, " ").lowercase(Locale.ROOT)

private fun <T> DataResult<T>.getOrNull(): T? =
    when (this) {
        is DataResult.Failure -> null
        is DataResult.Success -> value
    }

private val HISTORY_BLOCK_ORDER =
    compareBy<ContentBlockWithAttachmentsEntity> { it.block.blockOrder }
        .thenBy { it.block.createdAt }
        .thenBy { it.block.id }

private val SEARCH_WHITESPACE = Regex("\\s+")
