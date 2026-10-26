package com.mintfy.app.ui.screens.history

import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.repository.toLocalDate
import com.mintfy.app.ui.screens.income.BASE_CURRENCY
import com.mintfy.app.ui.screens.income.formatCents
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// Plain Kotlin so it runs in JVM unit tests.

enum class EntryKind { EXPENSE, INCOME }

/** One line of the history list. [amountCents] is signed LKR: spending and crypto losses are negative. */
data class HistoryRow(
    val id: String,
    val kind: EntryKind,
    val date: LocalDate,
    val title: String,
    val detail: String,
    val amountCents: Long,
    val received: Boolean = true, // false for income that has not arrived yet; it never counts (FR-E6)
)

/** Inclusive date range for the history filter (FR-C7). */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    companion object {
        /** The default: the whole of the current month, so expected invoices later this month show too. */
        fun monthOf(today: LocalDate): DateRange = YearMonth.from(today).let { DateRange(it.atDay(1), it.atEndOfMonth()) }
    }
}

/**
 * Merges income and expenses into one list, newest day first (FR-C7).
 * Entries on the same day keep the order they arrive in, which for each list is newest first.
 * Picking a category shows only expenses in it: income has no category.
 * The date range is applied again here even though the queries already use it, so the list is correct
 * whatever the cache returns.
 */
fun historyRows(
    expenses: List<Expense>,
    income: List<IncomeEntry>,
    range: DateRange,
    categoryId: String?,
    categoryNames: Map<String, String>,
    paymentMethodNames: Map<String, String>,
    sourceNames: Map<String, String>,
): List<HistoryRow> {
    val expenseRows = expenses
        .filter { categoryId == null || it.categoryId == categoryId }
        .map { e ->
            HistoryRow(
                id = e.id,
                kind = EntryKind.EXPENSE,
                date = e.date.toLocalDate(),
                title = categoryNames[e.categoryId] ?: "Unknown category",
                detail = listOfNotNull(paymentMethodNames[e.paymentMethodId], e.note).joinToString(" · "),
                amountCents = -e.amountCents,
            )
        }
    val incomeRows = if (categoryId != null) emptyList() else income.mapNotNull { i ->
        HistoryRow(
            id = i.id ?: return@mapNotNull null,
            kind = EntryKind.INCOME,
            date = i.date,
            title = sourceNames[i.sourceId] ?: "Income",
            detail = listOfNotNull(
                statusText(i.status),
                if (i.currency == BASE_CURRENCY) null else "${i.currency} ${signedAmount(i.originalAmountCents)}",
                i.client,
                i.note,
            ).joinToString(" · "),
            amountCents = i.lkrAmountCents,
            received = i.status == IncomeStatus.RECEIVED,
        )
    }
    return (expenseRows + incomeRows)
        .filter { it.date in range.from..range.to }
        .sortedByDescending { it.date } // stable, so same-day entries keep their order
}

/** "+ LKR 120,000" or "− LKR 850". */
fun amountLabel(cents: Long): String = "${if (cents < 0) "−" else "+"} LKR ${formatCents(abs(cents))}"

private fun signedAmount(cents: Long) = (if (cents < 0) "−" else "") + formatCents(abs(cents))

private fun statusText(status: IncomeStatus) = when (status) {
    IncomeStatus.RECEIVED -> null
    IncomeStatus.EXPECTED -> "Expected, not received yet"
    IncomeStatus.INVOICED -> "Invoiced, not received yet"
}
