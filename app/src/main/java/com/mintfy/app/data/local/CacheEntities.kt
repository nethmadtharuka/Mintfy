package com.mintfy.app.data.local

import androidx.room.Entity

// Local copies of the user's Firestore documents (FR-G2). Every row carries the owner's uid, so two
// accounts on one phone never see each other's data. Dates are stored as plain numbers: an expense's
// moment as epoch milliseconds, day-only dates (income, due dates) as epoch days. Money stays in
// whole cents and exchange rates as exact decimal text (NFR-05).

@Entity(tableName = "cached_expense", primaryKeys = ["uid", "id"])
data class CachedExpense(
    val uid: String,
    val id: String,
    val amountCents: Long,
    val dateMillis: Long,
    val categoryId: String,
    val paymentMethodId: String,
    val note: String?,
    val recurringId: String?,
)

@Entity(tableName = "cached_income_entry", primaryKeys = ["uid", "id"])
data class CachedIncomeEntry(
    val uid: String,
    val id: String,
    val sourceId: String,
    val sourceType: String,
    val status: String,
    val dateEpochDay: Long,
    val currency: String,
    val originalAmountCents: Long,
    val exchangeRate: String,
    val lkrAmountCents: Long,
    val baseAmountCents: Long?,
    val bonusAmountCents: Long?,
    val client: String?,
    val project: String?,
    val milestone: String?,
    val dueDateEpochDay: Long?,
    val note: String?,
)

/** Reference lists keep the order the repository shows them in ([position]). */
@Entity(tableName = "cached_category", primaryKeys = ["uid", "id"])
data class CachedCategory(
    val uid: String,
    val id: String,
    val position: Int,
    val name: String,
    val spendingType: String,
    val icon: String?,
    val builtIn: Boolean,
    val archived: Boolean,
)

@Entity(tableName = "cached_payment_method", primaryKeys = ["uid", "id"])
data class CachedPaymentMethod(
    val uid: String,
    val id: String,
    val position: Int,
    val name: String,
    val type: String,
    val archived: Boolean,
)

@Entity(tableName = "cached_income_source", primaryKeys = ["uid", "id"])
data class CachedIncomeSource(
    val uid: String,
    val id: String,
    val position: Int,
    val name: String,
    val type: String,
    val defaultCurrency: String,
    val expectedAmountCents: Long?,
    val archived: Boolean,
)

@Entity(tableName = "cached_recurring", primaryKeys = ["uid", "id"])
data class CachedRecurring(
    val uid: String,
    val id: String,
    val position: Int,
    val name: String,
    val categoryId: String,
    val paymentMethodId: String,
    val amountCents: Long,
    val frequency: String,
    val nextDueEpochDay: Long,
    val active: Boolean,
)
