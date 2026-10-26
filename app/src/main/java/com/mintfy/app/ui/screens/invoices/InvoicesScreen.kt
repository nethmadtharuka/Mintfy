package com.mintfy.app.ui.screens.invoices

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.components.ChipGroup
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyDatePicker
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.dateLabel
import com.mintfy.app.ui.screens.expense.offlineMessage
import com.mintfy.app.ui.screens.income.BASE_CURRENCY
import com.mintfy.app.ui.screens.income.formatCents
import com.mintfy.app.ui.screens.income.statusLabel
import com.mintfy.app.ui.theme.MintfyTheme
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun InvoicesScreen(
    onBack: () -> Unit,
    onOpenInvoice: (id: String) -> Unit,
    onAddIncome: () -> Unit,
    viewModel: InvoicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.savedOffline) {
        if (!state.savedOffline) return@LaunchedEffect
        Toast.makeText(context, offlineMessage(deleted = false), Toast.LENGTH_LONG).show()
        viewModel.onOfflineMessageShown()
    }
    InvoicesContent(
        state = state,
        actions = InvoicesActions(
            onBack = onBack,
            onFilterSelected = viewModel::onFilterSelected,
            onRetry = viewModel::retry,
            onMarkReceived = viewModel::markReceived,
            onOpenInvoice = onOpenInvoice,
            onAddIncome = onAddIncome,
        ),
    )
}

/** Everything the user can do on the screen, so the content stays stateless and previewable. */
data class InvoicesActions(
    val onBack: () -> Unit = {},
    val onFilterSelected: (InvoiceFilter) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMarkReceived: (id: String, receivedOn: LocalDate) -> Unit = { _, _ -> },
    val onOpenInvoice: (id: String) -> Unit = {},
    val onAddIncome: () -> Unit = {},
)

/**
 * Freelance invoices with client, project, amount, due date and status (FR-B4), filtered by status,
 * with the outstanding total (FR-B7). Tap an invoice to edit or delete it in the Add income form;
 * "Mark received" is the one-step shortcut for the common case (UC-03).
 */
@Composable
fun InvoicesContent(state: InvoicesUiState, actions: InvoicesActions) {
    var receivingId by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    Scaffold(topBar = { MintfyTopBar("Invoices", onBack = actions.onBack) }) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (!state.isLoading && state.error == null) {
                item { OutstandingSummaryCard(state.outstanding) }
            }
            item {
                ChipGroup(
                    isLoading = false,
                    emptyMessage = "",
                    options = InvoiceFilter.entries.map { it.name to it.label },
                    selectedId = state.filter.name,
                    onSelect = { actions.onFilterSelected(InvoiceFilter.valueOf(it)) },
                )
            }
            state.actionError?.let { message -> item { ErrorBanner(message) } }

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
                state.invoices.isEmpty() -> item { EmptyInvoices(state.filter, actions) }
                else -> items(state.invoices, key = { it.id!! }) { invoice ->
                    InvoiceItem(
                        invoice = invoice,
                        today = today,
                        isMarking = state.markingId == invoice.id,
                        markEnabled = state.markingId == null,
                        onOpen = { actions.onOpenInvoice(invoice.id!!) },
                        onMarkReceived = { receivingId = invoice.id },
                    )
                }
            }
        }
    }

    receivingId?.let { id ->
        // Defaults to today; the money can't have arrived in the future.
        MintfyDatePicker(
            selected = today,
            title = "Date the payment arrived",
            onConfirm = {
                actions.onMarkReceived(id, it)
                receivingId = null
            },
            onDismiss = { receivingId = null },
        )
    }
}

/** FR-B7: the unpaid total, kept apart from income. Also used on the dashboard. */
@Composable
fun OutstandingSummaryCard(summary: OutstandingSummary, onViewInvoices: (() -> Unit)? = null) {
    MintfyCard {
        Text(
            "Unpaid freelance invoices",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("LKR ${formatCents(summary.totalLkrCents)}", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (summary.count) {
                0 -> "Nothing waiting to be paid."
                1 -> "1 invoice · not counted as income until received"
                else -> "${summary.count} invoices · not counted as income until received"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onViewInvoices != null) {
            TextButton(onClick = onViewInvoices) { Text("View invoices", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** FR-H1: say why the list is empty and offer the next step. */
@Composable
private fun EmptyInvoices(filter: InvoiceFilter, actions: InvoicesActions) {
    MintfyCard {
        val message = when (filter) {
            InvoiceFilter.OUTSTANDING -> "No unpaid invoices."
            InvoiceFilter.ALL -> "No freelance income yet."
            else -> "No ${filter.label.lowercase()} invoices."
        }
        Text(message, style = MaterialTheme.typography.titleSmall)
        Text(
            "Add freelance income with the status Expected or Invoiced to track it here until it's paid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton("Add freelance income", onClick = actions.onAddIncome)
        if (filter != InvoiceFilter.ALL) {
            SecondaryButton("Show all invoices", onClick = { actions.onFilterSelected(InvoiceFilter.ALL) })
        }
    }
}

/** Client, project and milestone, the amount, and the status with its due or received date. */
@Composable
private fun InvoiceItem(
    invoice: IncomeEntry,
    today: LocalDate,
    isMarking: Boolean,
    markEnabled: Boolean,
    onOpen: () -> Unit,
    onMarkReceived: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val overdue = invoice.isOverdue(today)
    val statusLine = when {
        invoice.status == IncomeStatus.RECEIVED -> "Received · ${dateLabel(invoice.date, today)}"
        overdue -> "Overdue · was due ${dateLabel(invoice.dueDate!!, today)}"
        invoice.dueDate != null -> "${statusLabel(invoice.status)} · due ${dateLabel(invoice.dueDate, today)}"
        else -> "${statusLabel(invoice.status)} · expected ${dateLabel(invoice.date, today)}"
    }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (overdue) colors.error else colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    invoice.invoiceTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("LKR ${formatCents(invoice.lkrAmountCents)}", style = MaterialTheme.typography.titleSmall)
            }
            val details = listOfNotNull(
                invoice.project.takeIf { invoice.client != null }, // the title already shows it otherwise
                invoice.milestone,
                if (invoice.currency == BASE_CURRENCY) null else "${invoice.currency} ${formatCents(invoice.originalAmountCents)}",
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(
                statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) colors.error else colors.onSurfaceVariant,
            )
            if (invoice.isOutstandingInvoice()) {
                if (isMarking) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onMarkReceived, enabled = markEnabled) {
                        Text("Mark received", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * The dashboard's unpaid-invoices card (FR-B7). It has its own ViewModel so the dashboard does not need to
 * know how invoices are loaded; the total comes from the same [outstandingSummary] as the Invoices screen.
 */
@Composable
fun UnpaidInvoicesCard(onViewInvoices: () -> Unit, viewModel: InvoicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.isLoading -> MintfyCard {
            Text("Unpaid freelance invoices", style = MaterialTheme.typography.bodySmall)
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        state.error != null -> MintfyCard {
            Text("Unpaid freelance invoices", style = MaterialTheme.typography.bodySmall)
            ErrorBanner(state.error)
            TextButton(onClick = viewModel::retry) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
        }
        else -> OutstandingSummaryCard(state.outstanding, onViewInvoices)
    }
}

private val previewInvoices = listOf(
    IncomeEntry(
        sourceId = "src_freelance", sourceType = IncomeSourceType.FREELANCE, status = IncomeStatus.INVOICED,
        date = LocalDate.now().plusDays(3), currency = "LKR", originalAmountCents = 4_500_000,
        exchangeRate = BigDecimal.ONE, lkrAmountCents = 4_500_000, client = "Lanka Bites",
        project = "WordPress site", milestone = "Milestone 2", dueDate = LocalDate.now().minusDays(2), id = "i1",
    ),
    IncomeEntry(
        sourceId = "src_freelance", sourceType = IncomeSourceType.FREELANCE, status = IncomeStatus.EXPECTED,
        date = LocalDate.now().plusDays(20), currency = "LKR", originalAmountCents = 2_000_000,
        exchangeRate = BigDecimal.ONE, lkrAmountCents = 2_000_000, project = "React dashboard", id = "i2",
    ),
)

@Preview
@Composable
fun InvoicesContentPreview() {
    MintfyTheme {
        InvoicesContent(
            state = InvoicesUiState(
                invoices = previewInvoices,
                outstanding = OutstandingSummary(6_500_000, 2),
                isLoading = false,
            ),
            actions = InvoicesActions(),
        )
    }
}

@Preview
@Composable
fun InvoicesEmptyPreview() {
    MintfyTheme {
        InvoicesContent(state = InvoicesUiState(isLoading = false), actions = InvoicesActions())
    }
}
