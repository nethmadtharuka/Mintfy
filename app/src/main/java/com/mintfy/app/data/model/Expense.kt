package com.mintfy.app.data.model

import com.google.firebase.Timestamp

/**
 * A document at users/{uid}/expenses/{id}. Expenses are always in LKR.
 * The committed/discretionary type is not stored here; it comes from the category (schema D-05).
 */
data class Expense(
    val id: String,
    val amountCents: Long, // whole cents: LKR 850.50 is 85050 (NFR-05)
    val date: Timestamp,
    val categoryId: String,
    val paymentMethodId: String,
    val note: String? = null,
    val recurringId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)
