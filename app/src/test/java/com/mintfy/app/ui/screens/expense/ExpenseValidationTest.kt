package com.mintfy.app.ui.screens.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpenseValidationTest {

    @Test
    fun parsesAmountsToWholeCents() {
        assertEquals(85000L, parseAmountToCents("850"))
        assertEquals(85050L, parseAmountToCents("850.50"))
        assertEquals(85050L, parseAmountToCents("850.5"))
        assertEquals(85010L, parseAmountToCents("850.10")) // exact, no floating-point drift
        assertEquals(3400000L, parseAmountToCents("34,000"))
        assertEquals(85000L, parseAmountToCents(" 850. "))
    }

    @Test
    fun rejectsInvalidAmounts() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("-850"))
        assertNull(parseAmountToCents("850.505")) // more than two decimal places
        assertNull(parseAmountToCents(".50"))
    }

    @Test
    fun amountErrors() {
        assertNull(amountError("850"))
        assertEquals("Enter the amount.", amountError("  "))
        assertEquals("Enter an amount like 850 or 850.50.", amountError("12.345"))
        assertEquals("The amount must be more than zero.", amountError("0"))
        assertEquals("The amount must be more than zero.", amountError("0.00"))
        assertEquals("That amount is too large.", amountError("100000001"))
    }

    @Test
    fun date() {
        val today = LocalDate.of(2026, 10, 24)
        assertNull(dateError(today, today))
        assertNull(dateError(today.minusDays(7), today)) // backfilling last week is allowed
        assertEquals("The date can't be in the future.", dateError(today.plusDays(1), today))
    }

    @Test
    fun note() {
        assertNull(noteError(""))
        assertNull(noteError("a".repeat(MAX_NOTE_LENGTH)))
        assertNotNull(noteError("a".repeat(MAX_NOTE_LENGTH + 1)))
    }
}
