package com.mintfy.app.data.local

import com.google.firebase.Timestamp
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.Expense
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.PaymentMethodType
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import com.mintfy.app.data.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

class CacheMappersTest {

    private val uid = "user_1"

    @Test
    fun `an expense survives the round trip, including its exact moment`() {
        val expense = Expense(
            id = "e1", amountCents = 85_050, date = Timestamp(Date(1_790_000_123_000)),
            categoryId = "cat_food", paymentMethodId = "pm_card", note = "Coffee", recurringId = "r1",
        )
        assertEquals(expense, expense.toCached(uid).toModel())
    }

    @Test
    fun `an income entry keeps every field and the exact exchange rate`() {
        val entry = IncomeEntry(
            sourceId = "src_adsense", sourceType = IncomeSourceType.ADSENSE, status = IncomeStatus.RECEIVED,
            date = LocalDate.of(2026, 9, 28), currency = "USD", originalAmountCents = 4_200,
            exchangeRate = BigDecimal("297.625000"), lkrAmountCents = 1_250_025,
            client = "c", project = "p", milestone = "m", dueDate = LocalDate.of(2026, 10, 1), note = "n", id = "i1",
        )
        val back = entry.toCached(uid)!!.toModel()!!
        assertEquals(entry, back)
        assertEquals(0, BigDecimal("297.625").compareTo(back.exchangeRate))
    }

    @Test
    fun `an unsaved income entry is not cached`() {
        val entry = IncomeEntry(
            sourceId = "s", sourceType = IncomeSourceType.SALARY, status = IncomeStatus.RECEIVED,
            date = LocalDate.of(2026, 9, 1), currency = "LKR", originalAmountCents = 1,
            exchangeRate = BigDecimal.ONE, lkrAmountCents = 1,
        )
        assertNull(entry.toCached(uid))
    }

    @Test
    fun `reference data and recurring items survive the round trip`() {
        val category = Category("cat_rent", "Rent", SpendingType.COMMITTED, icon = "home", builtIn = true)
        val method = PaymentMethod("pm_cash", "Cash", PaymentMethodType.CASH)
        val source = IncomeSource("src", "Startup salary", IncomeSourceType.SALARY, "LKR", 12_000_000)
        val recurring = RecurringExpense(
            "r1", "Rent", "cat_rent", "pm_card", 3_400_000, RecurringFrequency.MONTHLY, LocalDate.of(2026, 11, 1),
        )
        assertEquals(category, category.toCached(uid, 0).toModel())
        assertEquals(method, method.toCached(uid, 0).toModel())
        assertEquals(source, source.toCached(uid, 0).toModel())
        assertEquals(recurring, recurring.toCached(uid, 0).toModel())
    }

    @Test
    fun `a row with an unknown type is skipped`() {
        val row = CachedCategory(uid, "c", 0, "Odd", "SOMETHING_NEW", null, builtIn = false, archived = false)
        assertNull(row.toModel())
    }
}
