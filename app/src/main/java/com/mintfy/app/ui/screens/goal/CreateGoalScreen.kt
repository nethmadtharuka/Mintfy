
package com.mintfy.app.ui.screens.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val goalDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SavingsGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create savings goal") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !state.isSaving,
                    ) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Plan something worth saving for.",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Enter your target and deadline to calculate how much you need to save.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Goal name") },
                placeholder = { Text("e.g. MacBook") },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.nameError != null,
                supportingText = {
                    state.nameError?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.targetAmount,
                onValueChange = viewModel::onTargetAmountChange,
                label = { Text("Target amount (LKR)") },
                placeholder = { Text("e.g. 490000.00") },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.targetAmountError != null,
                supportingText = {
                    state.targetAmountError?.let { Text(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.startingSavedAmount,
                onValueChange = viewModel::onStartingSavedAmountChange,
                label = { Text("Amount already saved (LKR)") },
                placeholder = { Text("e.g. 11200.00") },
                singleLine = true,
                enabled = !state.isSaving,
                isError = state.startingSavedAmountError != null,
                supportingText = {
                    Text(
                        state.startingSavedAmountError
                            ?: "Leave blank if you have not saved anything yet."
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Start date",
                    style = MaterialTheme.typography.labelLarge,
                )

                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.startDate.format(goalDateFormatter))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Deadline",
                    style = MaterialTheme.typography.labelLarge,
                )

                OutlinedButton(
                    onClick = { showDeadlinePicker = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(state.deadline.format(goalDateFormatter))
                }

                state.deadlineError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = "Only received income will count toward your actual monthly savings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { viewModel.saveGoal(onSaved) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Saving...")
                } else {
                    Text("Save goal")
                }
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate.toUtcMillis(),
        )

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onStartDateChange(millis.toLocalDateUtc())
                        }
                        showStartDatePicker = false
                    },
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.deadline.toUtcMillis(),
        )

        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onDeadlineChange(millis.toLocalDateUtc())
                        }
                        showDeadlinePicker = false
                    },
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeadlinePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
