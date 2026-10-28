
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.domain.savingsgoal.SavingsGoalCalculations
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

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
                        text = state.errorMessage!!,
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
    progress: com.mintfy.app.data.domain.savingsgoal.SavingsGoalProgress,
) {
    val today = LocalDate.now()

    val moneyFormat = NumberFormat.getCurrencyInstance(
        Locale("en", "LK"),
    )

    fun formatLkr(cents: Long): String =
        moneyFormat.format(cents / 100.0)

    val statusText = when {
        goal.status == SavingsGoalStatus.ARCHIVED -> "Archived"
        goal.status == SavingsGoalStatus.ACHIEVED ||
                progress.status.name == "ACHIEVED" -> "Achieved"
        progress.status.name == "BEHIND" -> "Behind"
        else -> "On Track"
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

            Spacer(Modifier.height(8.dp))

            Text("Status: $statusText")

            Spacer(Modifier.height(8.dp))

            Text(
                "${formatLkr(progress.currentSavedCents)} saved of " +
                        formatLkr(goal.targetAmountCents),
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = {
                    (progress.progressPercentage / 100.0)
                        .toFloat()
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Progress: ${"%.1f".format(progress.progressPercentage)}%",
            )

            Text(
                "Remaining: ${formatLkr(progress.remainingAmountCents)}",
            )

            Text("Deadline: ${goal.deadline}")

            val daysRemaining = ChronoUnit.DAYS.between(
                today,
                goal.deadline,
            )

            Text(
                when {
                    progress.remainingAmountCents == 0L ->
                        "Target reached!"

                    daysRemaining < 0L ->
                        "Deadline passed"

                    daysRemaining == 0L ->
                        "Deadline is today"

                    else ->
                        "$daysRemaining days remaining"
                },
            )

            progress.requiredMonthlySavingsCents?.let { monthly ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Required monthly savings: ${formatLkr(monthly)}",
                )
            }
        }
    }
}
