package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /**
     * Saves a new expense and returns its ID. Inputs must already be validated (see ExpenseValidation).
     *
     * The write is not awaited. Firestore applies it to the phone's local copy at once, so every
     * listener (and the dashboard later) sees it immediately, and it is sent to the server in the
     * background, including later if the phone is offline now (FR-G1, FR-G3, NFR-03). Waiting for
     * the server would freeze the save button whenever there is no signal.
     */
    fun addExpense(
        amountCents: Long,
        date: LocalDate,
        categoryId: String,
        paymentMethodId: String,
        note: String?,
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val doc = expenses(uid).document() // ID generated on the phone, so it works offline (schema D-08)

        val data = buildMap {
            put(AMOUNT_CENTS, amountCents)
            put(DATE, date.toTimestamp())
            put(CATEGORY_ID, categoryId)
            put(PAYMENT_METHOD_ID, paymentMethodId)
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { put(NOTE, it) }
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        doc.set(data).addOnFailureListener { Log.w(TAG, "Expense ${doc.id} was rejected by the server", it) }
        return Result.success(doc.id)
    }

    private fun expenses(uid: String) =
        firestore.collection("users").document(uid).collection("expenses")

    private companion object {
        const val TAG = "ExpenseRepository"

        // Field names from firestore.rules (users/{uid}/expenses).
        const val AMOUNT_CENTS = "amountCents"
        const val DATE = "date"
        const val CATEGORY_ID = "categoryId"
        const val PAYMENT_METHOD_ID = "paymentMethodId"
        const val NOTE = "note"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}

/**
 * Today keeps the real time, so today's entries stay in the order they happened.
 * Any other day is stored at midday, which keeps it inside that day in any nearby time zone.
 * Shared with IncomeRepository so income and expense dates line up.
 */
internal fun LocalDate.toTimestamp(): Timestamp {
    val zone = ZoneId.systemDefault()
    if (this == LocalDate.now(zone)) return Timestamp.now()
    return Timestamp(Date.from(atTime(LocalTime.NOON).atZone(zone).toInstant()))
}
