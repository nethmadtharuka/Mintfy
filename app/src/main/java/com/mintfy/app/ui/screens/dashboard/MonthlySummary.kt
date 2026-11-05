package com.mintfy.app.ui.screens.dashboard

import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.data.repository.toLocalDate
import java.time.YearMonth

// Plain Kotlin so it runs in JVM unit tests. Every dashboard figure is calculated here from the
// month's entries; nothing is stored as a total (schema decision D-09), so edits and deletes are
// always reflected and the numbers never drift.

/** What one category cost this month (FR-F3). */
data class CategorySpend(
    val categoryId: String,
    val name: String,
    val spendingType: SpendingType,
    val totalCents: Long,
    val isLargestDiscretionary: Boolean = false,
)

/** What one income source paid this month (FR-F6). Crypto can be negative (a loss). */
data class SourceIncome(
    val sourceId: String,
    val name: String,
    val type: IncomeSourceType,
    val totalCents: Long,
)

enum class EstimatePosition { BELOW, WITHIN, ABOVE }

/** Kavindu's own estimate next to what he actually received (FR-B8). */
data class EstimateCheck(val minCents: Long, val maxCents: Long, val actualCents: Long) {
    val position: EstimatePosition
        get() = when {
            actualCents < minCents -> EstimatePosition.BELOW
            actualCents > maxCents -> EstimatePosition.ABOVE
            else -> EstimatePosition.WITHIN
        }
}

/** Everything the dashboard shows for one month. All money is whole LKR cents. */
data class MonthlySummary(
    val month: YearMonth,
    val receivedIncomeCents: Long,
    val expensesCents: Long,
    val committedCents: Long,
    val discretionaryCents: Long,
    val topCategories: List<CategorySpend>,
    val incomeBySource: List<SourceIncome>,
    val estimate: EstimateCheck?,
    val expenseCount: Int,
    val incomeCount: Int,
) {
    /** Received income minus recorded expenses (SRS A-01, FR-E3). Can be negative. */
    val savingsCents: Long get() = receivedIncomeCents - expensesCents

    /** Share of spending that is committed, 0 to 1, for the donut (FR-F2). Null when nothing was spent. */
    val committedShare: Float? get() = if (expensesCents > 0) committedCents.toFloat() / expensesCents else null

    val isEmpty: Boolean get() = expenseCount == 0 && incomeCount == 0
}

/** Shown for spending whose category was archived, so its type and name are no longer listed. */
const val ARCHIVED_CATEGORY_NAME = "Archived category"

/**
 * Builds the dashboard figures for [month].
 *
 * - Only RECEIVED income counts (FR-B6, FR-E6); expected and invoiced freelance money is shown
 *   separately by the unpaid-invoices card.
 * - Every total adds lkrAmountCents, the rupees that actually arrived (FR-B5).
 * - The committed/discretionary split comes from each expense's category at the time of viewing,
 *   so changing a category's type redraws past months too (schema decision D-05).
 * - Spending in a category that is no longer listed is counted as discretionary under
 *   "Archived category", so the donut always adds up to total spending.
 */
fun monthlySummary(
    month: YearMonth,
    expenses: List<Expense>,
    incomeEntries: List<IncomeEntry>,
    categories: List<Category>,
    sources: List<IncomeSource>,
    estimateMinCents: Long?,
    estimateMaxCents: Long?,
): MonthlySummary {
    val monthExpenses = expenses.filter { YearMonth.from(it.date.toLocalDate()) == month }
    val received = incomeEntries.filter { it.status == IncomeStatus.RECEIVED && YearMonth.from(it.date) == month }

    val categoryById = categories.associateBy { it.id }
    val spendByCategory = monthExpenses
        .groupBy { it.categoryId }
        .map { (id, list) ->
            val category = categoryById[id]
            CategorySpend(
                categoryId = id,
                name = category?.name ?: ARCHIVED_CATEGORY_NAME,
                spendingType = category?.spendingType ?: SpendingType.DISCRETIONARY,
                totalCents = list.sumOf { it.amountCents },
            )
        }
        .sortedWith(compareByDescending<CategorySpend> { it.totalCents }.thenBy { it.name.lowercase() })

    // FR-F3: the biggest discretionary cost is the one Kavindu can most easily cut.
    val largestDiscretionaryId = spendByCategory.firstOrNull { it.spendingType == SpendingType.DISCRETIONARY }?.categoryId
    val topCategories = spendByCategory.map { it.copy(isLargestDiscretionary = it.categoryId == largestDiscretionaryId) }

    val sourceById = sources.associateBy { it.id }
    val incomeBySource = received
        .groupBy { it.sourceId }
        .map { (id, list) ->
            val first = list.first()
            SourceIncome(
                sourceId = id,
                name = sourceById[id]?.name ?: first.sourceType.label(),
                type = first.sourceType,
                totalCents = list.sumOf { it.lkrAmountCents },
            )
        }
        .sortedByDescending { it.totalCents }

    val receivedTotal = received.sumOf { it.lkrAmountCents }
    val estimate = if (estimateMinCents != null && estimateMaxCents != null) {
        EstimateCheck(estimateMinCents, estimateMaxCents, receivedTotal)
    } else {
        null
    }

    return MonthlySummary(
        month = month,
        receivedIncomeCents = receivedTotal,
        expensesCents = monthExpenses.sumOf { it.amountCents },
        committedCents = spendByCategory.filter { it.spendingType == SpendingType.COMMITTED }.sumOf { it.totalCents },
        discretionaryCents = spendByCategory.filter { it.spendingType == SpendingType.DISCRETIONARY }.sumOf { it.totalCents },
        topCategories = topCategories,
        incomeBySource = incomeBySource,
        estimate = estimate,
        expenseCount = monthExpenses.size,
        incomeCount = received.size,
    )
}

/** A readable name when a source is no longer listed (archived). */
private fun IncomeSourceType.label(): String = when (this) {
    IncomeSourceType.SALARY -> "Salary"
    IncomeSourceType.FREELANCE -> "Freelance"
    IncomeSourceType.ADSENSE -> "AdSense"
    IncomeSourceType.CRYPTO -> "Crypto"
    IncomeSourceType.OTHER -> "Other income"
}
