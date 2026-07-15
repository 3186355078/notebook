package com.worklogai.app.ai.provider

import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.ai.model.AiSummaryResponse

interface AiSummaryProvider {
    val providerId: String

    suspend fun generateSummary(request: AiSummaryRequest): Result<AiSummaryResponse>
}
