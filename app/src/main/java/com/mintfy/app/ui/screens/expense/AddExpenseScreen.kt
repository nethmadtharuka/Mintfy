package com.mintfy.app.ui.screens.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.DefaultReferenceData
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AddExpenseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Saving is instant (it works offline), so close the screen as soon as the ViewModel reports it.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    AddExpenseContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onCategorySelected = viewModel::onCategorySelected,
        onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
        onDateSelected = viewModel::onDateSelected,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onCancel = onBack,
    )
}

/**
 * Figma "05 Add expense". Stateless: it only draws [state] and reports what the user does.
 * With the date defaulting to today and the category and payment method pre-selected, a typical
 * expense is: type the amount, tap Save expense (NFR-01).
 */
@Composable
fun AddExpenseContent(
    state: AddExpenseUiState,
    onAmountChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onPaymentMethodSelected: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    focusAmountOnOpen: Boolean = true,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusAmountOnOpen) amountFocus.requestFocus() // Figma annotation: amount field has focus on open
    }

    Scaffold(topBar = { MintfyTopBar("Add expense", onBack = onCancel) }) { innerPadding ->
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
            MintfyTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = "Amount (LKR)",
                error = state.amountError,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.focusRequester(amountFocus),
            )

            SectionLabel("Category")
            ChipGroup(
                isLoading = state.isLoadingOptions,
                emptyMessage = "No categories yet. Connect to the internet to load them.",
                options = state.categories.map { it.id to it.name },
                selectedId = state.selectedCategoryId,
                onSelect = onCategorySelected,
            )

            PickerField(
                label = "Date",
                value = dateLabel(state.date, LocalDate.now()),
                error = state.dateError,
                onClick = { showDatePicker = true },
            )

            SectionLabel("Payment method")
            ChipGroup(
                isLoading = state.isLoadingOptions,
                emptyMessage = "No payment methods yet. Connect to the internet to load them.",
                options = state.paymentMethods.map { it.id to it.name },
                selectedId = state.selectedPaymentMethodId,
                onSelect = onPaymentMethodSelected,
            )

            MintfyTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = "Note (optional)",
                error = state.noteError,
                imeAction = ImeAction.Done,
                onImeAction = onSave,
            )

            Text(
                "Defaults to today. Record in three interactions or fewer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ErrorBanner(state.error)
            PrimaryButton("Save expense", onClick = onSave)
            SecondaryButton("Cancel", onClick = onCancel)
        }
    }

    if (showDatePicker) {
        ExpenseDatePicker(
            selected = state.date,
            onConfirm = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** Figma section label: 12 Regular, secondary text. */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Chips that wrap onto new lines, exactly one selected. Shows a spinner while the options load. */
@Composable
private fun ChipGroup(
    isLoading: Boolean,
    emptyMessage: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    when {
        isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        options.isEmpty() -> Text(
            emptyMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (id, name) ->
                SelectChip(label = name, selected = id == selectedId, onClick = { onSelect(id) })
            }
        }
    }
}

/**
 * Figma "Chip": 8dp radius, 14 x 8 padding, 13 Medium label.
 * Selected is green with black text; unselected is the card colour with a 1dp border.
 */
@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(14.dp, 8.dp))
    }
}

/** Looks like the Figma "Input" but opens a picker instead of the keyboard. */
@Composable
private fun PickerField(label: String, value: String, error: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, if (error != null) colors.error else colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/** Material 3 date picker that only allows today or earlier, so missed expenses can be backfilled (FR-C4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePicker(selected: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material date picker works in UTC milliseconds, so convert at the edges only.
    val todayMillis = LocalDate.now().toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "Today · 20 Oct 2026", "Yesterday · 19 Oct 2026", or "Mon · 13 Oct 2026". */
internal fun dateLabel(date: LocalDate, today: LocalDate): String {
    val prefix = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }
    return "$prefix · ${date.format(dateFormat)}"
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview
@Composable
fun AddExpenseContentPreview() {
    MintfyTheme {
        AddExpenseContent(
            state = AddExpenseUiState(
                amount = "1,250",
                note = "Morning coffee",
                categories = DefaultReferenceData.categories
                    .sortedBy { it.spendingType != SpendingType.DISCRETIONARY },
                paymentMethods = DefaultReferenceData.paymentMethods,
                selectedCategoryId = "default_food_coffee",
                selectedPaymentMethodId = "default_cash",
                isLoadingOptions = false,
            ),
            onAmountChange = {},
            onCategorySelected = {},
            onPaymentMethodSelected = {},
            onDateSelected = {},
            onNoteChange = {},
            onSave = {},
            onCancel = {},
            focusAmountOnOpen = false,
        )
    }
}

@Preview
@Composable
fun AddExpenseErrorsPreview() {
    MintfyTheme {
        AddExpenseContent(
            state = AddExpenseUiState(
                amount = "12.345",
                amountError = "Enter an amount like 850 or 850.50.",
                categories = DefaultReferenceData.categories,
                paymentMethods = DefaultReferenceData.paymentMethods,
                selectedCategoryId = "default_rent",
                selectedPaymentMethodId = "default_card",
                isLoadingOptions = false,
            ),
            onAmountChange = {},
            onCategorySelected = {},
            onPaymentMethodSelected = {},
            onDateSelected = {},
            onNoteChange = {},
            onSave = {},
            onCancel = {},
            focusAmountOnOpen = false,
        )
    }
}
