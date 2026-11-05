package com.mintfy.app.ui.screens.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mintfy.app.data.model.SavingsGoalStatus
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SavingsGoalScreen(
    viewModel: GoalViewModel,
    onBack: (() -> Unit)? = null, // null when Goal is shown as a bottom-navigation tab
    bottomBar: @Composable () -> Unit = {},
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

    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var startingSaved by remember { mutableStateOf("0") }

    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var deadline by remember {
        mutableStateOf(LocalDate.now().plusYears(1))
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }

    var formError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MintfyTopBar(
                title = "Savings Goal",
                onBack = onBack
            )
        },
        bottomBar = bottomBar
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

                state.goal == null || isEditing -> {

                    Text(
                        if (isEditing) {
                            "Edit savings goal"
                        } else {
                            "Create a savings goal"
                        },
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
                        value = startDate.format(
                            DateTimeFormatter.ofPattern("dd MMM yyyy")
                        ),
                        error = null,
                        onClick = {
                            showStartPicker = true
                        }
                    )

                    PickerField(
                        label = "Deadline",
                        value = deadline.format(
                            DateTimeFormatter.ofPattern("dd MMM yyyy")
                        ),
                        error = null,
                        onClick = {
                            showDeadlinePicker = true
                        }
                    )

                    PrimaryButton(
                        text = if (isEditing) {
                            "Save Changes"
                        } else {
                            "Create goal"
                        },
                        isLoading = state.isSaving,
                        onClick = {

                            val (input, errors) =
                                SavingsGoalValidation.validate(
                                    name = name,
                                    targetAmount = targetAmount,
                                    startingSaved = startingSaved,
                                    startDate = startDate,
                                    deadline = deadline
                                )

                            formError =
                                errors.name
                                    ?: errors.targetAmount
                                            ?: errors.startingSaved
                                            ?: errors.deadline

                            if (input != null) {

                                val existingGoal = state.goal

                                val goal = SavingsGoal(
                                    id = existingGoal?.id ?: "active",
                                    name = input.name,
                                    targetAmountCents =
                                        input.targetAmountCents,
                                    startingSavedCents =
                                        input.startingSavedCents,
                                    startDate = input.startDate,
                                    deadline = input.deadline,
                                    status = existingGoal?.status
                                        ?: com.mintfy.app.data.model.SavingsGoalStatus.ACTIVE
                                )

                                formError = null

                                if (existingGoal == null) {
                                    viewModel.createGoal(goal)
                                } else {
                                    viewModel.updateGoal(goal)
                                    isEditing = false
                                }
                            }
                        }
                    )

                    if (isEditing) {
                        SecondaryButton(
                            text = "Cancel",
                            onClick = {
                                isEditing = false
                                formError = null
                            }
                        )
                    }
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
                                formatSavingsGoalLkr(
                                    goal.targetAmountCents
                                ),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text("Currently saved")

                            Text(
                                formatSavingsGoalLkr(
                                    progress.currentSavedCents
                                ),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text("Remaining amount")

                            Text(
                                formatSavingsGoalLkr(
                                    progress.remainingCents
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )

                            LinearProgressIndicator(
                                progress = {
                                    progress.progressPercent / 100f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                "${progress.progressPercent}% completed"
                            )
                        }

                        MintfyCard {

                            Text(
                                "Monthly savings",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
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
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Text("Actual monthly savings")

                                Text(
                                    formatSavingsGoalLkr(
                                        progress.actualMonthlySavingsCents
                                    )
                                )
                            }

                            val reached = goal.status == SavingsGoalStatus.ACHIEVED
                            Text(
                                when {
                                    reached -> "Goal reached"
                                    progress.isOnTrack -> "On Track"
                                    else -> "Behind"
                                },
                                color = if (reached || progress.isOnTrack) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text("Deadline: ${goal.deadline.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}")
                        }

                        PrimaryButton(
                            text = "Edit Goal",
                            onClick = {

                                name = goal.name

                                targetAmount =
                                    formatAmountInput(
                                        goal.targetAmountCents
                                    )

                                startingSaved =
                                    formatAmountInput(
                                        goal.startingSavedCents
                                    )

                                startDate = goal.startDate
                                deadline = goal.deadline

                                formError = null
                                isEditing = true
                            }
                        )

                        SecondaryButton(
                            text = "Delete Goal",
                            onClick = {
                                showDeleteConfirmation = true
                            },
                            enabled = !state.isDeleting
                        )

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
                formError = null
            },
            onDismiss = {
                showStartPicker = false
            },
            title = "Select start date"
        )
    }

    if (showDeadlinePicker) {

        MintfyDatePicker(
            selected = deadline,
            onConfirm = {
                deadline = it
                showDeadlinePicker = false
                formError = null
            },
            onDismiss = {
                showDeadlinePicker = false
            },
            allowFuture = true,
            title = "Select deadline"
        )
    }

    if (showDeleteConfirmation) {

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text("Delete savings goal?")
            },
            text = {
                Text(
                    "Are you sure you want to delete this savings goal? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteGoal()
                    },
                    enabled = !state.isDeleting
                ) {
                    Text(
                        if (state.isDeleting) {
                            "Deleting..."
                        } else {
                            "Delete"
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatAmountInput(amountCents: Long): String {
    return BigDecimal.valueOf(amountCents, 2).toPlainString()
}

private fun formatSavingsGoalLkr(amountCents: Long): String {
    val amount = BigDecimal.valueOf(amountCents, 2)

    return "LKR ${String.format("%,.2f", amount)}"
}
