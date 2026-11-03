
package com.mintfy.app.ui.screens.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.domain.savingsgoal.GoalProgressStatus
import com.mintfy.app.data.domain.savingsgoal.SavingsGoalProgress
import com.mintfy.app.data.model.SavingsGoal
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val lkrFormatter: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale("en", "LK"))

private fun formatLkr(amountCents: Long): String =
    lkrFormatter.format(BigDecimal.valueOf(amountCents, 2))

@Composable
fun SavingsGoalsScreen(
    onBack: () -> Unit,
    onCreateGoal: () -> Unit,
    viewModel: SavingsGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }

                Text(
                    text = "Savings Goals",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {

            Button(
                onClick = onCreateGoal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create savings goal")
            }

            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage
                            ?: "Unable to load savings goals.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.goals.isEmpty() -> {
                    Text(
                        text = "No savings goals yet. Create one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.goals,
                            key = { it.goal.id },
                        ) { item ->
                            SavingsGoalCard(
                                goal = item.goal,
                                progress = item.progress,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    progress: SavingsGoalProgress,
) {
    val statusText = when (progress.status) {
        GoalProgressStatus.ON_TRACK -> "On Track"
        GoalProgressStatus.BEHIND,
        GoalProgressStatus.ACHIEVED -> "Behind".takeIf {
            progress.status == GoalProgressStatus.BEHIND
        } ?: "On Track"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = goal.name,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Amount saved: ${formatLkr(progress.currentSavedCents)}"
            )

            Text(
                "Target amount: ${formatLkr(goal.targetAmountCents)}"
            )

            Text(
                "Remaining amount: ${formatLkr(progress.remainingAmountCents)}"
            )

            Text(
                "Deadline: ${goal.deadline}"
            )

            Text(
                "Required monthly saving: ${
                    progress.requiredMonthlySavingsCents?.let {
                        formatLkr(it)
                    } ?: "Deadline reached"
                }"
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
