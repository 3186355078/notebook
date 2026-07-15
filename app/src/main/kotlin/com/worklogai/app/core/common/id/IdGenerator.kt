package com.worklogai.app.core.common.id

import java.util.UUID
import javax.inject.Inject

fun interface IdGenerator {
    fun generate(): String
}

class UuidIdGenerator
    @Inject
    constructor() : IdGenerator {
        override fun generate(): String = UUID.randomUUID().toString()
    }
