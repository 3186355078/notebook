package com.worklogai.app.feature.editor.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.worklogai.app.R
import com.worklogai.app.feature.editor.SaveState

@Composable
fun SaveStatusIndicator(
    saveState: SaveState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        when (saveState) {
            SaveState.Idle -> ""
            is SaveState.Failed -> stringResource(R.string.today_save_failed)
            is SaveState.Saved -> stringResource(R.string.today_save_saved)
            SaveState.Saving -> stringResource(R.string.today_save_saving)
        }
    if (label.isEmpty()) return

    if (saveState is SaveState.Failed) {
        TextButton(onClick = onRetry, modifier = modifier.semantics { stateDescription = label }) {
            Text(text = label, color = MaterialTheme.colorScheme.error)
        }
    } else {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { stateDescription = label },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
