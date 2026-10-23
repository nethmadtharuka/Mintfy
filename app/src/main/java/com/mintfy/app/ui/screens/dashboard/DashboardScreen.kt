package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.theme.MintfyTheme

/** Placeholder home screen until the dashboard is built; uses the Figma top bar and card styles. */
@Composable
fun DashboardScreen(email: String?, onSignOut: () -> Unit) {
    Scaffold(topBar = { MintfyTopBar("Dashboard") }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        ) {
            if (email != null) {
                MintfyCard {
                    Text(
                        "Signed in as",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(email, style = MaterialTheme.typography.titleMedium)
                }
            }
            SecondaryButton("Sign out", onClick = onSignOut)
        }
    }
}

@Preview
@Composable
fun DashboardScreenPreview() {
    MintfyTheme {
        DashboardScreen(email = "kavindu@example.com", onSignOut = {})
    }
}
