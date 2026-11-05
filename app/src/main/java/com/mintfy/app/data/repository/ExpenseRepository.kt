package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mintfy.app.data.model.Expense
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
     * [recurringId] links an expense recorded from a recurring item (FR-C6).
     */
    fun addExpense(
        amountCents: Long,
        date: LocalDate,
        categoryId: String,
        paymentMethodId: String,
        note: String?,
        recurringId: String? = null,
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val doc = expenses(uid).document() // ID generated on the phone, so it works offline (schema D-08)

        val data = buildMap {
            put(AMOUNT_CENTS, amountCents)
            put(DATE, date.toTimestamp())
            put(CATEGORY_ID, categoryId)
            put(PAYMENT_METHOD_ID, paymentMethodId)
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { put(NOTE, it) }
            recurringId?.let { put(RECURRING_ID, it) }
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        doc.set(data).addOnFailureListener { Log.w(TAG, "Expense ${doc.id} was rejected by the server", it) }
        return Result.success(doc.id)
    }

    /**
     * Expenses dated [from] to [to] inclusive, newest first (schema query Q5), in real time.
     * Served from the phone's cache first, so it also works offline. The listener is removed when collection ends.
     */
    fun observeExpenses(from: LocalDate, to: LocalDate): Flow<List<Expense>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val registration = expenses(uid)
            .whereGreaterThanOrEqualTo(DATE, from.startOfDay())
            .whereLessThan(DATE, to.plusDays(1).startOfDay())
            .orderBy(DATE, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toExpense() })
            }
        awaitClose { registration.remove() }
    }

    suspend fun getExpense(id: String): Result<Expense> = attempt {
        val uid = auth.currentUser?.uid ?: throw signedOut()
        expenses(uid).document(id).getPreferCache()?.toExpense() ?: throw entryNotFound()
    }

    /**
     * Changes an existing expense (FR-C5). createdAt is left alone, as the rules require; a cleared note
     * is removed. The date is only rewritten when [dateChanged], so an unchanged entry keeps its time of day.
     */
    suspend fun updateExpense(
        id: String,
        amountCents: Long,
        date: LocalDate,
        dateChanged: Boolean,
        categoryId: String,
        paymentMethodId: String,
        note: String?,
    ): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = buildMap<String, Any> {
            put(AMOUNT_CENTS, amountCents)
            if (dateChanged) put(DATE, date.toTimestamp())
            put(CATEGORY_ID, categoryId)
            put(PAYMENT_METHOD_ID, paymentMethodId)
            put(NOTE, note?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        return expenses(uid).document(id).confirmWrite({ it.toSaveError("expense") }) { update(data) }
    }

    suspend fun deleteExpense(id: String): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        return expenses(uid).document(id).confirmWrite({ it.toSaveError("expense") }) { delete() }
    }

    private fun expenses(uid: String) =
        firestore.collection("users").document(uid).collection("expenses")

    /** Null for a document that does not match the schema, so one bad document cannot break the list. */
    private fun DocumentSnapshot.toExpense(): Expense? = Expense(
        id = id,
        amountCents = getLong(AMOUNT_CENTS) ?: return null,
        date = getTimestamp(DATE) ?: return null,
        categoryId = getString(CATEGORY_ID) ?: return null,
        paymentMethodId = getString(PAYMENT_METHOD_ID) ?: return null,
        note = getString(NOTE),
        recurringId = getString(RECURRING_ID),
        createdAt = getTimestamp(CREATED_AT),
        updatedAt = getTimestamp(UPDATED_AT),
    )

    private companion object {
        const val TAG = "ExpenseRepository"

        // Field names from firestore.rules (users/{uid}/expenses).
        const val AMOUNT_CENTS = "amountCents"
        const val DATE = "date"
        const val CATEGORY_ID = "categoryId"
        const val PAYMENT_METHOD_ID = "paymentMethodId"
        const val NOTE = "note"
        const val RECURRING_ID = "recurringId"
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

/** Midnight at the start of this day on the phone, for date-range queries. */
internal fun LocalDate.startOfDay(): Timestamp =
    Timestamp(Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant()))

fun Timestamp.toLocalDate(): LocalDate = toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
