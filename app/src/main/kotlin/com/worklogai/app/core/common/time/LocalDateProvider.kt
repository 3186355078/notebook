package com.worklogai.app.core.common.time

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface LocalDateProvider {
    fun today(): LocalDate

    fun zoneId(): ZoneId
}

class SystemLocalDateProvider
    @Inject
    constructor() : LocalDateProvider {
        override fun today(): LocalDate = LocalDate.now(zoneId())

        override fun zoneId(): ZoneId = ZoneId.systemDefault()
    }
