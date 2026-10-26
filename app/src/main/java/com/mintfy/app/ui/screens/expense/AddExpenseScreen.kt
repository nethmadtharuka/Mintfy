package com.mintfy.app.ui.screens.expense

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.DefaultReferenceData
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.ui.components.ChipGroup
import com.mintfy.app.ui.components.ConfirmDeleteDialog
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.SectionLabel
import com.mintfy.app.ui.components.dateLabel
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.LocalDate

@Composable
fun AddExpenseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Adding is instant (it works offline), so close the screen as soon as the ViewModel reports it.
    LaunchedEffect(state.isSaved) {
        if (!state.isSaved) return@LaunchedEffect
        if (state.savedOffline) Toast.makeText(context, offlineMessage(state.isDeleted), Toast.LENGTH_LONG).show()
        onSaved()
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
        onDelete = viewModel::delete,
    )
}

/** Shown when a change is kept on the phone because the server could not be reached. */
fun offlineMessage(deleted: Boolean) =
    if (deleted) "Deleted on this phone. It will sync when you're back online."
    else "Saved on this phone. It will sync when you're back online."

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
    onDelete: () -> Unit = {},
    focusAmountOnOpen: Boolean = true,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusAmountOnOpen) amountFocus.requestFocus() // Figma annotation: amount field has focus on open
    }

    val title = if (state.isEditing) "Edit expense" else "Add expense"
    Scaffold(topBar = { MintfyTopBar(title, onBack = onCancel) }) { innerPadding ->
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

            if (!state.isEditing) {
                Text(
                    "Defaults to today. Record in three interactions or fewer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ErrorBanner(state.error)
            PrimaryButton(if (state.isEditing) "Save changes" else "Save expense", onClick = onSave, isLoading = state.isSaving)
            SecondaryButton("Cancel", onClick = onCancel, enabled = !state.isSaving)
            if (state.isEditing) {
                SecondaryButton("Delete expense", onClick = { confirmDelete = true }, enabled = !state.isSaving)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "expense",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showDatePicker) {
        // Today or earlier only, so missed expenses can be backfilled (FR-C4).
        MintfyDatePicker(
            selected = state.date,
            onConfirm = {
                onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

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
