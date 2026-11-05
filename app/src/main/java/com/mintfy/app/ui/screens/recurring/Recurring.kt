package com.mintfy.app.ui.screens.recurring

import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import com.mintfy.app.ui.screens.expense.amountError
import com.mintfy.app.ui.screens.expense.parseAmountToCents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Plain Kotlin so it runs in JVM unit tests. Limits mirror firestore.rules (recurringExpenses).

const val MAX_RECURRING_NAME_LENGTH = 60

/** The day after this period: one week, month or year on. 31 Jan + 1 month is 28 (or 29) Feb. */
fun RecurringFrequency.next(date: LocalDate): LocalDate = when (this) {
    RecurringFrequency.WEEKLY -> date.plusWeeks(1)
    RecurringFrequency.MONTHLY -> date.plusMonths(1)
    RecurringFrequency.YEARLY -> date.plusYears(1)
}

/** Due on or before [today], and not paused. */
fun RecurringExpense.isDue(today: LocalDate): Boolean = active && !nextDueDate.isAfter(today)

/**
 * The list on Figma "09 Recurring": what needs recording now first, then what is coming up,
 * then paused items. Each part is ordered by due date.
 */
data class RecurringGroups(
    val due: List<RecurringExpense>,
    val upcoming: List<RecurringExpense>,
    val paused: List<RecurringExpense>,
) {
    val isEmpty: Boolean get() = due.isEmpty() && upcoming.isEmpty() && paused.isEmpty()
}

fun groupRecurring(items: List<RecurringExpense>, today: LocalDate): RecurringGroups {
    val sorted = items.sortedWith(compareBy({ it.nextDueDate }, { it.name.lowercase() }))
    return RecurringGroups(
        due = sorted.filter { it.isDue(today) },
        upcoming = sorted.filter { it.active && !it.isDue(today) },
        paused = sorted.filter { !it.active },
    )
}

/**
 * What the active recurring costs come to in an average month, so Kavindu sees his fixed load in one
 * number: weekly x 52 / 12, yearly / 12. Paused items are left out.
 */
fun monthlyEquivalentCents(items: List<RecurringExpense>): Long = items
    .filter { it.active }
    .sumOf {
        when (it.frequency) {
            RecurringFrequency.WEEKLY -> it.amountCents * 52 / 12
            RecurringFrequency.MONTHLY -> it.amountCents
            RecurringFrequency.YEARLY -> it.amountCents / 12
        }
    }

/** Figma: "Monthly · 1st". Weekly shows the weekday, yearly the day and month. */
fun scheduleLabel(frequency: RecurringFrequency, nextDueDate: LocalDate): String = when (frequency) {
    RecurringFrequency.WEEKLY ->
        "Weekly · ${nextDueDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
    RecurringFrequency.MONTHLY -> "Monthly · ${ordinal(nextDueDate.dayOfMonth)}"
    RecurringFrequency.YEARLY -> "Yearly · ${nextDueDate.format(DAY_MONTH)}"
}

/** "Due today", "Due since 3 Oct" or "Next 1 Nov". */
fun dueLabel(item: RecurringExpense, today: LocalDate): String = when {
    !item.active -> "Paused"
    item.nextDueDate == today -> "Due today"
    item.nextDueDate.isBefore(today) -> "Due since ${item.nextDueDate.format(DAY_MONTH)}"
    else -> "Next ${item.nextDueDate.format(DAY_MONTH)}"
}

fun RecurringFrequency.displayName(): String = when (this) {
    RecurringFrequency.WEEKLY -> "Weekly"
    RecurringFrequency.MONTHLY -> "Monthly"
    RecurringFrequency.YEARLY -> "Yearly"
}

fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

// ---------- Form validation ----------

/** What the user typed on the add / edit recurring form. */
data class RecurringInput(
    val name: String,
    val amount: String,
    val categoryId: String?,
    val paymentMethodId: String?,
    val frequency: RecurringFrequency,
    val nextDueDate: LocalDate,
)

data class RecurringFieldErrors(
    val name: String? = null,
    val amount: String? = null,
    val category: String? = null,
    val paymentMethod: String? = null,
) {
    val hasAny: Boolean get() = listOfNotNull(name, amount, category, paymentMethod).isNotEmpty()
}

sealed interface RecurringCheck {
    data class Valid(val item: RecurringExpense) : RecurringCheck
    data class Invalid(val errors: RecurringFieldErrors) : RecurringCheck
}

/** Validates the form and builds the item to save. [id] is empty for a new item. */
fun checkRecurring(input: RecurringInput, id: String = "", active: Boolean = true): RecurringCheck {
    val name = input.name.trim()
    val errors = RecurringFieldErrors(
        name = when {
            name.isEmpty() -> "Enter a name, such as Rent."
            name.length > MAX_RECURRING_NAME_LENGTH -> "Use at most $MAX_RECURRING_NAME_LENGTH characters."
            else -> null
        },
        amount = amountError(input.amount),
        category = if (input.categoryId == null) "Choose a category." else null,
        paymentMethod = if (input.paymentMethodId == null) "Choose how it is paid." else null,
    )
    if (errors.hasAny) return RecurringCheck.Invalid(errors)
    return RecurringCheck.Valid(
        RecurringExpense(
            id = id,
            name = name,
            categoryId = input.categoryId!!,
            paymentMethodId = input.paymentMethodId!!,
            amountCents = parseAmountToCents(input.amount)!!,
            frequency = input.frequency,
            nextDueDate = input.nextDueDate,
            active = active,
        )
    )
}
