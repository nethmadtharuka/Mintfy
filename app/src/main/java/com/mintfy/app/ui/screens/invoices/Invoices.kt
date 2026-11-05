package com.mintfy.app.ui.screens.invoices

import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.screens.income.incomeDateError
import java.time.LocalDate

// Plain Kotlin so it runs in JVM unit tests. Invoices are freelance income entries; there is no
// separate invoice collection, so marking one received moves it into income everywhere at once.

/** The status chips on the Invoices screen. Outstanding means expected or invoiced, not yet received. */
enum class InvoiceFilter(val label: String) {
    OUTSTANDING("Outstanding"),
    EXPECTED("Expected"),
    INVOICED("Invoiced"),
    RECEIVED("Received"),
    ALL("All"),
}

/** SRS 1.4: a freelance entry whose status is expected or invoiced, not yet received (FR-B4, FR-B7). */
fun IncomeEntry.isOutstandingInvoice(): Boolean =
    sourceType == IncomeSourceType.FREELANCE && status != IncomeStatus.RECEIVED

/** What the dashboard shows apart from received income (FR-B7). Never counted as income or savings (FR-E6). */
data class OutstandingSummary(val totalLkrCents: Long, val count: Int)

fun outstandingSummary(entries: List<IncomeEntry>): OutstandingSummary {
    val outstanding = entries.filter { it.isOutstandingInvoice() }
    return OutstandingSummary(outstanding.sumOf { it.lkrAmountCents }, outstanding.size)
}

/** Unpaid and past its due date, so worth a follow-up (E-02: two invoices were forgotten). */
fun IncomeEntry.isOverdue(today: LocalDate): Boolean =
    isOutstandingInvoice() && dueDate?.isBefore(today) == true

/**
 * The freelance entries that match [filter]. Unpaid ones come first, the soonest due first
 * (an invoice with no due date goes by its expected payment date); received ones follow, newest first.
 */
fun invoices(entries: List<IncomeEntry>, filter: InvoiceFilter): List<IncomeEntry> {
    val freelance = entries.filter { it.sourceType == IncomeSourceType.FREELANCE && it.id != null }
    val matching = when (filter) {
        InvoiceFilter.OUTSTANDING -> freelance.filter { it.status != IncomeStatus.RECEIVED }
        InvoiceFilter.EXPECTED -> freelance.filter { it.status == IncomeStatus.EXPECTED }
        InvoiceFilter.INVOICED -> freelance.filter { it.status == IncomeStatus.INVOICED }
        InvoiceFilter.RECEIVED -> freelance.filter { it.status == IncomeStatus.RECEIVED }
        InvoiceFilter.ALL -> freelance
    }
    val (paid, unpaid) = matching.partition { it.status == IncomeStatus.RECEIVED }
    return unpaid.sortedBy { it.dueDate ?: it.date } + paid.sortedByDescending { it.date }
}

/** Client, else project, else a generic name: the label Kavindu knows the work by. */
fun IncomeEntry.invoiceTitle(): String = client ?: project ?: "Freelance work"

sealed interface MarkReceived {
    data class Ready(val entry: IncomeEntry) : MarkReceived
    data class Invalid(val message: String) : MarkReceived
}

/**
 * The entry after the money arrived on [receivedOn]: status RECEIVED, dated the day it was paid, and with
 * no due date, which only applies while unpaid (the same rule checkIncome applies). The source, amounts,
 * client, project and milestone are kept. The repository removes the stored dueDate field.
 */
fun markReceived(entry: IncomeEntry, receivedOn: LocalDate, today: LocalDate): MarkReceived {
    if (!entry.isOutstandingInvoice()) return MarkReceived.Invalid("This invoice has already been received.")
    incomeDateError(receivedOn, today, IncomeStatus.RECEIVED)?.let { return MarkReceived.Invalid(it) }
    return MarkReceived.Ready(entry.copy(status = IncomeStatus.RECEIVED, date = receivedOn, dueDate = null))
}
