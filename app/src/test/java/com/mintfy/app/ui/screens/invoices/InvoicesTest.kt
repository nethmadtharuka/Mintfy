package com.mintfy.app.ui.screens.invoices

import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InvoicesTest {

    private val today = LocalDate.of(2026, 10, 26)

    private fun entry(
        id: String,
        status: IncomeStatus,
        lkrCents: Long = 4_500_000,
        date: LocalDate = today,
        dueDate: LocalDate? = null,
        type: IncomeSourceType = IncomeSourceType.FREELANCE,
        client: String? = "Lanka Bites",
        project: String? = "WordPress site",
    ) = IncomeEntry(
        sourceId = "src_${type.name.lowercase()}",
        sourceType = type,
        status = status,
        date = date,
        currency = "LKR",
        originalAmountCents = lkrCents,
        exchangeRate = BigDecimal.ONE,
        lkrAmountCents = lkrCents,
        client = client,
        project = project,
        milestone = "Milestone 2",
        dueDate = dueDate,
        id = id,
    )

    private val invoiced = entry("invoiced", IncomeStatus.INVOICED, 4_500_000, dueDate = today.plusDays(10))
    private val expected = entry("expected", IncomeStatus.EXPECTED, 2_000_000, date = today.plusDays(20))
    private val paid = entry("paid", IncomeStatus.RECEIVED, 9_000_000, date = today.minusDays(5))
    private val paidEarlier = entry("paidEarlier", IncomeStatus.RECEIVED, 3_000_000, date = today.minusDays(40))
    private val otherUnpaid = entry("other", IncomeStatus.EXPECTED, 1_000_000, type = IncomeSourceType.OTHER)
    private val salary = entry("salary", IncomeStatus.RECEIVED, 13_250_000, type = IncomeSourceType.SALARY)
    private val all = listOf(paidEarlier, salary, expected, paid, otherUnpaid, invoiced)

    @Test
    fun outstandingTotalIsUnpaidFreelanceOnly() {
        // FR-B7 / SRS 1.4: expected + invoiced freelance; received and non-freelance income are excluded.
        assertEquals(OutstandingSummary(6_500_000, 2), outstandingSummary(all))
        assertEquals(OutstandingSummary(0, 0), outstandingSummary(listOf(paid, salary)))
        assertEquals(OutstandingSummary(0, 0), outstandingSummary(emptyList()))
    }

    @Test
    fun filtersByStatus() {
        assertEquals(listOf("invoiced", "expected"), invoices(all, InvoiceFilter.OUTSTANDING).map { it.id })
        assertEquals(listOf("expected"), invoices(all, InvoiceFilter.EXPECTED).map { it.id })
        assertEquals(listOf("invoiced"), invoices(all, InvoiceFilter.INVOICED).map { it.id })
        assertEquals(listOf("paid", "paidEarlier"), invoices(all, InvoiceFilter.RECEIVED).map { it.id })
        // Unpaid first by when they're due, then paid newest first; salary and "other" income never appear.
        assertEquals(
            listOf("invoiced", "expected", "paid", "paidEarlier"),
            invoices(all, InvoiceFilter.ALL).map { it.id },
        )
    }

    @Test
    fun unpaidSortByDueDateElseExpectedDate() {
        val dueSoon = entry("dueSoon", IncomeStatus.INVOICED, dueDate = today.plusDays(2), date = today.plusDays(30))
        val expectedSooner = entry("noDue", IncomeStatus.EXPECTED, date = today.plusDays(5))
        val dueLater = entry("dueLater", IncomeStatus.INVOICED, dueDate = today.plusDays(9))
        assertEquals(
            listOf("dueSoon", "noDue", "dueLater"),
            invoices(listOf(dueLater, expectedSooner, dueSoon), InvoiceFilter.OUTSTANDING).map { it.id },
        )
    }

    @Test
    fun overdueOnlyWhenUnpaidAndPastDue() {
        assertTrue(entry("a", IncomeStatus.INVOICED, dueDate = today.minusDays(1)).isOverdue(today))
        assertFalse(entry("b", IncomeStatus.INVOICED, dueDate = today).isOverdue(today)) // due today is not late
        assertFalse(entry("c", IncomeStatus.EXPECTED).isOverdue(today)) // no due date
        assertFalse(entry("d", IncomeStatus.RECEIVED, dueDate = today.minusDays(9)).isOverdue(today))
    }

    @Test
    fun markingReceivedKeepsTheWorkAndDropsTheDueDate() {
        val result = markReceived(invoiced, today.minusDays(1), today) as MarkReceived.Ready
        val updated = result.entry
        assertEquals(IncomeStatus.RECEIVED, updated.status)
        assertEquals(today.minusDays(1), updated.date)
        assertNull(updated.dueDate) // FR-B4: a paid invoice has no due date; the repository deletes the field
        // Everything else is unchanged, including the source type the rules keep fixed.
        assertEquals(invoiced.copy(status = IncomeStatus.RECEIVED, date = today.minusDays(1), dueDate = null), updated)
        assertEquals(IncomeSourceType.FREELANCE, updated.sourceType)
        // Once received it no longer counts as outstanding.
        assertEquals(OutstandingSummary(2_000_000, 1), outstandingSummary(listOf(updated, expected)))
    }

    @Test
    fun markingReceivedRejectsFutureDatesAndPaidInvoices() {
        assertEquals(
            MarkReceived.Invalid("Received income can't be dated in the future."),
            markReceived(invoiced, today.plusDays(1), today),
        )
        assertTrue(markReceived(paid, today, today) is MarkReceived.Invalid)
        assertTrue(markReceived(otherUnpaid, today, today) is MarkReceived.Invalid) // not a freelance invoice
    }

    @Test
    fun titleFallsBackFromClientToProject() {
        assertEquals("Lanka Bites", invoiced.invoiceTitle())
        assertEquals("WordPress site", entry("x", IncomeStatus.INVOICED, client = null).invoiceTitle())
        assertEquals("Freelance work", entry("y", IncomeStatus.INVOICED, client = null, project = null).invoiceTitle())
    }
}
