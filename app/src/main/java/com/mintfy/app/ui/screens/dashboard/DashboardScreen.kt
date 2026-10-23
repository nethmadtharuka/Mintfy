package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.theme.MintfyTheme

/** Placeholder home screen until the dashboard is built. */
@Composable
fun DashboardScreen(email: String?, onSignOut: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            if (email != null) {
                Text(
                    "Signed in as $email",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    MintfyTheme {
        DashboardScreen(email = "kavindu@example.com", onSignOut = {})
    }
}
