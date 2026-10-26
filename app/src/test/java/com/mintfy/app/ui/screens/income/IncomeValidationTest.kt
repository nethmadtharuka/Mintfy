package com.mintfy.app.ui.screens.income

import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class IncomeValidationTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val salary = IncomeSource("src_salary", "Startup salary", IncomeSourceType.SALARY, "LKR", 12_000_000)
    private val freelance = IncomeSource("src_freelance", "Freelance", IncomeSourceType.FREELANCE, "LKR")
    private val adsense = IncomeSource("src_adsense", "AdSense", IncomeSourceType.ADSENSE, "USD")
    private val crypto = IncomeSource("src_crypto", "Binance", IncomeSourceType.CRYPTO, "USDT")
    private val other = IncomeSource("src_other", "Other", IncomeSourceType.OTHER, "LKR")

    private fun valid(input: IncomeInput, source: IncomeSource) =
        (checkIncome(input, source, today) as IncomeCheck.Valid).entry

    private fun errors(input: IncomeInput, source: IncomeSource) =
        (checkIncome(input, source, today) as IncomeCheck.Invalid).errors

    @Test
    fun convertsAtTheEnteredRateRoundingHalfUp() {
        // Schema §4.2: USD 52.30 at 298.75 is LKR 15,624.625, rounded to 15,624.63.
        assertEquals(1_562_463L, convertToLkrCents(5_230, BigDecimal("298.75")))
        // Crypto loss of 60 USDT at 300.
        assertEquals(-1_800_000L, convertToLkrCents(-6_000, BigDecimal("300")))
        // Half a cent rounds away from zero, the same for gains and losses.
        assertEquals(1L, convertToLkrCents(1, BigDecimal("0.5")))
        assertEquals(-1L, convertToLkrCents(-1, BigDecimal("0.5")))
        assertEquals(0L, convertToLkrCents(1, BigDecimal("0.4")))
    }

    @Test
    fun parsesExchangeRates() {
        assertEquals(BigDecimal("298.75"), parseExchangeRate(" 298.75 "))
        assertEquals(BigDecimal("300"), parseExchangeRate("300"))
        assertNull(parseExchangeRate("0"))
        assertNull(parseExchangeRate("-300"))
        assertNull(parseExchangeRate("1.1234567")) // more than six decimal places
        assertNull(parseExchangeRate("abc"))
        assertNull(parseExchangeRate("2000000")) // above the limit
        assertEquals("Enter the exchange rate.", exchangeRateError(" "))
        assertEquals("The rate must be more than zero.", exchangeRateError("0"))
        assertEquals("Enter the LKR value of one unit, like 298.75.", exchangeRateError("12,5"))
        assertNull(exchangeRateError("298.75"))
    }

    @Test
    fun suggestsTheConvertedAmount() {
        assertEquals("15,624.63", suggestedLkrAmount("52.30", "", "298.75"))
        assertEquals("300", suggestedLkrAmount("1", "", "300"))
        assertNull(suggestedLkrAmount("52.30", "", ""))
        assertNull(suggestedLkrAmount("", "", "298.75"))
    }

    @Test
    fun currencies() {
        assertNull(currencyError("LKR"))
        assertNull(currencyError("USDT"))
        assertNull(currencyError("EUR")) // any 3 to 5 capital letters is valid
        assertNotNull(currencyError("usd"))
        assertNotNull(currencyError("US"))
        assertNotNull(currencyError("ABCDEF"))
        assertEquals("USD", defaultCurrencyFor(IncomeSourceType.ADSENSE))
        assertEquals("USDT", defaultCurrencyFor(IncomeSourceType.CRYPTO))
        assertEquals("LKR", defaultCurrencyFor(IncomeSourceType.SALARY))
    }

    @Test
    fun onlyFreelanceAndOtherCanBeUnpaid() {
        val all = listOf(IncomeStatus.RECEIVED, IncomeStatus.EXPECTED, IncomeStatus.INVOICED)
        assertEquals(all, statusOptions(IncomeSourceType.FREELANCE))
        assertEquals(all, statusOptions(IncomeSourceType.OTHER))
        listOf(IncomeSourceType.SALARY, IncomeSourceType.ADSENSE, IncomeSourceType.CRYPTO).forEach {
            assertEquals(listOf(IncomeStatus.RECEIVED), statusOptions(it))
            assertEquals(IncomeStatus.RECEIVED, allowedStatus(it, IncomeStatus.INVOICED))
        }
        assertEquals(IncomeStatus.INVOICED, allowedStatus(IncomeSourceType.FREELANCE, IncomeStatus.INVOICED))
    }

    @Test
    fun salaryStoresBaseAndBonusThatAddUp() {
        val entry = valid(IncomeInput(amount = "120,000", bonus = "8,000", date = today), salary)
        assertEquals(12_800_000L, entry.originalAmountCents)
        assertEquals(12_000_000L, entry.baseAmountCents)
        assertEquals(800_000L, entry.bonusAmountCents)
        assertEquals(entry.originalAmountCents, entry.baseAmountCents!! + entry.bonusAmountCents!!)
        // LKR: no conversion (firestore.rules).
        assertEquals(0, BigDecimal.ONE.compareTo(entry.exchangeRate))
        assertEquals(entry.originalAmountCents, entry.lkrAmountCents)
        assertEquals(IncomeStatus.RECEIVED, entry.status)

        val noBonus = valid(IncomeInput(amount = "120000", date = today), salary)
        assertEquals(12_000_000L, noBonus.baseAmountCents)
        assertNull(noBonus.bonusAmountCents)
        assertEquals("Enter a bonus like 8000 or 8000.50.", errors(IncomeInput(amount = "1", bonus = "-5", date = today), salary).bonus)
    }

    @Test
    fun lkrIgnoresAnyRateTyped() {
        val entry = valid(IncomeInput(amount = "45000", rate = "300", lkrAmount = "1", date = today), freelance)
        assertEquals(0, BigDecimal.ONE.compareTo(entry.exchangeRate))
        assertEquals(4_500_000L, entry.lkrAmountCents)
    }

    @Test
    fun foreignCurrencyKeepsOriginalRateAndCreditedLkr() {
        // The bank credited LKR 15,540.00, less than the converted value, because of fees (schema §4.2).
        val entry = valid(
            IncomeInput(amount = "52.30", currency = "USD", rate = "298.75", lkrAmount = "15,540", date = today),
            adsense,
        )
        assertEquals("USD", entry.currency)
        assertEquals(5_230L, entry.originalAmountCents)
        assertEquals(BigDecimal("298.75"), entry.exchangeRate)
        assertEquals(1_554_000L, entry.lkrAmountCents)
        assertNull(entry.baseAmountCents) // only salary stores base and bonus

        val missing = errors(IncomeInput(amount = "52.30", currency = "USD", date = today), adsense)
        assertEquals("Enter the exchange rate.", missing.rate)
        assertEquals("Enter the LKR amount credited.", missing.lkrAmount)
    }

    @Test
    fun convertedAmountThatRoundsToZeroIsRejected() {
        val e = errors(IncomeInput(amount = "0.01", currency = "USD", rate = "0.4", lkrAmount = "0", date = today), adsense)
        assertEquals("The amount must be more than zero.", e.lkrAmount)
    }

    @Test
    fun cryptoLossIsNegativeInBothAmounts() {
        val loss = valid(
            IncomeInput(amount = "60", currency = "USDT", rate = "300", lkrAmount = "18,000", isLoss = true, date = today),
            crypto,
        )
        assertEquals(-6_000L, loss.originalAmountCents)
        assertEquals(-1_800_000L, loss.lkrAmountCents)

        val gain = valid(IncomeInput(amount = "60", currency = "USDT", rate = "300", lkrAmount = "18,000", date = today), crypto)
        assertEquals(6_000L, gain.originalAmountCents)
        assertEquals(1_800_000L, gain.lkrAmountCents)

        // "Loss" only makes crypto negative; any other source stays positive.
        assertEquals(4_500_000L, valid(IncomeInput(amount = "45000", isLoss = true, date = today), freelance).originalAmountCents)
        assertEquals("The amount must be more than zero.", errors(IncomeInput(amount = "0", date = today), crypto).amount)
    }

    @Test
    fun statusIsForcedToReceivedWhereUnpaidIsNotAllowed() {
        val entry = valid(IncomeInput(amount = "120000", status = IncomeStatus.INVOICED, date = today), salary)
        assertEquals(IncomeStatus.RECEIVED, entry.status)
        val invoice = valid(IncomeInput(amount = "45000", status = IncomeStatus.INVOICED, date = today), freelance)
        assertEquals(IncomeStatus.INVOICED, invoice.status)
        assertEquals(IncomeStatus.EXPECTED, valid(IncomeInput(amount = "10", status = IncomeStatus.EXPECTED, date = today), other).status)
    }

    @Test
    fun receivedIncomeCannotBeInTheFutureButExpectedCan() {
        val tomorrow = today.plusDays(1)
        assertEquals(
            "Received income can't be dated in the future.",
            errors(IncomeInput(amount = "120000", date = tomorrow), salary).date,
        )
        // Salary can't be unpaid, so a future date stays an error even if INVOICED was picked.
        assertNotNull(errors(IncomeInput(amount = "120000", status = IncomeStatus.INVOICED, date = tomorrow), salary).date)
        val expected = valid(IncomeInput(amount = "45000", status = IncomeStatus.EXPECTED, date = tomorrow), freelance)
        assertEquals(tomorrow, expected.date)
    }

    @Test
    fun freelanceDetailsAreTrimmedAndOnlyKeptWhereTheyApply() {
        val due = today.plusDays(10)
        val input = IncomeInput(
            amount = "45000", status = IncomeStatus.INVOICED, date = today,
            client = "  Lanka Bites ", project = "", milestone = "Milestone 2", dueDate = due,
        )
        val invoice = valid(input, freelance)
        assertEquals("Lanka Bites", invoice.client)
        assertNull(invoice.project)
        assertEquals("Milestone 2", invoice.milestone)
        assertEquals(due, invoice.dueDate)

        // A received freelance payment has no due date; other source types keep no freelance fields.
        assertNull(valid(input.copy(status = IncomeStatus.RECEIVED), freelance).dueDate)
        val otherEntry = valid(input.copy(status = IncomeStatus.RECEIVED), other)
        assertNull(otherEntry.client)
        assertNull(otherEntry.milestone)

        val tooLong = "a".repeat(MAX_FREELANCE_TEXT_LENGTH + 1)
        assertEquals("Use at most 80 characters.", errors(input.copy(client = tooLong), freelance).client)
        assertFalse(checkIncome(input.copy(client = tooLong), other, today) is IncomeCheck.Invalid)
    }

    @Test
    fun amountsAndNotes() {
        assertEquals("Enter the amount.", errors(IncomeInput(amount = " ", date = today), salary).amount)
        assertEquals("That amount is too large.", errors(IncomeInput(amount = "100000000", bonus = "1", date = today), salary).amount)
        assertNotNull(errors(IncomeInput(amount = "1", note = "a".repeat(201), date = today), salary).note)
        assertNull(valid(IncomeInput(amount = "1", note = "   ", date = today), salary).note)
    }

    @Test
    fun sourceFields() {
        assertEquals("Enter a name for this source.", sourceNameError("  "))
        assertNull(sourceNameError("a".repeat(MAX_SOURCE_NAME_LENGTH)))
        assertNotNull(sourceNameError("a".repeat(MAX_SOURCE_NAME_LENGTH + 1)))
        assertNull(expectedAmountError(""))
        assertNotNull(expectedAmountError("0"))
        assertTrue(IncomeSourceType.CRYPTO.isSigned)
        assertFalse(IncomeSourceType.SALARY.allowsUnpaid)
    }

    @Test
    fun formatting() {
        assertEquals("120,000", formatCents(12_000_000))
        assertEquals("15,624.63", formatCents(1_562_463))
        assertEquals("0.05", formatCents(5))
        assertEquals("LKR 8,000 above your expected salary", salaryDifferenceLabel(12_800_000, 12_000_000))
        assertEquals("LKR 500.50 below your expected salary", salaryDifferenceLabel(11_949_950, 12_000_000))
        assertEquals("Matches your expected salary", salaryDifferenceLabel(12_000_000, 12_000_000))
    }

    /**
     * FR-C5: opening a saved entry for editing and saving it unchanged must give back the same entry.
     * The rate goes through a Double, as it does when read from Firestore.
     */
    private fun assertRoundTrip(input: IncomeInput, source: IncomeSource) {
        val saved = valid(input, source)
        val read = saved.copy(id = "doc1", exchangeRate = BigDecimal.valueOf(saved.exchangeRate.toDouble()))
        assertEquals(saved, valid(incomeInputFor(read), source))
    }

    @Test
    fun editingFormRoundTripsEverySourceType() {
        assertRoundTrip(IncomeInput(amount = "120,000", bonus = "12,500", date = today, note = "October"), salary)
        assertRoundTrip(IncomeInput(amount = "120000", date = today), salary) // no bonus
        assertRoundTrip(
            IncomeInput(amount = "52.30", currency = "USD", rate = "298.75", lkrAmount = "15,540", date = today),
            adsense,
        )
        assertRoundTrip(
            IncomeInput(amount = "60", currency = "USDT", rate = "300", lkrAmount = "18000", isLoss = true, date = today),
            crypto,
        )
        assertRoundTrip(
            IncomeInput(
                amount = "45,000", status = IncomeStatus.INVOICED, date = today.plusDays(10),
                client = "Lanka Bites", project = "WordPress site", milestone = "Milestone 2", dueDate = today.plusDays(12),
            ),
            freelance,
        )
    }

    @Test
    fun editingFormShowsStoredValues() {
        val loss = valid(
            IncomeInput(amount = "60", currency = "USDT", rate = "300", lkrAmount = "18,000", isLoss = true, date = today),
            crypto,
        )
        val input = incomeInputFor(loss.copy(exchangeRate = BigDecimal.valueOf(300.0)))
        assertEquals("60", input.amount) // shown without the sign; the loss chip carries it
        assertTrue(input.isLoss)
        assertEquals("300", input.rate)
        assertEquals("18,000", input.lkrAmount) // what was credited, not re-suggested

        val pay = valid(IncomeInput(amount = "120,000", bonus = "8,000", date = today), salary)
        assertEquals("120,000", incomeInputFor(pay).amount)
        assertEquals("8,000", incomeInputFor(pay).bonus)
        assertEquals("", incomeInputFor(pay).rate) // LKR has no rate field
    }
}
