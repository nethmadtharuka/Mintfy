package com.mintfy.app.ui.screens.income

import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.screens.dashboard.EstimatePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class IncomeOverviewTest {

    private val october = YearMonth.of(2026, 10)

    private val sources = listOf(
        IncomeSource("src_salary", "Startup salary", IncomeSourceType.SALARY, "LKR", expectedAmountCents = 12_000_000),
        IncomeSource("src_adsense", "Google AdSense", IncomeSourceType.ADSENSE, "USD"),
        IncomeSource("src_crypto", "Binance", IncomeSourceType.CRYPTO, "USDT"),
        IncomeSource("src_freelance", "Freelance", IncomeSourceType.FREELANCE, "LKR"),
    )

    private fun entry(
        id: String,
        sourceId: String,
        type: IncomeSourceType,
        lkrCents: Long,
        date: LocalDate = LocalDate.of(2026, 10, 25),
        status: IncomeStatus = IncomeStatus.RECEIVED,
        currency: String = "LKR",
        originalCents: Long = lkrCents,
        rate: BigDecimal = BigDecimal.ONE,
        base: Long? = null,
        bonus: Long? = null,
        client: String? = null,
        dueDate: LocalDate? = null,
    ) = IncomeEntry(
        sourceId = sourceId, sourceType = type, status = status, date = date, currency = currency,
        originalAmountCents = originalCents, exchangeRate = rate, lkrAmountCents = lkrCents,
        baseAmountCents = base, bonusAmountCents = bonus, client = client, dueDate = dueDate, id = id,
    )

    private val salary = entry("i1", "src_salary", IncomeSourceType.SALARY, 12_800_000, base = 12_000_000, bonus = 800_000)
    private val adsense = entry(
        "i2", "src_adsense", IncomeSourceType.ADSENSE, 1_250_000, LocalDate.of(2026, 10, 3),
        currency = "USD", originalCents = 4_200, rate = BigDecimal("297.62"),
    )
    private val cryptoGain = entry("i3", "src_crypto", IncomeSourceType.CRYPTO, 2_000_000, LocalDate.of(2026, 2, 10))
    private val cryptoLoss = entry("i4", "src_crypto", IncomeSourceType.CRYPTO, -1_800_000, LocalDate.of(2026, 10, 5))
    private val invoice = entry(
        "i5", "src_freelance", IncomeSourceType.FREELANCE, 4_500_000, LocalDate.of(2026, 11, 15),
        status = IncomeStatus.INVOICED, client = "Lanka Bites", dueDate = LocalDate.of(2026, 11, 15),
    )

    private fun overview(min: Long? = null, max: Long? = null) = incomeOverview(
        month = october,
        yearEntries = listOf(salary, adsense, cryptoGain, cryptoLoss, invoice),
        freelanceEntries = listOf(invoice),
        sources = sources,
        estimateMinCents = min,
        estimateMaxCents = max,
    )

    @Test
    fun receivedThisMonthOnly() {
        val o = overview()
        // salary + AdSense + the October crypto loss; the February gain and the unpaid invoice are not this month's income
        assertEquals(12_800_000L + 1_250_000L - 1_800_000L, o.receivedCents)
        assertEquals(listOf("i1", "i4", "i2"), o.rows.map { it.id }) // newest first
    }

    @Test
    fun salaryRowShowsBaseBonusAndExpected() {
        val row = overview().rows.first { it.id == "i1" }
        assertEquals("Startup salary", row.title)
        assertEquals("Base 120,000 + bonus 8,000 · LKR 8,000 above your expected salary", row.detail)
    }

    @Test
    fun foreignCurrencyRowShowsOriginalAmountAndRate() {
        assertEquals("USD 42 at 297.62 · Received 3 Oct", overview().rows.first { it.id == "i2" }.detail)
    }

    @Test
    fun cryptoRowsAndYearToDate() {
        val o = overview()
        assertEquals("Loss · 5 Oct", o.rows.first { it.id == "i4" }.detail)
        val ytd = o.cryptoYearToDate!!
        assertEquals(2026, ytd.year)
        assertEquals(200_000L, ytd.netCents) // Q1 gain 20,000 minus October loss 18,000
        assertEquals(2, ytd.entryCount)
    }

    @Test
    fun noCryptoMeansNoYearToDateCard() {
        val o = incomeOverview(october, listOf(salary), emptyList(), sources, null, null)
        assertNull(o.cryptoYearToDate)
    }

    @Test
    fun outstandingInvoicesAreSeparateFromIncome() {
        val o = overview()
        assertEquals(4_500_000L, o.outstanding.totalLkrCents)
        assertEquals("i5", o.nextInvoice?.id)
    }

    @Test
    fun estimateAgainstReceived() {
        assertNull(overview().estimate)
        assertEquals(EstimatePosition.BELOW, overview(16_000_000, 21_000_000).estimate!!.position)
    }

    @Test
    fun estimateInput() {
        assertEquals(EstimateInput.Valid(null, null), checkEstimate("", " "))
        assertEquals(EstimateInput.Valid(16_000_000, 21_000_000), checkEstimate("160,000", "210000"))
        val reversed = checkEstimate("210,000", "160,000") as EstimateInput.Invalid
        assertEquals("The highest amount can't be below the lowest.", reversed.maxError)
        val missing = checkEstimate("160,000", "") as EstimateInput.Invalid
        assertTrue(missing.maxError != null && missing.minError == null)
        assertTrue(checkEstimate("abc", "210,000") is EstimateInput.Invalid)
    }
}
