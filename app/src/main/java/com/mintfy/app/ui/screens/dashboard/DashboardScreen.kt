package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.theme.MintfyTheme

/** Placeholder home screen until the dashboard is built; uses the Figma top bar and card styles. */
@Composable
fun DashboardScreen(email: String?, onAddExpense: () -> Unit, onAddIncome: () -> Unit, onCreateSavingsGoal: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        topBar = { MintfyTopBar("Dashboard") },
        floatingActionButton = { AddExpenseButton(onClick = onAddExpense) },
    ) { innerPadding ->
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
            // Until the Income tab exists, this is the way into Add income.
            SecondaryButton("Add income", onClick = onAddIncome)
            SecondaryButton(
                "Create savings goal",
                onClick = onCreateSavingsGoal
            )
            SecondaryButton("Sign out", onClick = onSignOut)
        }
    }
}

/** Figma "FAB Add expense": green, 28dp radius, black 28 Bold "+". Read out as "Add expense". */
@Composable
private fun AddExpenseButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.semantics { contentDescription = "Add expense" },
    ) {
        Text(
            "+",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Preview
@Composable
fun DashboardScreenPreview() {
    MintfyTheme {
        DashboardScreen(email = "kavindu@example.com", onAddExpense = {}, onAddIncome = {}, onCreateSavingsGoal = {}, onSignOut = {})
    }
}
