package com.mintfy.app.ui.screens.recurring

import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringTest {

    private val today = LocalDate.of(2026, 10, 5)

    private fun item(
        id: String,
        due: LocalDate,
        amountCents: Long = 100_000,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        active: Boolean = true,
    ) = RecurringExpense(id, id, "cat_rent", "pm_card", amountCents, frequency, due, active)

    @Test
    fun `next period moves by one week, month or year`() {
        val date = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 7), RecurringFrequency.WEEKLY.next(date))
        assertEquals(LocalDate.of(2026, 2, 28), RecurringFrequency.MONTHLY.next(date))
        assertEquals(LocalDate.of(2027, 1, 31), RecurringFrequency.YEARLY.next(date))
    }

    @Test
    fun `an item is due on or after its date unless paused`() {
        assertTrue(item("a", today).isDue(today))
        assertTrue(item("b", today.minusDays(4)).isDue(today))
        assertFalse(item("c", today.plusDays(1)).isDue(today))
        assertFalse(item("d", today.minusDays(4), active = false).isDue(today))
    }

    @Test
    fun `items are grouped into due, upcoming and paused, by date`() {
        val rent = item("rent", LocalDate.of(2026, 10, 1))
        val gym = item("gym", LocalDate.of(2026, 10, 10))
        val utilities = item("utilities", LocalDate.of(2026, 10, 5))
        val netflix = item("netflix", LocalDate.of(2026, 9, 1), active = false)

        val groups = groupRecurring(listOf(gym, netflix, utilities, rent), today)

        assertEquals(listOf(rent, utilities), groups.due)
        assertEquals(listOf(gym), groups.upcoming)
        assertEquals(listOf(netflix), groups.paused)
    }

    @Test
    fun `monthly equivalent converts weekly and yearly and skips paused`() {
        val items = listOf(
            item("rent", today, amountCents = 3_400_000),
            item("bus", today, amountCents = 120_000, frequency = RecurringFrequency.WEEKLY), // 520,000 a month
            item("domain", today, amountCents = 1_200_000, frequency = RecurringFrequency.YEARLY), // 100,000
            item("paused", today, amountCents = 999_999, active = false),
        )
        assertEquals(3_400_000L + 520_000L + 100_000L, monthlyEquivalentCents(items))
    }

    @Test
    fun `labels match the Figma wording`() {
        assertEquals("Monthly · 1st", scheduleLabel(RecurringFrequency.MONTHLY, LocalDate.of(2026, 11, 1)))
        assertEquals("Monthly · 12th", scheduleLabel(RecurringFrequency.MONTHLY, LocalDate.of(2026, 11, 12)))
        assertEquals("Weekly · Monday", scheduleLabel(RecurringFrequency.WEEKLY, LocalDate.of(2026, 10, 5)))
        assertEquals("Yearly · 3 Mar", scheduleLabel(RecurringFrequency.YEARLY, LocalDate.of(2027, 3, 3)))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "31st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 31).map(::ordinal))
    }

    @Test
    fun `due label says when it is due`() {
        assertEquals("Due today", dueLabel(item("a", today), today))
        assertEquals("Due since 1 Oct", dueLabel(item("a", LocalDate.of(2026, 10, 1)), today))
        assertEquals("Next 1 Nov", dueLabel(item("a", LocalDate.of(2026, 11, 1)), today))
        assertEquals("Paused", dueLabel(item("a", today, active = false), today))
    }

    @Test
    fun `a valid form builds the item in cents`() {
        val check = checkRecurring(
            RecurringInput(" Rent ", "34,000", "cat_rent", "pm_card", RecurringFrequency.MONTHLY, today)
        )
        val item = (check as RecurringCheck.Valid).item
        assertEquals("Rent", item.name)
        assertEquals(3_400_000L, item.amountCents)
        assertTrue(item.active)
    }

    @Test
    fun `missing fields are reported together`() {
        val check = checkRecurring(RecurringInput("", "0", null, null, RecurringFrequency.MONTHLY, today))
        val errors = (check as RecurringCheck.Invalid).errors
        assertNotNull(errors.name)
        assertNotNull(errors.amount)
        assertNotNull(errors.category)
        assertNotNull(errors.paymentMethod)
    }
}
