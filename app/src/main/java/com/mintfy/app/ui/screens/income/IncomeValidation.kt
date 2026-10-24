package com.mintfy.app.ui.screens.income

import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.ui.screens.expense.MAX_AMOUNT_CENTS
import com.mintfy.app.ui.screens.expense.amountError
import com.mintfy.app.ui.screens.expense.noteError
import com.mintfy.app.ui.screens.expense.parseAmountToCents
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

// Plain Kotlin so it runs in JVM unit tests. Every rule here mirrors firestore.rules (incomeSources and
// incomeEntries), so a write that passes these checks is not rejected by the server.

const val BASE_CURRENCY = "LKR"

/** Offered on the form (schema §4). Any code of 3 to 5 capital letters is valid (firestore.rules). */
val SUPPORTED_CURRENCIES = listOf("LKR", "USD", "USDT")

const val MAX_SOURCE_NAME_LENGTH = 60 // firestore.rules: incomeSources.name
const val MAX_FREELANCE_TEXT_LENGTH = 80 // firestore.rules: client, project, milestone
private val MAX_EXCHANGE_RATE = BigDecimal(1_000_000) // keeps amount x rate far inside Long

private val CURRENCY_PATTERN = Regex("^[A-Z]{3,5}$")
private val RATE_PATTERN = Regex("^\\d{1,7}(\\.\\d{1,6})?$") // up to six decimal places, like 298.75

/** What the user typed on the Add income form. */
data class IncomeInput(
    val amount: String,
    val bonus: String = "",
    val currency: String = BASE_CURRENCY,
    val rate: String = "",
    val lkrAmount: String = "",
    val isLoss: Boolean = false, // crypto only
    val status: IncomeStatus = IncomeStatus.RECEIVED,
    val date: LocalDate,
    val client: String = "",
    val project: String = "",
    val milestone: String = "",
    val dueDate: LocalDate? = null,
    val note: String = "",
)

data class IncomeFieldErrors(
    val amount: String? = null,
    val bonus: String? = null,
    val currency: String? = null,
    val rate: String? = null,
    val lkrAmount: String? = null,
    val date: String? = null,
    val client: String? = null,
    val project: String? = null,
    val milestone: String? = null,
    val note: String? = null,
) {
    val hasAny: Boolean
        get() = listOf(amount, bonus, currency, rate, lkrAmount, date, client, project, milestone, note)
            .any { it != null }
}

sealed interface IncomeCheck {
    data class Valid(val entry: IncomeEntry) : IncomeCheck
    data class Invalid(val errors: IncomeFieldErrors) : IncomeCheck
}

/** Pre-selected currency for a new source: AdSense pays in USD, crypto in USDT, the rest in LKR. */
fun defaultCurrencyFor(type: IncomeSourceType): String = when (type) {
    IncomeSourceType.ADSENSE -> "USD"
    IncomeSourceType.CRYPTO -> "USDT"
    else -> BASE_CURRENCY
}

/** Salary, AdSense and crypto are recorded only when received (FR-E6). RECEIVED is always first. */
fun statusOptions(type: IncomeSourceType): List<IncomeStatus> =
    if (type.allowsUnpaid) listOf(IncomeStatus.RECEIVED, IncomeStatus.EXPECTED, IncomeStatus.INVOICED)
    else listOf(IncomeStatus.RECEIVED)

fun allowedStatus(type: IncomeSourceType, chosen: IncomeStatus): IncomeStatus =
    if (chosen in statusOptions(type)) chosen else IncomeStatus.RECEIVED

fun currencyError(code: String): String? =
    if (CURRENCY_PATTERN.matches(code)) null else "Use a currency code like LKR, USD or USDT."

/** The LKR value of one unit of the other currency, as the user typed it, or null if invalid. */
fun parseExchangeRate(text: String): BigDecimal? {
    val cleaned = text.trim()
    if (!RATE_PATTERN.matches(cleaned)) return null
    return BigDecimal(cleaned).takeIf { it.signum() > 0 && it <= MAX_EXCHANGE_RATE }
}

fun exchangeRateError(text: String): String? {
    if (text.isBlank()) return "Enter the exchange rate."
    val cleaned = text.trim()
    if (!RATE_PATTERN.matches(cleaned)) return "Enter the LKR value of one unit, like 298.75."
    return if (parseExchangeRate(cleaned) == null) "The rate must be more than zero." else null
}

/**
 * Converts an amount in another currency to LKR cents: original x rate, rounded half-up to the cent,
 * once, when the income is entered (schema §4.4). HALF_UP rounds away from zero, so a crypto loss
 * rounds the same way as the matching gain. Stored totals never recalculate this.
 */
fun convertToLkrCents(originalCents: Long, rate: BigDecimal): Long =
    BigDecimal.valueOf(originalCents).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact()

/** The LKR amount the form proposes until the user types what their bank actually credited. */
fun suggestedLkrAmount(amount: String, bonus: String, rate: String): String? {
    val base = parseAmountToCents(amount) ?: return null
    val extra = if (bonus.isBlank()) 0L else parseAmountToCents(bonus) ?: return null
    val parsedRate = parseExchangeRate(rate) ?: return null
    return formatCents(convertToLkrCents(base + extra, parsedRate))
}

/** A bonus is optional and may be zero (FR-B3). */
fun bonusError(text: String): String? {
    if (text.isBlank()) return null
    val cents = parseAmountToCents(text) ?: return "Enter a bonus like 8000 or 8000.50."
    return if (cents > MAX_AMOUNT_CENTS) "That amount is too large." else null
}

fun lkrAmountError(text: String): String? =
    if (text.isBlank()) "Enter the LKR amount credited." else amountError(text)

/** Received income can't be in the future; an unpaid entry's date is when payment is expected. */
fun incomeDateError(date: LocalDate, today: LocalDate, status: IncomeStatus): String? =
    if (status == IncomeStatus.RECEIVED && date.isAfter(today)) "Received income can't be dated in the future." else null

fun freelanceTextError(text: String): String? =
    if (text.trim().length > MAX_FREELANCE_TEXT_LENGTH) "Use at most $MAX_FREELANCE_TEXT_LENGTH characters." else null

fun sourceNameError(name: String): String? = when {
    name.isBlank() -> "Enter a name for this source."
    name.trim().length > MAX_SOURCE_NAME_LENGTH -> "Use at most $MAX_SOURCE_NAME_LENGTH characters."
    else -> null
}

/** Expected salary is optional; when given it is an LKR amount above zero. */
fun expectedAmountError(text: String): String? = if (text.isBlank()) null else amountError(text)

/**
 * Validates the form for [source] and builds the entry to save. Fields that do not apply to the
 * source type are ignored, so a value typed before switching source is never saved by mistake.
 */
fun checkIncome(input: IncomeInput, source: IncomeSource, today: LocalDate): IncomeCheck {
    val type = source.type
    val status = allowedStatus(type, input.status)
    val isSalary = type == IncomeSourceType.SALARY
    val isFreelance = type == IncomeSourceType.FREELANCE
    val isLkr = input.currency == BASE_CURRENCY

    var errors = IncomeFieldErrors(
        amount = amountError(input.amount),
        bonus = if (isSalary) bonusError(input.bonus) else null,
        currency = currencyError(input.currency),
        rate = if (isLkr) null else exchangeRateError(input.rate),
        lkrAmount = if (isLkr) null else lkrAmountError(input.lkrAmount),
        date = incomeDateError(input.date, today, status),
        client = if (isFreelance) freelanceTextError(input.client) else null,
        project = if (isFreelance) freelanceTextError(input.project) else null,
        milestone = if (isFreelance) freelanceTextError(input.milestone) else null,
        note = noteError(input.note),
    )
    if (errors.hasAny) return IncomeCheck.Invalid(errors)

    // The checks above guarantee these parse.
    val base = parseAmountToCents(input.amount)!!
    val bonus = if (isSalary && input.bonus.isNotBlank()) parseAmountToCents(input.bonus)!! else null
    val total = base + (bonus ?: 0L)
    if (total > MAX_AMOUNT_CENTS) {
        errors = errors.copy(amount = "That amount is too large.")
        return IncomeCheck.Invalid(errors)
    }

    // Crypto is a signed gain or loss (FR-B9); both amounts carry the same sign, as the rules require.
    val negative = type.isSigned && input.isLoss
    fun signed(cents: Long) = if (negative) -cents else cents

    // An LKR receipt has no conversion: rate exactly 1 and the same amount (firestore.rules, FR-B5).
    val rate = if (isLkr) BigDecimal.ONE else parseExchangeRate(input.rate)!!
    val lkr = if (isLkr) total else parseAmountToCents(input.lkrAmount)!!

    val unpaidFreelance = isFreelance && status != IncomeStatus.RECEIVED
    return IncomeCheck.Valid(
        IncomeEntry(
            source = source,
            status = status,
            date = input.date,
            currency = input.currency,
            originalAmountCents = signed(total),
            exchangeRate = rate,
            lkrAmountCents = signed(lkr),
            baseAmountCents = if (isSalary) base else null,
            bonusAmountCents = bonus,
            client = input.client.takeIf { isFreelance }?.trim()?.ifEmpty { null },
            project = input.project.takeIf { isFreelance }?.trim()?.ifEmpty { null },
            milestone = input.milestone.takeIf { isFreelance }?.trim()?.ifEmpty { null },
            dueDate = input.dueDate.takeIf { unpaidFreelance },
            note = input.note.trim().ifEmpty { null },
        )
    )
}

/** "120,000" for whole amounts, "15,624.63" otherwise, always with a dot so it parses back. */
fun formatCents(cents: Long): String {
    val pattern = if (cents % 100 == 0L) "#,##0" else "#,##0.00"
    return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(BigDecimal.valueOf(cents, 2))
}

/** FR-B3: how a salary receipt compares with the source's expected salary. */
fun salaryDifferenceLabel(receivedLkrCents: Long, expectedLkrCents: Long): String {
    val difference = receivedLkrCents - expectedLkrCents
    return when {
        difference > 0 -> "LKR ${formatCents(difference)} above your expected salary"
        difference < 0 -> "LKR ${formatCents(-difference)} below your expected salary"
        else -> "Matches your expected salary"
    }
}
