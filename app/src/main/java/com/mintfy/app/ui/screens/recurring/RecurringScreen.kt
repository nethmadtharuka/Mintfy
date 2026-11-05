package com.mintfy.app.ui.screens.recurring

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.PaymentMethodType
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.ui.components.ChipGroup
import com.mintfy.app.ui.components.ConfirmDeleteDialog
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.SectionLabel
import com.mintfy.app.ui.components.dateLabel
import com.mintfy.app.ui.screens.dashboard.ARCHIVED_CATEGORY_NAME
import com.mintfy.app.ui.screens.dashboard.LoadingCard
import com.mintfy.app.ui.screens.dashboard.lkr
import com.mintfy.app.ui.screens.expense.offlineMessage
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.LocalDate

/** Everything the user can do on the screen, so the content stays stateless and previewable. */
data class RecurringActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpen: (id: String) -> Unit = {},
    val onRecord: (id: String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onCategorySelected: (String) -> Unit = {},
    val onPaymentMethodSelected: (String) -> Unit = {},
    val onFrequencySelected: (RecurringFrequency) -> Unit = {},
    val onNextDueDateSelected: (LocalDate) -> Unit = {},
    val onSave: () -> Unit = {},
    val onTogglePaused: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
)

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onRecord: (id: String) -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.savedOffline) {
        if (!state.savedOffline) return@LaunchedEffect
        Toast.makeText(context, offlineMessage(deleted = false), Toast.LENGTH_LONG).show()
        viewModel.onOfflineMessageShown()
    }
    // "Due" follows the calendar when the screen is shown again.
    LaunchedEffect(Unit) { viewModel.refresh() }

    RecurringContent(
        state = state,
        actions = RecurringActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onAdd = viewModel::openNew,
            onOpen = viewModel::openItem,
            onRecord = onRecord,
            onNameChange = viewModel::onNameChange,
            onAmountChange = viewModel::onAmountChange,
            onCategorySelected = viewModel::onCategorySelected,
            onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
            onFrequencySelected = viewModel::onFrequencySelected,
            onNextDueDateSelected = viewModel::onNextDueDateSelected,
            onSave = viewModel::save,
            onTogglePaused = viewModel::togglePaused,
            onAskDelete = viewModel::askDelete,
            onCancelDelete = viewModel::cancelDelete,
            onDelete = viewModel::delete,
            onClose = viewModel::closeEditor,
        ),
    )
}

/** Figma "09 Recurring". Stateless: it only draws [state] and reports what the user does. */
@Composable
fun RecurringContent(state: RecurringUiState, actions: RecurringActions) {
    Scaffold(topBar = { MintfyTopBar("Recurring", onBack = actions.onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.error != null) {
                ErrorBanner(state.error)
                SecondaryButton("Try again", onClick = actions.onRetry)
            }
            val groups = state.groups
            when {
                state.isLoading -> LoadingCard()
                groups.isEmpty -> MintfyCard {
                    Text("No recurring expenses yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add rent, utilities, the gym or a subscription once, and Mintfy reminds you when each is due.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    MonthlyTotalCard(state.monthlyCents)
                    RecurringGroup("Due now", groups.due, state, actions)
                    RecurringGroup("Coming up", groups.upcoming, state, actions)
                    RecurringGroup("Paused", groups.paused, state, actions)
                }
            }
            SectionLabel("When one is due, record it in one tap. Tap an item to change it or pause it.")
            PrimaryButton("Add recurring expense", onClick = actions.onAdd)
        }
    }
    state.editor?.let { RecurringDialog(it, state, actions) }
}

/** All active recurring costs as one monthly figure: the fixed load Kavindu carries each month. */
@Composable
private fun MonthlyTotalCard(monthlyCents: Long) {
    MintfyCard {
        SectionLabel("Recurring costs in an average month")
        Text(lkr(monthlyCents), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun RecurringGroup(
    title: String,
    items: List<RecurringExpense>,
    state: RecurringUiState,
    actions: RecurringActions,
) {
    if (items.isEmpty()) return
    SectionLabel(title)
    MintfyCard {
        items.forEach { item ->
            RecurringRow(
                item = item,
                categoryName = state.categoryNames[item.categoryId] ?: ARCHIVED_CATEGORY_NAME,
                today = state.today,
                onClick = { actions.onOpen(item.id) },
                onRecord = { actions.onRecord(item.id) },
            )
        }
    }
}

/** Figma row: name over "Monthly · 1st", amount on the right. The due state is added to the subtitle. */
@Composable
private fun RecurringRow(
    item: RecurringExpense,
    categoryName: String,
    today: LocalDate,
    onClick: () -> Unit,
    onRecord: () -> Unit,
) {
    val isDue = item.isDue(today)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${scheduleLabel(item.frequency, item.nextDueDate)} · $categoryName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dueLabel(item, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(lkr(item.amountCents), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        }
        if (isDue) {
            // One tap to add this period's expense; the amount can be changed before saving (FR-D3).
            SecondaryButton("Record this ${periodName(item.frequency)}'s ${item.name}", onClick = onRecord)
        }
    }
}

private fun periodName(frequency: RecurringFrequency): String = when (frequency) {
    RecurringFrequency.WEEKLY -> "week"
    RecurringFrequency.MONTHLY -> "month"
    RecurringFrequency.YEARLY -> "year"
}

@Composable
private fun RecurringDialog(editor: RecurringEditorState, state: RecurringUiState, actions: RecurringActions) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = actions.onClose,
        title = {
            Text(
                if (editor.isNew) "Add recurring expense" else "Edit recurring expense",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                MintfyTextField(
                    value = editor.name,
                    onValueChange = actions.onNameChange,
                    label = "Name",
                    error = editor.errors.name,
                    enabled = !editor.isSaving,
                )
                MintfyTextField(
                    value = editor.amount,
                    onValueChange = actions.onAmountChange,
                    label = "Usual amount, LKR",
                    error = editor.errors.amount,
                    enabled = !editor.isSaving,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                )
                SectionLabel("How often")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "",
                    options = RecurringFrequency.entries.map { it.name to it.displayName() },
                    selectedId = editor.frequency.name,
                    onSelect = { actions.onFrequencySelected(RecurringFrequency.valueOf(it)) },
                )
                PickerField(
                    label = if (editor.isNew) "First due" else "Next due",
                    value = dateLabel(editor.nextDueDate, state.today),
                    error = null,
                    onClick = { if (!editor.isSaving) pickingDate = true },
                )
                SectionLabel("Category")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "No categories yet. Connect to the internet to load them.",
                    options = state.categories.map { it.id to it.name },
                    selectedId = editor.categoryId,
                    onSelect = actions.onCategorySelected,
                )
                editor.errors.category?.let { FieldError(it) }
                SectionLabel("Paid with")
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "No payment methods yet. Connect to the internet to load them.",
                    options = state.paymentMethods.map { it.id to it.name },
                    selectedId = editor.paymentMethodId,
                    onSelect = actions.onPaymentMethodSelected,
                )
                editor.errors.paymentMethod?.let { FieldError(it) }
                ErrorBanner(editor.error)
                if (!editor.isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = actions.onTogglePaused,
                            enabled = !editor.isSaving,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(if (editor.active) "Pause" else "Resume", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(
                            onClick = actions.onAskDelete,
                            enabled = !editor.isSaving,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("Delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSave, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (editor.isNew) "Add" else "Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onClose, enabled = !editor.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )

    if (pickingDate) {
        MintfyDatePicker(
            selected = editor.nextDueDate,
            onConfirm = { actions.onNextDueDateSelected(it); pickingDate = false },
            onDismiss = { pickingDate = false },
            allowFuture = true,
            title = if (editor.isNew) "First due" else "Next due",
        )
    }
    if (editor.confirmDelete) {
        ConfirmDeleteDialog(
            what = "recurring expense",
            onConfirm = actions.onDelete,
            onDismiss = actions.onCancelDelete,
        )
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

// ---------- Previews (Figma sample data) ----------

private val previewToday = LocalDate.of(2026, 10, 5)

private val previewItems = listOf(
    RecurringExpense("r1", "Rent", "cat_rent", "pm_card", 3_400_000, RecurringFrequency.MONTHLY, LocalDate.of(2026, 10, 1)),
    RecurringExpense("r2", "Utilities", "cat_utilities", "pm_card", 980_000, RecurringFrequency.MONTHLY, LocalDate.of(2026, 10, 5)),
    RecurringExpense("r3", "Gym", "cat_gym", "pm_account", 500_000, RecurringFrequency.MONTHLY, LocalDate.of(2026, 10, 10)),
    RecurringExpense("r4", "Subscriptions", "cat_subscriptions", "pm_card", 320_000, RecurringFrequency.MONTHLY, LocalDate.of(2026, 10, 12)),
)

private val previewState = RecurringUiState(
    today = previewToday,
    groups = groupRecurring(previewItems, previewToday),
    monthlyCents = monthlyEquivalentCents(previewItems),
    categoryNames = mapOf(
        "cat_rent" to "Rent", "cat_utilities" to "Utilities", "cat_gym" to "Gym", "cat_subscriptions" to "Subscriptions",
    ),
    categories = listOf(
        Category("cat_rent", "Rent", SpendingType.COMMITTED),
        Category("cat_utilities", "Utilities", SpendingType.COMMITTED),
        Category("cat_subscriptions", "Subscriptions", SpendingType.COMMITTED),
    ),
    paymentMethods = listOf(
        PaymentMethod("pm_card", "Card", PaymentMethodType.CARD),
        PaymentMethod("pm_cash", "Cash", PaymentMethodType.CASH),
    ),
    isLoading = false,
)

@Preview
@Composable
fun RecurringContentPreview() {
    MintfyTheme { RecurringContent(state = previewState, actions = RecurringActions()) }
}

@Preview
@Composable
fun RecurringDialogPreview() {
    MintfyTheme {
        RecurringContent(
            state = previewState.copy(
                editor = RecurringEditorState(
                    id = "r1", name = "Rent", amount = "34,000", categoryId = "cat_rent", paymentMethodId = "pm_card",
                    nextDueDate = LocalDate.of(2026, 10, 1),
                ),
            ),
            actions = RecurringActions(),
        )
    }
}
