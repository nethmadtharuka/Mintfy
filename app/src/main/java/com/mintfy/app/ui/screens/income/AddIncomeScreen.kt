package com.mintfy.app.ui.screens.income

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.components.ChipGroup
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.SectionLabel
import com.mintfy.app.ui.components.SelectChip
import com.mintfy.app.ui.components.dateLabel
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.LocalDate

@Composable
fun AddIncomeScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddIncomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.isSaved) {
        if (!state.isSaved) return@LaunchedEffect
        if (state.savedOffline) {
            Toast.makeText(context, "Saved on this phone. It will sync when you're back online.", Toast.LENGTH_LONG).show()
        }
        onSaved()
    }

    val actions = remember(viewModel) { viewModel.toActions() }
    AddIncomeContent(state = state, actions = actions, onCancel = onBack)
}

/** Everything the user can do on the screen, so the content stays stateless and previewable. */
data class AddIncomeActions(
    val onSourceSelected: (String) -> Unit = {},
    val onStatusSelected: (IncomeStatus) -> Unit = {},
    val onLossSelected: (Boolean) -> Unit = {},
    val onCurrencySelected: (String) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onBonusChange: (String) -> Unit = {},
    val onRateChange: (String) -> Unit = {},
    val onLkrAmountChange: (String) -> Unit = {},
    val onDateSelected: (LocalDate) -> Unit = {},
    val onClientChange: (String) -> Unit = {},
    val onProjectChange: (String) -> Unit = {},
    val onMilestoneChange: (String) -> Unit = {},
    val onDueDateSelected: (LocalDate?) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpenNewSource: () -> Unit = {},
    val onDismissNewSource: () -> Unit = {},
    val onNewSourceNameChange: (String) -> Unit = {},
    val onNewSourceTypeSelected: (IncomeSourceType) -> Unit = {},
    val onNewSourceCurrencySelected: (String) -> Unit = {},
    val onNewSourceExpectedAmountChange: (String) -> Unit = {},
    val onCreateSource: () -> Unit = {},
)

private fun AddIncomeViewModel.toActions() = AddIncomeActions(
    onSourceSelected = ::onSourceSelected,
    onStatusSelected = ::onStatusSelected,
    onLossSelected = ::onLossSelected,
    onCurrencySelected = ::onCurrencySelected,
    onAmountChange = ::onAmountChange,
    onBonusChange = ::onBonusChange,
    onRateChange = ::onRateChange,
    onLkrAmountChange = ::onLkrAmountChange,
    onDateSelected = ::onDateSelected,
    onClientChange = ::onClientChange,
    onProjectChange = ::onProjectChange,
    onMilestoneChange = ::onMilestoneChange,
    onDueDateSelected = ::onDueDateSelected,
    onNoteChange = ::onNoteChange,
    onSave = ::save,
    onOpenNewSource = ::openNewSource,
    onDismissNewSource = ::dismissNewSource,
    onNewSourceNameChange = ::onNewSourceNameChange,
    onNewSourceTypeSelected = ::onNewSourceTypeSelected,
    onNewSourceCurrencySelected = ::onNewSourceCurrencySelected,
    onNewSourceExpectedAmountChange = ::onNewSourceExpectedAmountChange,
    onCreateSource = ::createSource,
)

/**
 * Figma "03 Add income": source, amount, bonus (salary), date received, then Save income / Cancel.
 * Fields appear only when they apply: status for freelance and other income, gain or loss for crypto,
 * rate and LKR credited for a foreign currency, and client, project and milestone for freelance.
 */
@Composable
fun AddIncomeContent(state: AddIncomeUiState, actions: AddIncomeActions, onCancel: () -> Unit) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDueDatePicker by rememberSaveable { mutableStateOf(false) }
    val source = state.selectedSource
    val input = state.input
    val errors = state.errors
    val enabled = !state.isSaving
    val isUnpaid = input.status != IncomeStatus.RECEIVED

    Scaffold(topBar = { MintfyTopBar("Add income", onBack = onCancel) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel("Source")
            ChipGroup(
                isLoading = state.isLoadingSources,
                emptyMessage = "",
                options = state.sources.map { it.id to it.name },
                selectedId = source?.id,
                onSelect = actions.onSourceSelected,
                trailing = { SelectChip(label = "+ New source", selected = false, onClick = actions.onOpenNewSource) },
            )
            if (!state.isLoadingSources && source == null) {
                // FR-H1: say what to do next; the "+ New source" chip above is the action.
                Text(
                    "Add your first income source, such as your salary or AdSense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (source != null) {
                if (state.statusOptions.size > 1) {
                    SectionLabel("Status")
                    ChipGroup(
                        isLoading = false,
                        emptyMessage = "",
                        options = state.statusOptions.map { it.name to statusLabel(it) },
                        selectedId = input.status.name,
                        onSelect = { actions.onStatusSelected(IncomeStatus.valueOf(it)) },
                    )
                }
                if (source.type.isSigned) {
                    SectionLabel("Result")
                    ChipGroup(
                        isLoading = false,
                        emptyMessage = "",
                        options = listOf("gain" to "Gain", "loss" to "Loss"),
                        selectedId = if (input.isLoss) "loss" else "gain",
                        onSelect = { actions.onLossSelected(it == "loss") },
                    )
                }

                SectionLabel("Currency")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "",
                    options = state.currencyOptions.map { it to it },
                    selectedId = input.currency,
                    onSelect = actions.onCurrencySelected,
                )
                if (errors.currency != null) ErrorBanner(errors.currency)

                MintfyTextField(
                    value = input.amount,
                    onValueChange = actions.onAmountChange,
                    label = "Amount (${input.currency})",
                    error = errors.amount,
                    enabled = enabled,
                    keyboardType = KeyboardType.Decimal,
                )
                if (source.type == IncomeSourceType.SALARY) {
                    MintfyTextField(
                        value = input.bonus,
                        onValueChange = actions.onBonusChange,
                        label = "Bonus (optional)",
                        error = errors.bonus,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                }

                if (input.currency != BASE_CURRENCY) {
                    MintfyTextField(
                        value = input.rate,
                        onValueChange = actions.onRateChange,
                        label = "Exchange rate (LKR per 1 ${input.currency})",
                        error = errors.rate,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                    MintfyTextField(
                        value = input.lkrAmount,
                        onValueChange = actions.onLkrAmountChange,
                        label = "LKR credited",
                        error = errors.lkrAmount,
                        enabled = enabled,
                        keyboardType = KeyboardType.Decimal,
                    )
                    Hint("Suggested from amount × rate. Change it to what your bank actually credited.")
                }
                state.salaryDifference?.let { Hint(it) }

                PickerField(
                    label = if (isUnpaid) "Expected payment date" else "Date received",
                    value = dateLabel(input.date, LocalDate.now()),
                    error = errors.date,
                    onClick = { if (enabled) showDatePicker = true },
                )

                if (source.type == IncomeSourceType.FREELANCE) {
                    MintfyTextField(
                        value = input.client,
                        onValueChange = actions.onClientChange,
                        label = "Client (optional)",
                        error = errors.client,
                        enabled = enabled,
                    )
                    MintfyTextField(
                        value = input.project,
                        onValueChange = actions.onProjectChange,
                        label = "Project (optional)",
                        error = errors.project,
                        enabled = enabled,
                    )
                    MintfyTextField(
                        value = input.milestone,
                        onValueChange = actions.onMilestoneChange,
                        label = "Milestone (optional)",
                        error = errors.milestone,
                        enabled = enabled,
                    )
                    if (isUnpaid) {
                        PickerField(
                            label = "Invoice due date (optional)",
                            value = input.dueDate?.let { dateLabel(it, LocalDate.now()) } ?: "Not set",
                            error = null,
                            onClick = { if (enabled) showDueDatePicker = true },
                        )
                        if (input.dueDate != null) {
                            TextButton(onClick = { actions.onDueDateSelected(null) }, enabled = enabled) {
                                Text("Remove due date", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                MintfyTextField(
                    value = input.note,
                    onValueChange = actions.onNoteChange,
                    label = "Note (optional)",
                    error = errors.note,
                    enabled = enabled,
                    imeAction = ImeAction.Done,
                    onImeAction = actions.onSave,
                )
            }

            Hint("Only received income counts toward savings.")
            ErrorBanner(state.error)
            PrimaryButton("Save income", onClick = actions.onSave, isLoading = state.isSaving)
            SecondaryButton("Cancel", onClick = onCancel, enabled = enabled)
        }
    }

    if (showDatePicker) {
        MintfyDatePicker(
            selected = input.date,
            // Received income happened already; an unpaid entry's date is when payment is expected.
            allowFuture = isUnpaid,
            onConfirm = {
                actions.onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showDueDatePicker) {
        MintfyDatePicker(
            selected = input.dueDate ?: LocalDate.now(),
            allowFuture = true,
            onConfirm = {
                actions.onDueDateSelected(it)
                showDueDatePicker = false
            },
            onDismiss = { showDueDatePicker = false },
        )
    }
    state.sourceDialog?.let { NewSourceDialog(it, actions) }
}

/** Creates an income source. Its type is chosen here once and cannot be changed later. */
@Composable
private fun NewSourceDialog(dialog: NewSourceDialogState, actions: AddIncomeActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissNewSource,
        title = { Text("New income source", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                MintfyTextField(
                    value = dialog.name,
                    onValueChange = actions.onNewSourceNameChange,
                    label = "Name, like Startup salary",
                    error = dialog.nameError,
                    enabled = !dialog.isSaving,
                )
                SectionLabel("Type (can't be changed later)")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "",
                    options = IncomeSourceType.entries.map { it.name to typeLabel(it) },
                    selectedId = dialog.type.name,
                    onSelect = { actions.onNewSourceTypeSelected(IncomeSourceType.valueOf(it)) },
                )
                SectionLabel("Usual currency")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "",
                    options = SUPPORTED_CURRENCIES.map { it to it },
                    selectedId = dialog.currency,
                    onSelect = actions.onNewSourceCurrencySelected,
                )
                if (dialog.type == IncomeSourceType.SALARY) {
                    MintfyTextField(
                        value = dialog.expectedAmount,
                        onValueChange = actions.onNewSourceExpectedAmountChange,
                        label = "Expected salary, LKR (optional)",
                        error = dialog.expectedAmountError,
                        enabled = !dialog.isSaving,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        onImeAction = actions.onCreateSource,
                    )
                }
                ErrorBanner(dialog.error)
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onCreateSource, enabled = !dialog.isSaving) {
                if (dialog.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add source", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissNewSource, enabled = !dialog.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusLabel(status: IncomeStatus) = when (status) {
    IncomeStatus.RECEIVED -> "Received"
    IncomeStatus.EXPECTED -> "Expected"
    IncomeStatus.INVOICED -> "Invoiced"
}

private fun typeLabel(type: IncomeSourceType) = when (type) {
    IncomeSourceType.SALARY -> "Salary"
    IncomeSourceType.FREELANCE -> "Freelance"
    IncomeSourceType.ADSENSE -> "AdSense"
    IncomeSourceType.CRYPTO -> "Crypto"
    IncomeSourceType.OTHER -> "Other"
}

private val previewSources = listOf(
    IncomeSource("src_salary", "Startup salary", IncomeSourceType.SALARY, "LKR", expectedAmountCents = 12_000_000),
    IncomeSource("src_freelance", "Freelance", IncomeSourceType.FREELANCE, "LKR"),
    IncomeSource("src_adsense", "Tech blog AdSense", IncomeSourceType.ADSENSE, "USD"),
)

@Preview
@Composable
fun AddIncomeSalaryPreview() {
    MintfyTheme {
        AddIncomeContent(
            state = AddIncomeUiState(
                sources = previewSources,
                isLoadingSources = false,
                selectedSource = previewSources[0],
                input = IncomeInput(amount = "120,000", bonus = "8,000", date = LocalDate.of(2026, 9, 30)),
                salaryDifference = "LKR 8,000 above your expected salary",
            ),
            actions = AddIncomeActions(),
            onCancel = {},
        )
    }
}

@Preview
@Composable
fun AddIncomeUsdPreview() {
    MintfyTheme {
        AddIncomeContent(
            state = AddIncomeUiState(
                sources = previewSources,
                isLoadingSources = false,
                selectedSource = previewSources[2],
                input = IncomeInput(
                    amount = "52.30", currency = "USD", rate = "298.75", lkrAmount = "15,540", date = LocalDate.now(),
                ),
                error = "The server didn't accept this income. Check the details and try again.",
            ),
            actions = AddIncomeActions(),
            onCancel = {},
        )
    }
}
