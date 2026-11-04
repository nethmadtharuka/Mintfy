package com.mintfy.app.ui.screens.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SavingsGoalScreen(
    viewModel: GoalViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refresh()

        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var startingSaved by remember { mutableStateOf("0") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var deadline by remember { mutableStateOf(LocalDate.now().plusYears(1)) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MintfyTopBar(
                title = "Savings Goal",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ErrorBanner(state.error)
            ErrorBanner(formError)

            when {
                state.isLoading && state.goal == null -> {
                    Text("Loading savings goal...")
                }

                state.goal == null -> {
                    Text(
                        "Create a savings goal",
                        style = MaterialTheme.typography.titleLarge
                    )

                    MintfyTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            formError = null
                        },
                        label = "Goal name"
                    )

                    MintfyTextField(
                        value = targetAmount,
                        onValueChange = {
                            targetAmount = it
                            formError = null
                        },
                        label = "Target amount (LKR)",
                        keyboardType = KeyboardType.Decimal
                    )

                    MintfyTextField(
                        value = startingSaved,
                        onValueChange = {
                            startingSaved = it
                            formError = null
                        },
                        label = "Starting saved amount (LKR)",
                        keyboardType = KeyboardType.Decimal
                    )

                    PickerField(
                        label = "Start date",
                        value = startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                        error = null,
                        onClick = { showStartPicker = true }
                    )

                    PickerField(
                        label = "Deadline",
                        value = deadline.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                        error = null,
                        onClick = { showDeadlinePicker = true }
                    )

                    PrimaryButton(
                        text = "Create goal",
                        isLoading = state.isSaving,
                        onClick = {
                            val targetCents = parseAmountToCents(targetAmount)
                            val savedCents = parseAmountToCents(startingSaved)

                            when {
                                name.isBlank() ->
                                    formError = "Enter a goal name."

                                name.trim().length > 60 ->
                                    formError = "Goal name cannot exceed 60 characters."

                                targetCents == null || targetCents <= 0L ->
                                    formError = "Enter a valid target amount."

                                savedCents == null || savedCents < 0L ->
                                    formError = "Enter a valid starting saved amount."

                                !deadline.isAfter(startDate) ->
                                    formError = "Deadline must be after the start date."

                                else -> {
                                    formError = null
                                    viewModel.createGoal(
                                        SavingsGoal(
                                            name = name.trim(),
                                            targetAmountCents = targetCents,
                                            startingSavedCents = savedCents,
                                            startDate = startDate,
                                            deadline = deadline
                                        )
                                    )
                                }
                            }
                        }
                    )
                }

                else -> {
                    val goal = state.goal
                    val progress = state.progress

                    if (goal != null && progress != null) {
                        Text(
                            goal.name,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        MintfyCard {
                            Text("Target amount")
                            Text(
                                formatSavingsGoalLkr(goal.targetAmountCents),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text("Currently saved")
                            Text(
                                formatSavingsGoalLkr(progress.currentSavedCents),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text("Remaining amount")
                            Text(
                                formatSavingsGoalLkr(progress.remainingCents),
                                style = MaterialTheme.typography.titleMedium
                            )

                            LinearProgressIndicator(
                                progress = {
                                    progress.progressPercent / 100f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("${progress.progressPercent}% completed")
                        }

                        MintfyCard {
                            Text(
                                "Monthly savings",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Required monthly saving")
                                Text(
                                    formatSavingsGoalLkr(
                                        progress.requiredMonthlySavingCents
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Actual monthly savings")
                                Text(
                                    formatSavingsGoalLkr(
                                        progress.actualMonthlySavingsCents
                                    )
                                )
                            }

                            Text(
                                if (progress.isOnTrack) "On Track" else "Behind",
                                color = if (progress.isOnTrack) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text("Deadline: ${goal.deadline}")
                        }

                        SecondaryButton(
                            text = "Retry",
                            onClick = viewModel::retry,
                            enabled = !state.isRetrying
                        )
                    } else if (state.isLoading) {
                        Text("Calculating goal progress...")
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        MintfyDatePicker(
            selected = startDate,
            onConfirm = {
                startDate = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
            title = "Select start date"
        )
    }

    if (showDeadlinePicker) {
        MintfyDatePicker(
            selected = deadline,
            onConfirm = {
                deadline = it
                showDeadlinePicker = false
            },
            onDismiss = { showDeadlinePicker = false },
            allowFuture = true,
            title = "Select deadline"
        )
    }
}

private fun parseAmountToCents(value: String): Long? {
    return try {
        BigDecimal(value.trim())
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    } catch (_: Exception) {
        null
    }
}

private fun formatSavingsGoalLkr(amountCents: Long): String {
    val amount = BigDecimal.valueOf(amountCents, 2)
    return "LKR ${String.format("%,.2f", amount)}"
}