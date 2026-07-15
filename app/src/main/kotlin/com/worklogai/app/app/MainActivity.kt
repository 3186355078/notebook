package com.worklogai.app.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.worklogai.app.app.navigation.SummaryNavigationTarget
import com.worklogai.app.app.navigation.summaryNavigationTargetOrNull
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var summaryNavigationTarget by mutableStateOf<SummaryNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        summaryNavigationTarget = intent.summaryNavigationTargetOrNull()
        enableEdgeToEdge()
        setContent {
            WorkLogTheme {
                WorkLogApp(
                    summaryNavigationTarget = summaryNavigationTarget,
                    onSummaryNavigationConsumed = { summaryNavigationTarget = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        summaryNavigationTarget = intent.summaryNavigationTargetOrNull()
    }
}
