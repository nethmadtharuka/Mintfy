package com.mintfy.app.ui.screens.history

import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.repository.toTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HistoryRowsTest {

    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val categories = mapOf("default_food_coffee" to "Food & Coffee", "default_rent" to "Rent")
    private val methods = mapOf("default_cash" to "Cash", "default_card" to "Bank card")
    private val sources = mapOf("src_salary" to "Startup salary", "src_freelance" to "Freelance")

    private fun expense(id: String, day: Int, categoryId: String = "default_food_coffee", note: String? = null) =
        Expense(id, 85_000, LocalDate.of(2026, 10, day).toTimestamp(), categoryId, "default_cash", note)

    private fun income(id: String, day: Int, status: IncomeStatus = IncomeStatus.RECEIVED, sourceId: String = "src_salary") =
        IncomeEntry(
            sourceId = sourceId,
            sourceType = if (sourceId == "src_salary") IncomeSourceType.SALARY else IncomeSourceType.FREELANCE,
            status = status,
            date = LocalDate.of(2026, 10, day),
            currency = "LKR",
            originalAmountCents = 12_000_000,
            exchangeRate = BigDecimal.ONE,
            lkrAmountCents = 12_000_000,
            id = id,
        )

    private fun rows(
        expenses: List<Expense> = emptyList(),
        entries: List<IncomeEntry> = emptyList(),
        range: DateRange = october,
        categoryId: String? = null,
    ) = historyRows(expenses, entries, range, categoryId, categories, methods, sources)

    @Test
    fun mergesIncomeAndExpensesNewestDayFirst() {
        val result = rows(
            expenses = listOf(expense("e20", 20), expense("e3", 3)),
            entries = listOf(income("i25", 25), income("i10", 10)),
        )
        assertEquals(listOf("i25", "e20", "i10", "e3"), result.map { it.id })
    }

    @Test
    fun sameDayEntriesKeepTheirOrder() {
        // Each list arrives newest first from Firestore; sorting by day must not reshuffle within a day.
        val result = rows(expenses = listOf(expense("later", 20), expense("earlier", 20)), entries = listOf(income("pay", 20)))
        assertEquals(listOf("later", "earlier", "pay"), result.map { it.id })
    }

    @Test
    fun dateRangeIsInclusiveAtBothEnds() {
        val range = DateRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20))
        val result = rows(
            expenses = listOf(expense("before", 9), expense("first", 10), expense("last", 20), expense("after", 21)),
            entries = listOf(income("in", 15), income("out", 21)),
            range = range,
        )
        assertEquals(listOf("last", "in", "first"), result.map { it.id })
    }

    @Test
    fun categoryFilterShowsOnlyThatCategoryAndNoIncome() {
        val result = rows(
            expenses = listOf(expense("coffee", 20), expense("rent", 1, categoryId = "default_rent")),
            entries = listOf(income("salary", 25)),
            categoryId = "default_rent",
        )
        assertEquals(listOf("rent"), result.map { it.id })
    }

    @Test
    fun expensesAreNegativeAndUnpaidIncomeIsMarked() {
        val result = rows(
            expenses = listOf(expense("e1", 20, note = "Morning coffee")),
            entries = listOf(income("inv", 5, IncomeStatus.INVOICED, sourceId = "src_freelance")),
        )
        val coffee = result.first { it.id == "e1" }
        assertEquals(-85_000L, coffee.amountCents)
        assertEquals("Food & Coffee", coffee.title)
        assertEquals("Cash · Morning coffee", coffee.detail)
        assertTrue(coffee.received)

        val invoice = result.first { it.id == "inv" }
        assertEquals("Freelance", invoice.title)
        assertEquals("Invoiced, not received yet", invoice.detail)
        assertFalse(invoice.received)
    }

    @Test
    fun missingLabelsFallBackInsteadOfFailing() {
        val result = rows(
            expenses = listOf(expense("e1", 20, categoryId = "archived_category")),
            entries = listOf(income("i1", 20, sourceId = "archived_source")),
        )
        assertEquals("Unknown category", result.first { it.id == "e1" }.title)
        assertEquals("Income", result.first { it.id == "i1" }.title)
    }

    @Test
    fun defaultRangeIsTheWholeMonth() {
        assertEquals(october, DateRange.monthOf(LocalDate.of(2026, 10, 26)))
        val february = DateRange.monthOf(LocalDate.of(2028, 2, 10)) // leap year
        assertEquals(LocalDate.of(2028, 2, 29), february.to)
    }

    @Test
    fun amountLabels() {
        assertEquals("+ LKR 120,000", amountLabel(12_000_000))
        assertEquals("− LKR 850.50", amountLabel(-85_050))
    }
}
