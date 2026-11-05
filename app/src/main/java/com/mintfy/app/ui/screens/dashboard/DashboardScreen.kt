
package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.SavingsGoalProgress
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.screens.goal.GoalViewModel
import com.mintfy.app.ui.screens.goal.GoalUiState
import com.mintfy.app.ui.theme.MintfyTheme
import java.math.BigDecimal

@Composable
fun DashboardScreen(
    goalViewModel: GoalViewModel,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onSignOut: () -> Unit,
    onCategories: () -> Unit,
    onRecurring: () -> Unit,
    onSavingsGoal: () -> Unit,
    unpaidInvoices: @Composable () -> Unit = {},
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    bottomBar: @Composable () -> Unit = {},
) {

    val goalState by goalViewModel.uiState.collectAsState()
    val dashboard by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val summary = dashboard.summary

    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh date-dependent goal calculations (and roll the dashboard over to a new month)
    // when the Dashboard becomes active again.
    LaunchedEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {
                goalViewModel.refresh()
                dashboardViewModel.refresh()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        goalViewModel.refresh()

        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            // Figma "02 Dashboard": the top bar names the month the figures are for.
            MintfyTopBar(monthTitle(dashboard.month)) {
                DashboardMenu(onCategories = onCategories, onRecurring = onRecurring, onSignOut = onSignOut)
            }
        },

        // Figma "Bottom nav": tabs plus the green "+" that opens Add expense.
        bottomBar = bottomBar,

    ) { innerPadding ->

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // Loading, or an error with retry above the last figures (FR-H1, FR-H2).
            DashboardStatus(dashboard, onRetry = dashboardViewModel::retry)

            // FR-F1: savings this month, or the next step when the month is still empty.
            if (summary != null) {
                if (summary.isEmpty) {
                    EmptyMonthCard(summary.month, onAddExpense = onAddExpense, onAddIncome = onAddIncome)
                } else {
                    SavingsThisMonthCard(summary)
                }
            }

            // FR-F4: Savings Goal Progress Card.
            when {

                goalState.isLoading && goalState.goal == null -> {
                    LoadingCard()
                }

                goalState.goal != null &&
                        goalState.progress != null -> {

                    SavingsGoalProgressCard(
                        progress = goalState.progress!!,
                        onViewGoal = onSavingsGoal
                    )
                }

                goalState.goal == null -> {

                    // No goal has been created yet.
                    // The existing button opens the goal screen.
                    MintfyCard {

                        Text(
                            "No savings goal yet",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Create a savings goal to track your progress."
                        )

                        SecondaryButton(
                            text = "Create savings goal",
                            onClick = onSavingsGoal
                        )
                    }
                }
            }

            // FR-F2: committed vs discretionary (Figma "Where your money went").
            if (summary != null && !summary.isEmpty) {
                MoneySplitCard(summary)
            }

            // Existing unpaid freelance invoices section (FR-B7, not counted as income).
            unpaidInvoices()

            // FR-F3, FR-F6, FR-B8: the rest of the month's picture.
            if (summary != null && !summary.isEmpty) {
                TopCategoriesCard(summary)
                IncomeBySourceCard(summary)
                summary.estimate?.let { EstimateCard(it) }
            }
        }
    }
}

/**
 * The top bar's "⋮" menu, for actions that are not a bottom-navigation tab.
 * Income, History and Goal are tabs now, and Add income is on the Income tab.
 */
@Composable
private fun DashboardMenu(
    onCategories: () -> Unit,
    onRecurring: () -> Unit,
    onSignOut: () -> Unit,
) {

    var expanded by remember { mutableStateOf(false) }

    Box {

        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "More options" }
        ) {
            Text(
                "⋮",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {

            DropdownMenuItem(
                text = { Text("Categories") },
                onClick = {
                    expanded = false
                    onCategories()
                }
            )

            DropdownMenuItem(
                text = { Text("Recurring expenses") },
                onClick = {
                    expanded = false
                    onRecurring()
                }
            )

            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    expanded = false
                    onSignOut()
                }
            )
        }
    }
}

/**
 * FR-F4:
 * Displays the current savings goal progress on the Dashboard.
 */
@Composable
private fun SavingsGoalProgressCard(
    progress: SavingsGoalProgress,
    onViewGoal: () -> Unit
) {

    MintfyCard {

        // Goal name.
        Text(
            text = progress.goal.name,
            style = MaterialTheme.typography.titleLarge
        )

        // Current saved amount.
        Text(
            text = "Amount saved",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = formatGoalLkr(progress.currentSavedCents),
            style = MaterialTheme.typography.headlineSmall
        )

        // Visual progress indicator.
        LinearProgressIndicator(
            progress = {
                progress.progressPercent / 100f
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${progress.progressPercent}% completed",
            style = MaterialTheme.typography.bodySmall
        )

        // Required monthly saving.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = "Required monthly saving",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = formatGoalLkr(
                    progress.requiredMonthlySavingCents
                ),
                style = MaterialTheme.typography.titleMedium
            )
        }

        // On Track / Behind status.
        Text(
            text = if (progress.isOnTrack) {
                "On Track"
            } else {
                "Behind"
            },

            color = if (progress.isOnTrack) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },

            style = MaterialTheme.typography.titleMedium
        )

        // Navigate to the complete Savings Goal screen.
        SecondaryButton(
            text = "View goal",
            onClick = onViewGoal
        )
    }
}

/**
 * Format integer LKR cents into a readable currency amount.
 */
private fun formatGoalLkr(amountCents: Long): String {

    val amount = BigDecimal.valueOf(amountCents, 2)

    return "LKR ${String.format("%,.2f", amount)}"
}
