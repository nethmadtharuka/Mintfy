package com.mintfy.app.ui.screens.income

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.screens.dashboard.EstimateCheck
import com.mintfy.app.ui.screens.dashboard.EstimatePosition
import com.mintfy.app.ui.screens.dashboard.LoadingCard
import com.mintfy.app.ui.screens.dashboard.lkr
import com.mintfy.app.ui.screens.dashboard.monthTitle
import com.mintfy.app.ui.screens.expense.offlineMessage
import com.mintfy.app.ui.screens.invoices.OutstandingSummary
import com.mintfy.app.ui.screens.invoices.invoiceTitle
import com.mintfy.app.ui.theme.MintfyTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Income screen can ask for. Grouped so the stateless content stays readable. */
data class IncomeActions(
    val onBack: (() -> Unit)? = null,
    val onRetry: () -> Unit = {},
    val onAddIncome: () -> Unit = {},
    val onOpenEntry: (id: String) -> Unit = {},
    val onViewInvoices: () -> Unit = {},
    val onMarkNextInvoicePaid: () -> Unit = {},
    val onEditEstimate: () -> Unit = {},
    val onEstimateMinChange: (String) -> Unit = {},
    val onEstimateMaxChange: (String) -> Unit = {},
    val onSaveEstimate: () -> Unit = {},
    val onCloseEstimate: () -> Unit = {},
    val onEditSource: (id: String) -> Unit = {},
    val onSourceNameChange: (String) -> Unit = {},
    val onSourceExpectedAmountChange: (String) -> Unit = {},
    val onSaveSource: () -> Unit = {},
    val onArchiveSource: () -> Unit = {},
    val onCloseSource: () -> Unit = {},
)

@Composable
fun IncomeScreen(
    onAddIncome: () -> Unit,
    onOpenEntry: (id: String) -> Unit,
    onViewInvoices: () -> Unit,
    onBack: (() -> Unit)? = null, // null when Income is shown as a bottom-navigation tab
    bottomBar: @Composable () -> Unit = {},
    viewModel: IncomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.savedOffline) {
        if (!state.savedOffline) return@LaunchedEffect
        Toast.makeText(context, offlineMessage(deleted = false), Toast.LENGTH_LONG).show()
        viewModel.onOfflineMessageShown()
    }
    // Roll over to the new month on the 1st when the screen is shown again.
    LaunchedEffect(Unit) { viewModel.refresh() }

    IncomeContent(
        state = state,
        actions = IncomeActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onAddIncome = onAddIncome,
            onOpenEntry = onOpenEntry,
            onViewInvoices = onViewInvoices,
            onMarkNextInvoicePaid = viewModel::markNextInvoicePaid,
            onEditEstimate = viewModel::openEstimateEditor,
            onEstimateMinChange = viewModel::onEstimateMinChange,
            onEstimateMaxChange = viewModel::onEstimateMaxChange,
            onSaveEstimate = viewModel::saveEstimate,
            onCloseEstimate = viewModel::closeEstimateEditor,
            onEditSource = viewModel::openSourceEditor,
            onSourceNameChange = viewModel::onSourceNameChange,
            onSourceExpectedAmountChange = viewModel::onSourceExpectedAmountChange,
            onSaveSource = viewModel::saveSource,
            onArchiveSource = viewModel::archiveSource,
            onCloseSource = viewModel::closeSourceEditor,
        ),
        bottomBar = bottomBar,
    )
}

/** Figma "04 Income & freelance invoices". Stateless: it only draws [state] and reports what the user does. */
@Composable
fun IncomeContent(state: IncomeUiState, actions: IncomeActions, bottomBar: @Composable () -> Unit = {}) {
    Scaffold(
        topBar = { MintfyTopBar("Income", onBack = actions.onBack) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.isLoading) LoadingCard()
            if (state.error != null) {
                ErrorBanner(state.error)
                SecondaryButton("Try again", onClick = actions.onRetry)
            }
            val overview = state.overview
            if (overview != null) {
                ReceivedThisMonthCard(overview, actions)
                InvoicesCard(overview.outstanding, overview.nextInvoice, state.isMarkingPaid, state.actionError, actions)
                overview.cryptoYearToDate?.let { CryptoYearToDateCard(it) }
                EstimateCard(overview.estimate, actions.onEditEstimate)
                SourcesCard(state.sources, actions.onEditSource)
            }
            PrimaryButton("Add income", onClick = actions.onAddIncome)
        }
    }
    state.estimateEditor?.let { EstimateDialog(it, actions) }
    state.sourceEditor?.let { SourceDialog(it, actions) }
}

/** Figma "Received this month": green 28 Bold total, then one row per payment (FR-B2, FR-B6). */
@Composable
private fun ReceivedThisMonthCard(overview: IncomeOverview, actions: IncomeActions) {
    MintfyCard {
        Label("Received in ${monthTitle(overview.month)}")
        Text(lkr(overview.receivedCents), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        if (overview.rows.isEmpty()) {
            Label("Nothing received yet this month. Only money that has arrived counts.")
        }
        overview.rows.forEach { row ->
            ValueRow(
                title = row.title,
                detail = row.detail,
                amount = if (row.lkrAmountCents < 0) lkr(row.lkrAmountCents) else formatCents(row.lkrAmountCents),
                amountIsWarning = row.lkrAmountCents < 0,
                onClick = { actions.onOpenEntry(row.id) },
            )
        }
    }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Figma "Freelance invoices": the soonest-due unpaid invoice, "Mark as paid", and a reminder (FR-B4, FR-B7). */
@Composable
private fun InvoicesCard(
    outstanding: OutstandingSummary,
    next: IncomeEntry?,
    isMarking: Boolean,
    error: String?,
    actions: IncomeActions,
) {
    MintfyCard {
        Text("Freelance invoices", style = MaterialTheme.typography.labelLarge)
        if (next == null) {
            Label("No unpaid invoices.")
        } else {
            val due = (next.dueDate ?: next.date).format(dayMonth)
            ValueRow(
                title = next.invoiceTitle(),
                detail = "${statusLabel(next.status)} · due $due",
                amount = lkr(next.lkrAmountCents),
                amountIsMuted = true,
                onClick = next.id?.let { id -> { actions.onOpenEntry(id) } },
            )
            CompactOutlinedButton(
                text = if (isMarking) "Marking…" else "Mark as paid",
                enabled = !isMarking,
                onClick = actions.onMarkNextInvoicePaid,
            )
            if (outstanding.count > 1) {
                Label("${outstanding.count} unpaid invoices, ${lkr(outstanding.totalLkrCents)} in total.")
            }
        }
        ErrorBanner(error)
        Label("Unpaid invoices are not counted as income.")
        TextButton(onClick = actions.onViewInvoices, contentPadding = PaddingValues(0.dp)) {
            Text("View all invoices", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** FR-B9: Kavindu's crypto net position for the year, gains minus losses. */
@Composable
private fun CryptoYearToDateCard(ytd: CryptoYearToDate) {
    MintfyCard {
        Label("Crypto net in ${ytd.year} (gains minus losses)")
        Text(
            lkr(ytd.netCents),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = if (ytd.netCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Label("From ${ytd.entryCount} recorded result${if (ytd.entryCount == 1) "" else "s"} since 1 January.")
    }
}

/** Figma "Income estimate (not income)": his guess, kept apart from real income (FR-B8, E-05). */
@Composable
private fun EstimateCard(estimate: EstimateCheck?, onEdit: () -> Unit) {
    MintfyCard {
        Label("Income estimate (not income)")
        if (estimate == null) {
            Label("Add the range you think you earn each month to see how it compares with what actually arrives.")
        } else {
            Text(
                "${lkr(estimate.minCents)} – ${formatCents(estimate.maxCents)}",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            )
            Label(
                when (estimate.position) {
                    EstimatePosition.BELOW -> "This month so far: ${lkr(estimate.minCents - estimate.actualCents)} below your lowest estimate."
                    EstimatePosition.WITHIN -> "This month so far: within your estimate."
                    EstimatePosition.ABOVE -> "This month so far: ${lkr(estimate.actualCents - estimate.maxCents)} above your highest estimate."
                }
            )
        }
        CompactOutlinedButton(text = if (estimate == null) "Set estimate" else "Edit estimate", onClick = onEdit)
    }
}

/** The user's income sources; tap one to rename it, change the expected salary or archive it (FR-B1, FR-B3). */
@Composable
private fun SourcesCard(sources: List<IncomeSource>, onEdit: (String) -> Unit) {
    if (sources.isEmpty()) return
    MintfyCard {
        Text("Income sources", style = MaterialTheme.typography.labelLarge)
        sources.forEach { s ->
            ValueRow(
                title = s.name,
                detail = listOfNotNull(
                    s.type.displayName(),
                    s.defaultCurrency.takeIf { it != BASE_CURRENCY },
                    s.expectedAmountCents?.let { "expected ${lkr(it)}" },
                ).joinToString(" · "),
                amount = "Edit",
                amountIsMuted = true,
                onClick = { onEdit(s.id) },
            )
        }
    }
}

/** Figma "Row": 15 Medium title, 12 Regular detail, 15 SemiBold amount on the right. */
@Composable
private fun ValueRow(
    title: String,
    detail: String,
    amount: String,
    amountIsMuted: Boolean = false,
    amountIsWarning: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) Label(detail)
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = when {
                amountIsWarning -> colors.error
                amountIsMuted -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
        )
    }
}

/** Figma "Mark paid": 40dp tall outlined pill, 1.5dp green border, 14 SemiBold green label. */
@Composable
private fun CompactOutlinedButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, if (enabled) primary else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = primary),
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** FR-B8: the monthly income range Kavindu believes he earns. Both blank removes it. */
@Composable
private fun EstimateDialog(editor: EstimateEditorState, actions: IncomeActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseEstimate,
        title = { Text("Monthly income estimate", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("Your best guess, kept apart from real income so you can compare the two. Leave both blank to remove it.")
                MintfyTextField(
                    value = editor.min,
                    onValueChange = actions.onEstimateMinChange,
                    label = "Lowest, LKR",
                    error = editor.minError,
                    enabled = !editor.isSaving,
                    keyboardType = KeyboardType.Decimal,
                )
                MintfyTextField(
                    value = editor.max,
                    onValueChange = actions.onEstimateMaxChange,
                    label = "Highest, LKR",
                    error = editor.maxError,
                    enabled = !editor.isSaving,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    onImeAction = actions.onSaveEstimate,
                )
                ErrorBanner(editor.error)
            }
        },
        confirmButton = { DialogConfirm("Save", editor.isSaving, actions.onSaveEstimate) },
        dismissButton = {
            TextButton(onClick = actions.onCloseEstimate, enabled = !editor.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** Rename a source, change the expected salary, or archive it. The type can't change (firestore.rules). */
@Composable
private fun SourceDialog(editor: SourceEditorState, actions: IncomeActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseSource,
        title = { Text("Edit income source", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("Type: ${editor.type.displayName()} (can't be changed)")
                MintfyTextField(
                    value = editor.name,
                    onValueChange = actions.onSourceNameChange,
                    label = "Name",
                    error = editor.nameError,
                    enabled = !editor.isSaving,
                )
                if (editor.isSalary) {
                    MintfyTextField(
                        value = editor.expectedAmount,
                        onValueChange = actions.onSourceExpectedAmountChange,
                        label = "Expected salary, LKR (optional)",
                        error = editor.expectedAmountError,
                        enabled = !editor.isSaving,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        onImeAction = actions.onSaveSource,
                    )
                }
                ErrorBanner(editor.error)
                Label("Archiving hides this source when adding income. Income already recorded keeps its name.")
                TextButton(onClick = actions.onArchiveSource, enabled = !editor.isSaving, contentPadding = PaddingValues(0.dp)) {
                    Text("Archive source", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { DialogConfirm("Save", editor.isSaving, actions.onSaveSource) },
        dismissButton = {
            TextButton(onClick = actions.onCloseSource, enabled = !editor.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun DialogConfirm(text: String, isSaving: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !isSaving) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------- Previews (Figma sample data) ----------

private val previewSources = listOf(
    IncomeSource("src_salary", "Startup salary", IncomeSourceType.SALARY, "LKR", expectedAmountCents = 12_000_000),
    IncomeSource("src_adsense", "Google AdSense", IncomeSourceType.ADSENSE, "USD"),
    IncomeSource("src_freelance", "Freelance", IncomeSourceType.FREELANCE, "LKR"),
)

private val previewOverview = IncomeOverview(
    month = YearMonth.of(2026, 9),
    receivedCents = 14_050_000,
    rows = listOf(
        IncomeRow("i1", "Startup salary", "Base 120,000 + bonus 8,000 · LKR 8,000 above your expected salary", 12_800_000),
        IncomeRow("i2", "Google AdSense", "USD 42 at 297.62 · Received 28 Sep", 1_250_000),
    ),
    cryptoYearToDate = CryptoYearToDate(2026, 200_000, 2),
    outstanding = OutstandingSummary(4_500_000, 1),
    nextInvoice = IncomeEntry(
        sourceId = "src_freelance", sourceType = IncomeSourceType.FREELANCE, status = IncomeStatus.INVOICED,
        date = LocalDate.of(2026, 10, 15), currency = "LKR", originalAmountCents = 4_500_000,
        exchangeRate = BigDecimal.ONE, lkrAmountCents = 4_500_000, client = "Invoice #014",
        dueDate = LocalDate.of(2026, 10, 15), id = "i5",
    ),
    estimate = EstimateCheck(16_000_000, 21_000_000, 14_050_000),
)

@Preview
@Composable
fun IncomeContentPreview() {
    MintfyTheme {
        IncomeContent(
            state = IncomeUiState(overview = previewOverview, sources = previewSources, isLoading = false),
            actions = IncomeActions(),
        )
    }
}

@Preview
@Composable
fun IncomeEstimateDialogPreview() {
    MintfyTheme {
        IncomeContent(
            state = IncomeUiState(
                overview = previewOverview,
                sources = previewSources,
                isLoading = false,
                estimateEditor = EstimateEditorState("210,000", "160,000", maxError = "The highest amount can't be below the lowest."),
            ),
            actions = IncomeActions(),
        )
    }
}
