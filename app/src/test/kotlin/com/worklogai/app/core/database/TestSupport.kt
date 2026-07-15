package com.worklogai.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import java.time.Instant

internal fun createInMemoryDatabase(): WorkLogDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room
        .inMemoryDatabaseBuilder(context, WorkLogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}

internal class SequenceIdGenerator(
    ids: List<String>,
) : IdGenerator {
    private val remainingIds = ArrayDeque(ids)

    override fun generate(): String = remainingIds.removeFirst()
}

internal class FixedTimeProvider(
    private var current: Instant,
) : TimeProvider {
    override fun now(): Instant = current

    fun advanceTo(value: Instant) {
        current = value
    }
}

internal fun <T> DataResult<T>.successValue(): T =
    when (this) {
        is DataResult.Failure -> error("Expected success but was $error")
        is DataResult.Success -> value
    }

internal fun DataResult<*>.failureValue() =
    when (this) {
        is DataResult.Failure -> error
        is DataResult.Success -> error("Expected failure but was $value")
    }
