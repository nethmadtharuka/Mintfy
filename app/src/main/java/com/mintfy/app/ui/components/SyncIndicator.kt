package com.mintfy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.data.repository.SyncStatus
import com.mintfy.app.ui.theme.MintfyTheme
import com.mintfy.app.ui.theme.StatusWarning

/**
 * The app's sync status, provided once by the navigation host so every top bar can show it
 * without each screen passing it along.
 */
val LocalSyncStatus = compositionLocalOf { SyncStatus.SYNCED }

/** Short pill text, and the full sentence a screen reader announces. Null when there is nothing to say. */
fun syncIndicatorText(status: SyncStatus): Pair<String, String>? = when (status) {
    SyncStatus.SYNCED -> null
    SyncStatus.SYNCING -> "Syncing…" to "Sending your latest changes."
    SyncStatus.OFFLINE -> "Offline" to "You're offline. Your data on this phone is still available."
    SyncStatus.OFFLINE_PENDING -> "Offline · not synced" to
        "You're offline. Your changes are saved on this phone and will sync when you're back online."
}

/** FR-G5: a small pill in the top bar while offline or while changes are waiting to sync. */
@Composable
fun SyncIndicator(status: SyncStatus = LocalSyncStatus.current) {
    val (label, description) = syncIndicatorText(status) ?: return
    val color = if (status == SyncStatus.SYNCING) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = color,
        border = BorderStroke(1.dp, color),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Preview
@Composable
fun SyncIndicatorPreview() {
    MintfyTheme { MintfyTopBar("November 2026") { SyncIndicator(SyncStatus.OFFLINE_PENDING) } }
}
