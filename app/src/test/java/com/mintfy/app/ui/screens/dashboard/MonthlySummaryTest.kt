package com.mintfy.app.ui.screens.dashboard

import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.data.repository.toTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MonthlySummaryTest {

    private val october = YearMonth.of(2026, 10)

    private val categories = listOf(
        Category("default_rent", "Rent", SpendingType.COMMITTED),
        Category("default_gym", "Gym & Health", SpendingType.COMMITTED),
        Category("default_food_coffee", "Food & Coffee", SpendingType.DISCRETIONARY),
        Category("default_transport", "Transport", SpendingType.DISCRETIONARY),
    )

    private val sources = listOf(
        IncomeSource("src_salary", "Startup salary", IncomeSourceType.SALARY, "LKR"),
        IncomeSource("src_adsense", "Tech blog AdSense", IncomeSourceType.ADSENSE, "USD"),
        IncomeSource("src_crypto", "Binance", IncomeSourceType.CRYPTO, "USDT"),
        IncomeSource("src_freelance", "Freelance", IncomeSourceType.FREELANCE, "LKR"),
    )

    private fun expense(id: String, cents: Long, categoryId: String, date: LocalDate = LocalDate.of(2026, 10, 10)) =
        Expense(id, cents, date.toTimestamp(), categoryId, "default_cash")

    private fun income(
        sourceId: String,
        type: IncomeSourceType,
        lkrCents: Long,
        status: IncomeStatus = IncomeStatus.RECEIVED,
        date: LocalDate = LocalDate.of(2026, 10, 25),
    ) = IncomeEntry(
        sourceId = sourceId,
        sourceType = type,
        status = status,
        date = date,
        currency = "LKR",
        originalAmountCents = lkrCents,
        exchangeRate = BigDecimal.ONE,
        lkrAmountCents = lkrCents,
    )

    private fun summary(
        expenses: List<Expense> = emptyList(),
        income: List<IncomeEntry> = emptyList(),
        min: Long? = null,
        max: Long? = null,
    ) = monthlySummary(october, expenses, income, categories, sources, min, max)

    @Test
    fun incomeExpensesAndSavings() {
        val s = summary(
            expenses = listOf(expense("e1", 3_400_000, "default_rent"), expense("e2", 85_000, "default_food_coffee")),
            income = listOf(income("src_salary", IncomeSourceType.SALARY, 13_250_000)),
        )
        assertEquals(13_250_000L, s.receivedIncomeCents)
        assertEquals(3_485_000L, s.expensesCents)
        assertEquals(9_765_000L, s.savingsCents) // received income minus expenses (A-01)
    }

    @Test
    fun onlyReceivedIncomeInThisMonthCounts() {
        val s = summary(
            income = listOf(
                income("src_salary", IncomeSourceType.SALARY, 13_250_000),
                income("src_freelance", IncomeSourceType.FREELANCE, 4_500_000, IncomeStatus.INVOICED), // FR-E6
                income("src_salary", IncomeSourceType.SALARY, 12_000_000, date = LocalDate.of(2026, 9, 25)),
            ),
        )
        assertEquals(13_250_000L, s.receivedIncomeCents)
        assertEquals(1, s.incomeCount)
    }

    @Test
    fun expensesOutsideTheMonthAreIgnored() {
        val s = summary(
            expenses = listOf(
                expense("e1", 85_000, "default_food_coffee"),
                expense("e2", 99_000, "default_food_coffee", LocalDate.of(2026, 9, 30)),
                expense("e3", 99_000, "default_food_coffee", LocalDate.of(2026, 11, 1)),
            ),
        )
        assertEquals(85_000L, s.expensesCents)
        assertEquals(1, s.expenseCount)
    }

    @Test
    fun committedVersusDiscretionary() {
        val s = summary(
            expenses = listOf(
                expense("e1", 3_400_000, "default_rent"),
                expense("e2", 500_000, "default_gym"),
                expense("e3", 2_400_000, "default_food_coffee"),
            ),
        )
        assertEquals(3_900_000L, s.committedCents)
        assertEquals(2_400_000L, s.discretionaryCents)
        assertEquals(3_900_000f / 6_300_000f, s.committedShare!!, 0.0001f)
    }

    @Test
    fun noSpendingMeansNoDonutShare() {
        assertNull(summary().committedShare)
        assertTrue(summary().isEmpty)
    }

    @Test
    fun topCategoriesFlagTheLargestDiscretionary() {
        val s = summary(
            expenses = listOf(
                expense("e1", 3_400_000, "default_rent"),
                expense("e2", 1_200_000, "default_food_coffee"),
                expense("e3", 1_200_000, "default_food_coffee"),
                expense("e4", 300_000, "default_transport"),
            ),
        )
        assertEquals(listOf("Rent", "Food & Coffee", "Transport"), s.topCategories.map { it.name })
        assertEquals(2_400_000L, s.topCategories[1].totalCents)
        // Rent is bigger but committed; the highlight goes to the biggest cost he can choose to cut.
        assertFalse(s.topCategories[0].isLargestDiscretionary)
        assertTrue(s.topCategories[1].isLargestDiscretionary)
        assertFalse(s.topCategories[2].isLargestDiscretionary)
    }

    @Test
    fun spendingInAnUnlistedCategoryStillAddsUp() {
        val s = summary(expenses = listOf(expense("e1", 50_000, "custom_archived"), expense("e2", 85_000, "default_rent")))
        assertEquals(ARCHIVED_CATEGORY_NAME, s.topCategories.first { it.categoryId == "custom_archived" }.name)
        assertEquals(s.expensesCents, s.committedCents + s.discretionaryCents)
    }

    @Test
    fun incomeBySourceIncludesSignedCrypto() {
        val s = summary(
            income = listOf(
                income("src_salary", IncomeSourceType.SALARY, 13_250_000),
                income("src_adsense", IncomeSourceType.ADSENSE, 1_554_000),
                income("src_crypto", IncomeSourceType.CRYPTO, -1_800_000), // a Q3-style loss
            ),
        )
        assertEquals(listOf("Startup salary", "Tech blog AdSense", "Binance"), s.incomeBySource.map { it.name })
        assertEquals(-1_800_000L, s.incomeBySource.last().totalCents)
        assertEquals(13_004_000L, s.receivedIncomeCents)
    }

    @Test
    fun estimateComparedWithActual() {
        val received = listOf(income("src_salary", IncomeSourceType.SALARY, 13_250_000))
        assertNull(summary(income = received).estimate)
        assertEquals(EstimatePosition.BELOW, summary(income = received, min = 16_000_000, max = 21_000_000).estimate!!.position)
        assertEquals(EstimatePosition.WITHIN, summary(income = received, min = 12_000_000, max = 14_000_000).estimate!!.position)
        assertEquals(EstimatePosition.ABOVE, summary(income = received, min = 8_000_000, max = 10_000_000).estimate!!.position)
    }
}
