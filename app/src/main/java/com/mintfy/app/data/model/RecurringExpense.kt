package com.mintfy.app.data.model

import java.time.LocalDate

/** How often a recurring cost comes round (SRS FR-C6). */
enum class RecurringFrequency { WEEKLY, MONTHLY, YEARLY }

/**
 * A document at users/{uid}/recurringExpenses/{id}, such as rent or the gym (FR-C6).
 * It is a reminder, not an expense: nothing counts towards totals until Kavindu records the
 * period's expense, because committed costs like utilities change each month (FR-D3).
 * Expenses recorded from it carry its ID in their recurringId field.
 */
data class RecurringExpense(
    val id: String,
    val name: String,
    val categoryId: String,
    val paymentMethodId: String,
    val amountCents: Long, // the usual amount; it can be changed when the period's expense is recorded
    val frequency: RecurringFrequency,
    val nextDueDate: LocalDate,
    val active: Boolean = true, // false = paused, for example a cancelled subscription
)
