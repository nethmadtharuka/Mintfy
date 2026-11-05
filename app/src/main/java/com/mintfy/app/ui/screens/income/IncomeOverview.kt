package com.mintfy.app.ui.screens.income

import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.screens.dashboard.EstimateCheck
import com.mintfy.app.ui.screens.invoices.InvoiceFilter
import com.mintfy.app.ui.screens.invoices.OutstandingSummary
import com.mintfy.app.ui.screens.invoices.invoices
import com.mintfy.app.ui.screens.invoices.outstandingSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Plain Kotlin so it runs in JVM unit tests. Builds everything the Income screen shows
// (Figma "04 Income & freelance invoices") from the stored entries; no totals are stored.

/** One received payment this month, as a Figma row: title, a detail line and the LKR amount. */
data class IncomeRow(
    val id: String,
    val title: String,
    val detail: String,
    val lkrAmountCents: Long, // negative for a crypto loss
)

/** FR-B9: crypto gains minus losses since 1 January, so Kavindu can finally answer "what's my crypto net?". */
data class CryptoYearToDate(val year: Int, val netCents: Long, val entryCount: Int)

data class IncomeOverview(
    val month: YearMonth,
    val receivedCents: Long,
    val rows: List<IncomeRow>,
    val cryptoYearToDate: CryptoYearToDate?, // null when there is no crypto this year
    val outstanding: OutstandingSummary,
    val nextInvoice: IncomeEntry?, // the unpaid invoice due soonest, for "Mark as paid"
    val estimate: EstimateCheck?,
)

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * @param yearEntries income entries dated from 1 January of [month]'s year to the end of [month].
 * @param freelanceEntries every freelance entry (paid or not), for the invoices card.
 */
fun incomeOverview(
    month: YearMonth,
    yearEntries: List<IncomeEntry>,
    freelanceEntries: List<IncomeEntry>,
    sources: List<IncomeSource>,
    estimateMinCents: Long?,
    estimateMaxCents: Long?,
): IncomeOverview {
    val sourceById = sources.associateBy { it.id }
    val receivedThisMonth = yearEntries
        .filter { it.status == IncomeStatus.RECEIVED && YearMonth.from(it.date) == month && it.id != null }
        .sortedByDescending { it.date }
    val receivedTotal = receivedThisMonth.sumOf { it.lkrAmountCents }

    val crypto = yearEntries.filter {
        it.sourceType == IncomeSourceType.CRYPTO && it.status == IncomeStatus.RECEIVED && it.date.year == month.year
    }

    return IncomeOverview(
        month = month,
        receivedCents = receivedTotal,
        rows = receivedThisMonth.map { it.toRow(sourceById[it.sourceId]) },
        cryptoYearToDate = crypto.takeIf { it.isNotEmpty() }
            ?.let { CryptoYearToDate(month.year, it.sumOf { e -> e.lkrAmountCents }, it.size) },
        outstanding = outstandingSummary(freelanceEntries),
        nextInvoice = invoices(freelanceEntries, InvoiceFilter.OUTSTANDING).firstOrNull(),
        estimate = if (estimateMinCents != null && estimateMaxCents != null) {
            EstimateCheck(estimateMinCents, estimateMaxCents, receivedTotal)
        } else {
            null
        },
    )
}

/**
 * The Figma detail line for each kind of income:
 * salary "Base 120,000 + bonus 8,000 · LKR 8,000 above your expected salary" (FR-B3),
 * foreign currency "USD 52.30 at 298.75 · Received 28 Sep" (FR-B5),
 * crypto "Gain · 3 Oct" or "Loss · 3 Oct" (FR-B9), freelance "Lanka Bites · WordPress site".
 */
internal fun IncomeEntry.toRow(source: IncomeSource?): IncomeRow {
    val received = "Received ${date.format(dayMonth)}"
    val detail = when {
        sourceType == IncomeSourceType.SALARY -> listOfNotNull(
            if (baseAmountCents != null && bonusAmountCents != null) {
                "Base ${formatCents(baseAmountCents)} + bonus ${formatCents(bonusAmountCents)}"
            } else {
                received
            },
            source?.expectedAmountCents?.let { salaryDifferenceLabel(lkrAmountCents, it) },
        ).joinToString(" · ")
        sourceType == IncomeSourceType.CRYPTO ->
            "${if (lkrAmountCents < 0) "Loss" else "Gain"} · ${date.format(dayMonth)}"
        currency != BASE_CURRENCY ->
            "$currency ${formatCents(kotlin.math.abs(originalAmountCents))} at ${exchangeRate.stripTrailingZeros().toPlainString()} · $received"
        sourceType == IncomeSourceType.FREELANCE ->
            listOfNotNull(client, project).joinToString(" · ").ifEmpty { received }
        else -> received
    }
    return IncomeRow(
        id = id.orEmpty(),
        title = source?.name ?: sourceType.displayName(),
        detail = detail,
        lkrAmountCents = lkrAmountCents,
    )
}

/** A readable name when the source is no longer listed (archived). */
fun IncomeSourceType.displayName(): String = when (this) {
    IncomeSourceType.SALARY -> "Salary"
    IncomeSourceType.FREELANCE -> "Freelance"
    IncomeSourceType.ADSENSE -> "AdSense"
    IncomeSourceType.CRYPTO -> "Crypto"
    IncomeSourceType.OTHER -> "Other income"
}

/** FR-B8: both blank clears the estimate; otherwise both are needed and the low end comes first. */
sealed interface EstimateInput {
    data class Valid(val minCents: Long?, val maxCents: Long?) : EstimateInput
    data class Invalid(val minError: String?, val maxError: String?) : EstimateInput
}

fun checkEstimate(minText: String, maxText: String): EstimateInput {
    if (minText.isBlank() && maxText.isBlank()) return EstimateInput.Valid(null, null)
    val min = parseLkrCents(minText)
    val max = parseLkrCents(maxText)
    val minError = when {
        minText.isBlank() -> "Enter the lowest amount."
        min == null -> "Enter an amount like 160,000."
        else -> null
    }
    val maxError = when {
        maxText.isBlank() -> "Enter the highest amount."
        max == null -> "Enter an amount like 210,000."
        min != null && max < min -> "The highest amount can't be below the lowest."
        else -> null
    }
    return if (minError == null && maxError == null) EstimateInput.Valid(min, max) else EstimateInput.Invalid(minError, maxError)
}

/** Whole or decimal LKR ("160,000" or "160000.50") into cents; null if not a valid amount of zero or more. */
private fun parseLkrCents(text: String): Long? {
    val cleaned = text.trim().replace(",", "")
    if (!Regex("^\\d{1,12}(\\.\\d{0,2})?$").matches(cleaned)) return null
    return java.math.BigDecimal(cleaned).movePointRight(2).longValueExact()
}

/** 1 January to the end of [month]: one listener covers both this month's rows and the crypto year-to-date. */
fun yearToMonthRange(month: YearMonth): Pair<LocalDate, LocalDate> = month.withMonth(1).atDay(1) to month.atEndOfMonth()
