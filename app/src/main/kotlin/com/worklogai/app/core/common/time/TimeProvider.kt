package com.worklogai.app.core.common.time

import java.time.Instant
import javax.inject.Inject

fun interface TimeProvider {
    fun now(): Instant
}

class SystemTimeProvider
    @Inject
    constructor() : TimeProvider {
        override fun now(): Instant = Instant.now()
    }
