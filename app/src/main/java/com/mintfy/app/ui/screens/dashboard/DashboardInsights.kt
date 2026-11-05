package com.mintfy.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.screens.income.formatCents
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

// Dashboard cards from the Figma "02 Dashboard" frame, plus the SRS views the frame leaves out
// (top categories FR-F3, income by source FR-F6, estimate FR-B8) drawn in the same card style.

/** The month as the Figma top bar shows it: "September 2026". */
fun monthTitle(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"

/** "LKR 15,500", or "−LKR 2,000" for a negative amount. */
fun lkr(cents: Long): String = (if (cents < 0) "−" else "") + "LKR ${formatCents(abs(cents))}"

/**
 * The insight cards above the goal card (savings), and below it (split, top categories, income, estimate).
 * Handles every state so the dashboard is never blank: loading, error with retry (keeping the last figures
 * visible, FR-H2) and an empty month with the next action (FR-H1).
 */
@Composable
fun DashboardStatus(state: DashboardUiState, onRetry: () -> Unit) {
    if (state.isLoading) LoadingCard()
    if (state.error != null) {
        ErrorBanner(state.error)
        SecondaryButton("Try again", onClick = onRetry)
    }
}

/** A card-shaped spinner, so loading never leaves a blank gap. */
@Composable
fun LoadingCard() {
    MintfyCard {
        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

/** Figma "Savings this month": green 30 Bold amount, then "Received … − Expenses …" (FR-F1, A-01). */
@Composable
fun SavingsThisMonthCard(summary: MonthlySummary) {
    MintfyCard {
        CardLabel("Savings this month")
        Text(
            lkr(summary.savingsCents),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp),
            color = if (summary.savingsCents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        CardLabel("Received ${formatCents(summary.receivedIncomeCents)} − Expenses ${formatCents(summary.expensesCents)}")
    }
}

/** Shown instead of the figures when nothing has been recorded this month yet (FR-H1). */
@Composable
fun EmptyMonthCard(month: YearMonth, onAddExpense: () -> Unit, onAddIncome: () -> Unit) {
    MintfyCard {
        Text("Nothing recorded for ${monthTitle(month)} yet", style = MaterialTheme.typography.titleMedium)
        CardLabel("Add today's spending or your latest income to see where your money goes.")
        PrimaryButton("Add expense", onClick = onAddExpense)
        SecondaryButton("Add income", onClick = onAddIncome)
    }
}

/**
 * Figma "Where your money went": committed and discretionary rows, with the SRS donut (FR-F2)
 * in place of the frame's split bar. Discretionary is green, the part Kavindu can change.
 */
@Composable
fun MoneySplitCard(summary: MonthlySummary) {
    MintfyCard {
        Text("Where your money went", style = MaterialTheme.typography.labelLarge)
        if (summary.expensesCents == 0L) {
            CardLabel("No spending recorded this month yet.")
            return@MintfyCard
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DonutChart(committedShare = summary.committedShare, diameter = 112.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                SplitRow(
                    title = "Committed",
                    detail = groupNames(summary, SpendingType.COMMITTED),
                    amountCents = summary.committedCents,
                    highlight = false,
                )
                SplitRow(
                    title = "Discretionary",
                    detail = groupNames(summary, SpendingType.DISCRETIONARY),
                    amountCents = summary.discretionaryCents,
                    highlight = true,
                )
            }
        }
    }
}

@Composable
private fun SplitRow(title: String, detail: String, amountCents: Long, highlight: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
        Text(
            lkr(amountCents),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail.isNotEmpty()) {
            CardLabel(detail, maxLines = 2)
        }
    }
}

/** "Rent, utilities, gym": the categories in that group that were used this month, biggest first. */
private fun groupNames(summary: MonthlySummary, type: SpendingType): String =
    summary.topCategories.filter { it.spendingType == type }.take(4)
        .mapIndexed { i, c -> if (i == 0) c.name else c.name.lowercase() }
        .joinToString(", ")

/** FR-F3: biggest costs first; the largest discretionary one is marked as the one to look at first. */
@Composable
fun TopCategoriesCard(summary: MonthlySummary, maxRows: Int = 5) {
    if (summary.topCategories.isEmpty()) return
    MintfyCard {
        Text("Top spending", style = MaterialTheme.typography.labelLarge)
        summary.topCategories.take(maxRows).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (c.isLargestDiscretionary) {
                        HighlightBadge("Biggest cost you can cut")
                    }
                }
                Text(
                    lkr(c.totalCents),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (c.isLargestDiscretionary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** FR-F6: where this month's received income came from. A crypto loss shows as a negative line. */
@Composable
fun IncomeBySourceCard(summary: MonthlySummary) {
    if (summary.incomeBySource.isEmpty()) return
    MintfyCard {
        Text("Income by source", style = MaterialTheme.typography.labelLarge)
        summary.incomeBySource.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CardLabel(s.type.label())
                }
                Text(
                    lkr(s.totalCents),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (s.totalCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        CardLabel("Only income you have received counts. Unpaid invoices are shown separately.")
    }
}

/** FR-B8: Kavindu's own estimate against what actually arrived, so the guess can be checked. */
@Composable
fun EstimateCard(estimate: EstimateCheck) {
    MintfyCard {
        CardLabel("Your income estimate (not income)")
        Text(
            "${lkr(estimate.minCents)} – ${formatCents(estimate.maxCents)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            when (estimate.position) {
                EstimatePosition.BELOW -> "Received ${lkr(estimate.actualCents)}, ${lkr(estimate.minCents - estimate.actualCents)} below your lowest estimate."
                EstimatePosition.WITHIN -> "Received ${lkr(estimate.actualCents)}, within your estimate."
                EstimatePosition.ABOVE -> "Received ${lkr(estimate.actualCents)}, ${lkr(estimate.actualCents - estimate.maxCents)} above your highest estimate."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (estimate.position == EstimatePosition.BELOW) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Figma card label: 12 Regular, secondary text. */
@Composable
private fun CardLabel(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Figma "Badge": 1dp green outline, 12dp radius, 12 SemiBold. */
@Composable
private fun HighlightBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun IncomeSourceType.label(): String = when (this) {
    IncomeSourceType.SALARY -> "Salary"
    IncomeSourceType.FREELANCE -> "Freelance"
    IncomeSourceType.ADSENSE -> "AdSense (converted to LKR)"
    IncomeSourceType.CRYPTO -> "Crypto gains and losses"
    IncomeSourceType.OTHER -> "Other"
}

/** Kavindu's figures from the Figma sample, used by the previews. */
internal val sampleSummary = MonthlySummary(
    month = YearMonth.of(2026, 9),
    receivedIncomeCents = 14_050_000,
    expensesCents = 12_500_000,
    committedCents = 5_200_000,
    discretionaryCents = 7_300_000,
    topCategories = listOf(
        CategorySpend("default_rent", "Rent", SpendingType.COMMITTED, 3_400_000),
        CategorySpend("default_food_coffee", "Food & Coffee", SpendingType.DISCRETIONARY, 2_400_000, isLargestDiscretionary = true),
        CategorySpend("default_transport", "Transport", SpendingType.DISCRETIONARY, 1_900_000),
        CategorySpend("default_shopping", "Shopping", SpendingType.DISCRETIONARY, 1_600_000),
        CategorySpend("default_utilities", "Utilities", SpendingType.COMMITTED, 980_000),
        CategorySpend("default_gym", "Gym & Health", SpendingType.COMMITTED, 500_000),
        CategorySpend("default_entertainment", "Entertainment", SpendingType.DISCRETIONARY, 1_400_000),
    ),
    incomeBySource = listOf(
        SourceIncome("src_salary", "Startup salary", IncomeSourceType.SALARY, 12_800_000),
        SourceIncome("src_adsense", "Tech blog AdSense", IncomeSourceType.ADSENSE, 1_250_000),
    ),
    estimate = EstimateCheck(16_000_000, 21_000_000, 14_050_000),
    expenseCount = 42,
    incomeCount = 2,
)

@Preview
@Composable
fun DashboardInsightsPreview() {
    MintfyTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SavingsThisMonthCard(sampleSummary)
                MoneySplitCard(sampleSummary)
                TopCategoriesCard(sampleSummary)
                IncomeBySourceCard(sampleSummary)
                EstimateCard(sampleSummary.estimate!!)
            }
        }
    }
}

@Preview
@Composable
fun DashboardEmptyAndErrorPreview() {
    MintfyTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStatus(DashboardUiState(isLoading = false, error = "No internet connection."), onRetry = {})
                EmptyMonthCard(YearMonth.of(2026, 11), onAddExpense = {}, onAddIncome = {})
            }
        }
    }
}
