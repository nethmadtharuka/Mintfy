package com.mintfy.app.ui.screens.expense

import java.math.BigDecimal

// Plain Kotlin so it runs in JVM unit tests.

const val MAX_NOTE_LENGTH = 200 // firestore.rules: expenses.note
const val MAX_AMOUNT_CENTS = 10_000_000_000L // LKR 100,000,000.00, far above any real expense

// Digits with up to two decimal places: "850", "850.5", "850.50". Commas are removed first.
private val AMOUNT_PATTERN = Regex("^\\d{1,12}(\\.\\d{0,2})?$")

/**
 * Converts what the user typed into whole cents, or null if it is not a valid amount.
 * BigDecimal keeps it exact: "850.10" becomes 85010, never 85009.999 (NFR-05).
 */
fun parseAmountToCents(text: String): Long? {
    val cleaned = text.trim().replace(",", "")
    if (!AMOUNT_PATTERN.matches(cleaned)) return null
    return BigDecimal(cleaned).movePointRight(2).longValueExact()
}

fun amountError(text: String): String? {
    if (text.isBlank()) return "Enter the amount."
    val cents = parseAmountToCents(text) ?: return "Enter an amount like 850 or 850.50."
    return when {
        cents <= 0 -> "The amount must be more than zero."
        cents > MAX_AMOUNT_CENTS -> "That amount is too large."
        else -> null
    }
}

fun noteError(note: String): String? =
    if (note.trim().length > MAX_NOTE_LENGTH) "Use at most $MAX_NOTE_LENGTH characters." else null
