package com.mintfy.app.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.DefaultReferenceData
import com.mintfy.app.ui.components.ChipGroup
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PickerField
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.SectionLabel
import com.mintfy.app.ui.components.dateLabel
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.LocalDate

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenEntry: (HistoryRow) -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        actions = HistoryActions(
            onBack = onBack,
            onFromSelected = viewModel::onFromSelected,
            onToSelected = viewModel::onToSelected,
            onCategorySelected = viewModel::onCategorySelected,
            onRetry = viewModel::retry,
            onOpenEntry = onOpenEntry,
            onAddExpense = onAddExpense,
            onAddIncome = onAddIncome,
        ),
    )
}

/** Everything the user can do on the screen, so the content stays stateless and previewable. */
data class HistoryActions(
    val onBack: () -> Unit = {},
    val onFromSelected: (LocalDate) -> Unit = {},
    val onToSelected: (LocalDate) -> Unit = {},
    val onCategorySelected: (String?) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenEntry: (HistoryRow) -> Unit = {},
    val onAddExpense: () -> Unit = {},
    val onAddIncome: () -> Unit = {},
)

private const val ALL_CATEGORIES = "" // chip ID for "no category filter"

/** Past income and expenses with date-range and category filters (FR-C7). Tap an entry to edit or delete it (FR-C5). */
@Composable
fun HistoryContent(state: HistoryUiState, actions: HistoryActions) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "from", "to" or null
    val today = LocalDate.now()

    Scaffold(topBar = { MintfyTopBar("History", onBack = actions.onBack) }) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField("From", dateLabel(state.range.from, today), error = null, onClick = { picking = "from" })
                    PickerField("To", dateLabel(state.range.to, today), error = null, onClick = { picking = "to" })
                    SectionLabel("Category")
                    ChipGroup(
                        isLoading = false,
                        emptyMessage = "",
                        options = listOf(ALL_CATEGORIES to "All") + state.categories.map { it.id to it.name },
                        selectedId = state.selectedCategoryId ?: ALL_CATEGORIES,
                        onSelect = { actions.onCategorySelected(it.ifEmpty { null }) },
                    )
                }
            }

            when {
                state.isLoading -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 24.dp).size(32.dp))
                }
                state.error != null -> item {
                    // FR-H2: say what went wrong and offer a retry.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ErrorBanner(state.error)
                        SecondaryButton("Try again", onClick = actions.onRetry)
                    }
                }
                state.rows.isEmpty() -> item { EmptyHistory(state.selectedCategoryId != null, actions) }
                else -> items(state.rows, key = { "${it.kind}_${it.id}" }) { row ->
                    HistoryRowItem(row, today, onClick = { actions.onOpenEntry(row) })
                }
            }
        }
    }

    picking?.let { which ->
        // Any date can be picked: expected invoices can be dated in the future.
        MintfyDatePicker(
            selected = if (which == "from") state.range.from else state.range.to,
            allowFuture = true,
            onConfirm = {
                if (which == "from") actions.onFromSelected(it) else actions.onToSelected(it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** FR-H1: say why the list is empty and offer the next step. */
@Composable
private fun EmptyHistory(isFiltered: Boolean, actions: HistoryActions) {
    MintfyCard {
        if (isFiltered) {
            Text("No expenses in this category for these dates.", style = MaterialTheme.typography.titleSmall)
            SecondaryButton("Show all categories", onClick = { actions.onCategorySelected(null) })
        } else {
            Text("Nothing recorded for these dates yet.", style = MaterialTheme.typography.titleSmall)
            Text(
                "Add an expense or income, or pick other dates. Missed entries can be backdated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Add expense", onClick = actions.onAddExpense)
            SecondaryButton("Add income", onClick = actions.onAddIncome)
        }
    }
}

/** One entry: what and when on the left, the signed LKR amount on the right (green for money in). */
@Composable
private fun HistoryRowItem(row: HistoryRow, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.detail.isNotEmpty()) {
                    Text(
                        row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(dateLabel(row.date, today), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(
                amountLabel(row.amountCents),
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    !row.received -> colors.onSurfaceVariant // not money yet (FR-E6)
                    row.amountCents > 0 -> colors.primary
                    else -> colors.onSurface
                },
            )
        }
    }
}

@Preview
@Composable
fun HistoryContentPreview() {
    val today = LocalDate.now()
    MintfyTheme {
        HistoryContent(
            state = HistoryUiState(
                range = DateRange.monthOf(today),
                categories = DefaultReferenceData.categories,
                rows = listOf(
                    HistoryRow("e1", EntryKind.EXPENSE, today, "Food & Coffee", "Cash · Morning coffee", -85_000),
                    HistoryRow("i1", EntryKind.INCOME, today.minusDays(1), "Startup salary", "", 13_250_000),
                    HistoryRow(
                        "i2", EntryKind.INCOME, today.minusDays(2), "Freelance",
                        "Invoiced, not received yet · Lanka Bites", 4_500_000, received = false,
                    ),
                ),
                isLoading = false,
            ),
            actions = HistoryActions(),
        )
    }
}

@Preview
@Composable
fun HistoryEmptyPreview() {
    MintfyTheme {
        HistoryContent(state = HistoryUiState(isLoading = false), actions = HistoryActions())
    }
}
