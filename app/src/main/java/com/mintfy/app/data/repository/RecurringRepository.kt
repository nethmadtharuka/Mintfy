package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mintfy.app.data.local.CacheDao
import com.mintfy.app.data.local.toCached
import com.mintfy.app.data.local.toModel
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
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
class RecurringRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cache: CacheDao,
) {

    /**
     * Emits all of the user's recurring expenses, active and paused, in real time (FR-C6).
     * Paused ones are included so they can be resumed; the screen groups and sorts them.
     * A person has only a handful, so one small listener is enough and no extra index is needed.
     */
    fun observeRecurring(): Flow<List<RecurringExpense>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val cacheFirst = CacheFirst(this, "recurring expenses") { list ->
            cache.replaceRecurring(uid, list.mapIndexed { i, item -> item.toCached(uid, i) })
        }
        cacheFirst.startFrom { cache.recurring(uid).mapNotNull { it.toModel() } }
        val registration = recurring(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toRepositoryException())
                return@addSnapshotListener
            }
            cacheFirst.onLive(snapshot?.documents.orEmpty().mapNotNull { it.toRecurring() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun getRecurring(id: String): Result<RecurringExpense> = attempt {
        val uid = auth.currentUser?.uid ?: throw signedOut()
        recurring(uid).document(id).getPreferCache()?.toRecurring()
            ?: throw RepositoryException("This recurring expense no longer exists.")
    }

    /** Creates a recurring expense. Inputs must already be validated (see RecurringValidation). */
    suspend fun addRecurring(item: RecurringExpense): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = item.toFields() + mapOf(
            CREATED_AT to FieldValue.serverTimestamp(),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        return recurring(uid).document().createAndConfirm(data) { it.toSaveError("recurring expense") }
    }

    /** Changes every editable field. createdAt is left alone, as the rules require. */
    suspend fun updateRecurring(item: RecurringExpense): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = item.toFields() + (UPDATED_AT to FieldValue.serverTimestamp())
        return recurring(uid).document(item.id).confirmWrite({ it.toSaveError("recurring expense") }) { update(data) }
    }

    /** Pauses or resumes it. A paused item stays in the list but is never due. */
    suspend fun setActive(id: String, active: Boolean): Result<SaveOutcome> =
        updateFields(id, mapOf(ACTIVE to active))

    /**
     * Moves it to its next period once this period's expense has been recorded. Like adding an
     * expense, it is not awaited: the phone's copy changes at once and the server catches up,
     * including later when offline, so recording a due item stays instant.
     */
    fun advanceTo(id: String, nextDueDate: LocalDate): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = mapOf<String, Any>(
            NEXT_DUE_DATE to nextDueDate.toDueTimestamp(),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        recurring(uid).document(id).update(data)
            .addOnFailureListener { Log.w(TAG, "Recurring expense $id was not moved on", it) }
        return Result.success(Unit)
    }

    /** Expenses already recorded from it are kept; they are real spending. */
    suspend fun deleteRecurring(id: String): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        return recurring(uid).document(id).confirmWrite({ it.toSaveError("recurring expense") }) { delete() }
    }

    private suspend fun updateFields(id: String, fields: Map<String, Any>): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = fields + (UPDATED_AT to FieldValue.serverTimestamp())
        return recurring(uid).document(id).confirmWrite({ it.toSaveError("recurring expense") }) { update(data) }
    }

    private fun recurring(uid: String) =
        firestore.collection("users").document(uid).collection("recurringExpenses")

    private fun RecurringExpense.toFields(): Map<String, Any> = mapOf(
        NAME to name.trim(),
        CATEGORY_ID to categoryId,
        PAYMENT_METHOD_ID to paymentMethodId,
        AMOUNT_CENTS to amountCents,
        FREQUENCY to frequency.name,
        NEXT_DUE_DATE to nextDueDate.toDueTimestamp(),
        ACTIVE to active,
    )

    /** Null for a document that does not match the schema, so one bad document cannot break the list. */
    private fun DocumentSnapshot.toRecurring(): RecurringExpense? {
        val frequency = getString(FREQUENCY)
            ?.let { runCatching { RecurringFrequency.valueOf(it) }.getOrNull() }
            ?: return null
        return RecurringExpense(
            id = id,
            name = getString(NAME) ?: return null,
            categoryId = getString(CATEGORY_ID) ?: return null,
            paymentMethodId = getString(PAYMENT_METHOD_ID) ?: return null,
            amountCents = getLong(AMOUNT_CENTS) ?: return null,
            frequency = frequency,
            nextDueDate = getTimestamp(NEXT_DUE_DATE)?.toLocalDate() ?: return null,
            active = getBoolean(ACTIVE) ?: true,
        )
    }

    private companion object {
        const val TAG = "RecurringRepository"

        // Field names from firestore.rules (users/{uid}/recurringExpenses).
        const val NAME = "name"
        const val CATEGORY_ID = "categoryId"
        const val PAYMENT_METHOD_ID = "paymentMethodId"
        const val AMOUNT_CENTS = "amountCents"
        const val FREQUENCY = "frequency"
        const val NEXT_DUE_DATE = "nextDueDate"
        const val ACTIVE = "active"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}

/** A due date is a day, not a moment: stored at midday so it reads back as the same day nearby. */
private fun LocalDate.toDueTimestamp(): Timestamp =
    Timestamp(Date.from(atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()))
