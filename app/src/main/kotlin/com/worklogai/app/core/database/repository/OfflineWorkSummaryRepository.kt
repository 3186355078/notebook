package com.worklogai.app.core.database.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.dao.WorkSummaryDao
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.database.mapper.toDomain
import com.worklogai.app.core.database.mapper.toEntity
import com.worklogai.app.core.model.GeneratedSummaryContent
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkSummaryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@Suppress("TooManyFunctions")
class OfflineWorkSummaryRepository
    @Inject
    constructor(
        private val database: WorkLogDatabase,
        private val workSummaryDao: WorkSummaryDao,
        private val timeProvider: TimeProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WorkSummaryRepository {
        override fun observeSummary(
            summaryType: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
        ): Flow<DataResult<WorkSummary?>> {
            if (periodStart > periodEnd) {
                return kotlinx.coroutines.flow.flowOf(invalid(DataValidationReason.INVALID_DATE_RANGE))
            }

            return workSummaryDao
                .observeByPeriod(summaryType, periodStart, periodEnd)
                .map { entity -> DataResult.Success(entity?.toDomain()) }
                .catchDatabaseErrors()
                .flowOn(ioDispatcher)
        }

        override fun observeSummaries(summaryType: SummaryType): Flow<DataResult<List<WorkSummary>>> =
            workSummaryDao
                .observeByType(summaryType)
                .map { entities -> DataResult.Success(entities.map(WorkSummaryEntity::toDomain)) }
                .catchDatabaseErrors()
                .flowOn(ioDispatcher)

        override suspend fun getSummary(
            summaryType: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
        ): DataResult<WorkSummary?> {
            if (periodStart > periodEnd) return invalid(DataValidationReason.INVALID_DATE_RANGE)

            return databaseResult {
                DataResult.Success(workSummaryDao.getByPeriod(summaryType, periodStart, periodEnd)?.toDomain())
            }
        }

        override suspend fun saveSummary(summary: WorkSummary): DataResult<WorkSummary> =
            when {
                summary.periodStart > summary.periodEnd -> invalid(DataValidationReason.INVALID_DATE_RANGE)
                !summary.errorMessage.isSafeErrorMessage() -> invalid(DataValidationReason.UNSAFE_ERROR_MESSAGE)
                else ->
                    databaseResult {
                        database.withTransaction {
                            val existing =
                                workSummaryDao.getByPeriod(
                                    summary.summaryType,
                                    summary.periodStart,
                                    summary.periodEnd,
                                )
                            val now = timeProvider.now()
                            val entity =
                                summary
                                    .toEntity()
                                    .copy(
                                        id = existing?.id ?: summary.id,
                                        createdAt = existing?.createdAt ?: now,
                                        updatedAt = now,
                                    )
                            if (existing == null) {
                                workSummaryDao.insert(entity)
                            } else {
                                workSummaryDao.update(entity)
                            }
                            DataResult.Success(entity.toDomain())
                        }
                    }
            }

        override suspend fun updateStatus(
            summaryType: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            status: SummaryStatus,
            errorMessage: String?,
        ): DataResult<Unit> =
            when {
                periodStart > periodEnd -> invalid(DataValidationReason.INVALID_DATE_RANGE)
                !errorMessage.isSafeErrorMessage() -> invalid(DataValidationReason.UNSAFE_ERROR_MESSAGE)
                else ->
                    databaseResult {
                        val rows =
                            workSummaryDao.updateStatus(
                                summaryType = summaryType,
                                periodStart = periodStart,
                                periodEnd = periodEnd,
                                status = status,
                                errorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty),
                                updatedAt = timeProvider.now(),
                            )
                        if (rows == 1) DataResult.Success(Unit) else notFound()
                    }
            }

        override suspend fun saveGeneratedContent(content: GeneratedSummaryContent): DataResult<Unit> =
            when {
                content.periodStart > content.periodEnd -> invalid(DataValidationReason.INVALID_DATE_RANGE)
                !content.isValid() -> invalid(DataValidationReason.INVALID_SUMMARY)
                else ->
                    databaseResult {
                        val rows =
                            workSummaryDao.updateGeneratedContent(
                                summaryType = content.summaryType,
                                periodStart = content.periodStart,
                                periodEnd = content.periodEnd,
                                sourceHash = content.sourceHash,
                                aiProvider = content.aiProvider,
                                modelName = content.modelName,
                                originalContent = content.originalContent,
                                editedContent = content.editedContent,
                                generatedAt = content.generatedAt,
                                updatedAt = timeProvider.now(),
                            )
                        if (rows == 1) DataResult.Success(Unit) else notFound()
                    }
            }

        override suspend fun updateEditedContent(
            summaryType: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            editedContent: String?,
        ): DataResult<Unit> {
            if (periodStart > periodEnd) return invalid(DataValidationReason.INVALID_DATE_RANGE)

            return databaseResult {
                val rows =
                    workSummaryDao.updateEditedContent(
                        summaryType = summaryType,
                        periodStart = periodStart,
                        periodEnd = periodEnd,
                        editedContent = editedContent,
                        updatedAt = timeProvider.now(),
                    )
                if (rows == 1) DataResult.Success(Unit) else notFound()
            }
        }

        override suspend fun getNotSuccessfulSummaries(summaryType: SummaryType): DataResult<List<WorkSummary>> =
            databaseResult {
                DataResult.Success(
                    workSummaryDao.getNotSuccessfulByType(summaryType).map(WorkSummaryEntity::toDomain),
                )
            }

        override suspend fun deleteSummary(
            summaryType: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
        ): DataResult<Unit> {
            if (periodStart > periodEnd) return invalid(DataValidationReason.INVALID_DATE_RANGE)

            return databaseResult {
                val summary =
                    workSummaryDao.getByPeriod(summaryType, periodStart, periodEnd) ?: return@databaseResult notFound()
                workSummaryDao.delete(summary)
                DataResult.Success(Unit)
            }
        }

        private suspend fun <T> databaseResult(operation: suspend () -> DataResult<T>): DataResult<T> =
            withContext(ioDispatcher) {
                try {
                    operation()
                } catch (_: SQLiteConstraintException) {
                    DataResult.Failure(DataError.Conflict)
                } catch (_: SQLiteException) {
                    DataResult.Failure(DataError.Storage)
                }
            }

        private fun <T> Flow<DataResult<T>>.catchDatabaseErrors(): Flow<DataResult<T>> =
            catch { error ->
                currentCoroutineContext().ensureActive()
                val dataError = if (error is SQLiteConstraintException) DataError.Conflict else DataError.Storage
                emit(DataResult.Failure(dataError))
            }
    }

private fun String?.isSafeErrorMessage(): Boolean =
    this == null ||
        !Regex("(?i)(authorization|bearer|api[_ -]?key|access[_ -]?token)").containsMatchIn(this)

private fun GeneratedSummaryContent.isValid(): Boolean =
    listOf(sourceHash, aiProvider, modelName, originalContent).none(String::isBlank)

private fun <T> notFound(): DataResult<T> = DataResult.Failure(DataError.NotFound)

private fun <T> invalid(reason: DataValidationReason): DataResult<T> = DataResult.Failure(DataError.Validation(reason))
