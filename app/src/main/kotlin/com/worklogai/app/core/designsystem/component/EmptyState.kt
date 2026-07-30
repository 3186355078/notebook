package com.worklogai.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing

@Composable
fun EmptyState(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) = WorkLogEmptyState(title, body, action, modifier = modifier)

@Composable
fun WorkLogEmptyState(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WorkLogSpacing.huge, vertical = WorkLogSpacing.extraLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(WorkLogSpacing.medium).size(28.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = WorkLogSpacing.large).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = WorkLogSpacing.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        action?.let { content ->
            Column(modifier = Modifier.padding(top = WorkLogSpacing.large)) {
                content()
            }
        }
    }
}
