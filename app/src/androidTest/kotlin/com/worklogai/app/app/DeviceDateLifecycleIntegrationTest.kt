package com.worklogai.app.app

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.SystemLocalDateProvider
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.feature.editor.TextBlockUiModel
import com.worklogai.app.feature.editor.TodayAction
import com.worklogai.app.feature.editor.TodayViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DeviceDateLifecycleIntegrationTest {
    @Test
    fun todayModeFlushesAcrossDateChangeWhileFixedDateIgnoresItOnDevice() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            val original = ByteArrayOutputStream()
            assertTrue(dependencies.backupArchiveService().createBackup(original) is BackupOperationResult.Success)
            val clock = MutableTimeProvider(Instant.parse("1902-01-01T08:00:00Z"))

            try {
                val todayViewModel =
                    onMain {
                        TodayViewModel(
                            dependencies.workEntryRepository(),
                            dependencies.attachmentFileStore(),
                            dependencies.tableContentEditor(),
                            clock,
                            SavedStateHandle(),
                        )
                    }
                waitUntil { !todayViewModel.uiState.value.isLoading }
                onMain { todayViewModel.onAction(TodayAction.AddTextBlock) }
                waitUntil {
                    todayViewModel.uiState.value.blocks
                        .filterIsInstance<TextBlockUiModel>()
                        .isNotEmpty()
                }
                val block =
                    todayViewModel.uiState.value.blocks
                        .filterIsInstance<TextBlockUiModel>()
                        .single()
                onMain {
                    todayViewModel.onAction(TodayAction.TextChanged(block.id, "Stage 9 midnight draft"))
                    todayViewModel.onAction(TodayAction.DateChanged(LocalDate.of(1902, 1, 2)))
                }
                waitUntil {
                    todayViewModel.uiState.value.date == LocalDate.of(1902, 1, 2) &&
                        !todayViewModel.uiState.value.isLoading
                }
                val dayOne = dependencies.workEntryRepository().getEntry(LocalDate.of(1902, 1, 1)) as DataResult.Success
                assertEquals(
                    "Stage 9 midnight draft",
                    dayOne.value
                        ?.blocks
                        ?.filterIsInstance<com.worklogai.app.core.model.ContentBlock.Text>()
                        ?.single()
                        ?.content,
                )
                assertTrue(
                    todayViewModel.uiState.value.blocks
                        .isEmpty(),
                )

                val fixedViewModel =
                    onMain {
                        TodayViewModel(
                            dependencies.workEntryRepository(),
                            dependencies.attachmentFileStore(),
                            dependencies.tableContentEditor(),
                            clock,
                            SavedStateHandle(mapOf("entryDate" to "1902-01-01")),
                        )
                    }
                waitUntil { !fixedViewModel.uiState.value.isLoading }
                onMain { fixedViewModel.onAction(TodayAction.DateChanged(LocalDate.of(1902, 1, 2))) }
                assertEquals(LocalDate.of(1902, 1, 1), fixedViewModel.uiState.value.date)
                assertFalse(fixedViewModel.uiState.value.followsCurrentDate)
            } finally {
                assertTrue(
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(original.toByteArray())) is BackupOperationResult.Success,
                )
            }
        }

    @Test
    fun processTimeZonesCrossYearWeekAndLeapMonthProduceStableDeviceResults() {
        val originalTimeZone = TimeZone.getDefault()
        val provider = SystemLocalDateProvider()
        try {
            listOf("Asia/Shanghai", "America/Denver", "UTC").forEach { zoneName ->
                val zone = ZoneId.of(zoneName)
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val before = Instant.now().atZone(zone).toLocalDate()
                val actual = provider.today()
                val after = Instant.now().atZone(zone).toLocalDate()
                assertTrue(actual == before || actual == after)
                assertEquals(zone, provider.zoneId())
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }

        val calculator = DefaultWorkPeriodCalculator()
        val crossYear = calculator.weekContaining(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), crossYear.start)
        assertEquals(LocalDate.of(2027, 1, 3), crossYear.end)
        val leapMonth = calculator.monthContaining(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 29), leapMonth.end)
        assertEquals(LocalDate.of(2027, 2, 28), calculator.monthContaining(YearMonth.of(2027, 2)).end)
    }

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value.set(block()) }
        return value.get()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MILLIS
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        assertTrue(condition())
    }

    private class MutableTimeProvider(
        var instant: Instant,
    ) : TimeProvider {
        override fun now(): Instant = instant
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
