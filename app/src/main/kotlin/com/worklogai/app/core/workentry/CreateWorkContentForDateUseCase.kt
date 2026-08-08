package com.worklogai.app.core.workentry

import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.model.CreatedWorkContent
import com.worklogai.app.core.model.NewWorkContent
import com.worklogai.app.core.repository.WorkEntryRepository
import java.time.LocalDate
import javax.inject.Inject

class CreateWorkContentForDateUseCase
    @Inject
    constructor(
        private val workEntryRepository: WorkEntryRepository,
        private val localDateProvider: LocalDateProvider,
    ) {
        suspend operator fun invoke(
            date: LocalDate,
            content: NewWorkContent,
        ): DataResult<CreatedWorkContent> {
            if (date > localDateProvider.today()) {
                return DataResult.Failure(DataError.Validation(DataValidationReason.FUTURE_WORK_ENTRY))
            }
            return workEntryRepository.createContentForDate(date, content)
        }
    }
