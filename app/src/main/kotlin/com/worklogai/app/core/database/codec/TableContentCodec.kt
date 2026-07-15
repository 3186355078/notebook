package com.worklogai.app.core.database.codec

import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.model.TableContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface TableContentCodec {
    fun encode(content: TableContent): DataResult<String>

    fun decode(value: String): DataResult<TableContent>
}

class KotlinxTableContentCodec
    @Inject
    constructor() : TableContentCodec {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }

        override fun encode(content: TableContent): DataResult<String> =
            content.validate().map { json.encodeToString(content) }

        override fun decode(value: String): DataResult<TableContent> =
            try {
                val content = json.decodeFromString<TableContent>(value)
                content.validate()
            } catch (_: SerializationException) {
                DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_TABLE_CONTENT))
            } catch (_: IllegalArgumentException) {
                DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_TABLE_CONTENT))
            }
    }

private const val MAX_TABLE_COLUMNS = 20
private const val MAX_TABLE_ROWS = 100

private fun TableContent.validate(): DataResult<TableContent> =
    when {
        !hasSupportedDimensions() -> invalidTableContent()
        !hasUniqueIds() -> invalidTableContent()
        !containsOnlyKnownColumns() -> invalidTableContent()
        else -> DataResult.Success(this)
    }

private fun TableContent.hasSupportedDimensions(): Boolean =
    columns.size <= MAX_TABLE_COLUMNS && rows.size <= MAX_TABLE_ROWS

private fun TableContent.hasUniqueIds(): Boolean =
    columns.map { it.id }.hasUniqueNonBlankValues() && rows.map { it.id }.hasUniqueNonBlankValues()

private fun TableContent.containsOnlyKnownColumns(): Boolean {
    val columnIds = columns.map { it.id }.toSet()
    return rows.none { row -> row.cells.keys.any { it !in columnIds } }
}

private fun List<String>.hasUniqueNonBlankValues(): Boolean = none(String::isBlank) && distinct().size == size

private fun invalidTableContent(): DataResult.Failure =
    DataResult.Failure(DataError.Validation(DataValidationReason.INVALID_TABLE_CONTENT))

private inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> =
    when (this) {
        is DataResult.Failure -> this
        is DataResult.Success -> DataResult.Success(transform(value))
    }
