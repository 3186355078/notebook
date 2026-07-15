package com.worklogai.app.core.common.result

sealed interface DataResult<out T> {
    data class Success<T>(
        val value: T,
    ) : DataResult<T>

    data class Failure(
        val error: DataError,
    ) : DataResult<Nothing>
}

sealed interface DataError {
    data object Conflict : DataError

    data object NotFound : DataError

    data object Storage : DataError

    data class Validation(
        val reason: DataValidationReason,
    ) : DataError
}

enum class DataValidationReason {
    INVALID_ATTACHMENT,
    INVALID_BLOCK_CONTENT,
    INVALID_DATE_RANGE,
    INVALID_ORDERING,
    INVALID_SUMMARY,
    INVALID_TABLE_CONTENT,
    UNSAFE_ERROR_MESSAGE,
}
