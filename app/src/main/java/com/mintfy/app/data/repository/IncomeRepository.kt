package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mintfy.app.data.model.IncomeEntry
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** A saved document. [pendingSync] means it is on this phone but the server has not confirmed it yet. */
data class SaveOutcome(val id: String, val pendingSync: Boolean)

@Singleton
class IncomeRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /** Saves a new income entry. Inputs must already be validated (see IncomeValidation). */
    suspend fun addEntry(entry: IncomeEntry): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val doc = entries(uid).document() // ID generated on the phone, so it works offline (schema D-08)

        val data = buildMap {
            put(SOURCE_ID, entry.source.id)
            put(SOURCE_TYPE, entry.source.type.name) // copied from the source; the rules keep it fixed
            put(STATUS, entry.status.name)
            put(DATE, entry.date.toTimestamp())
            put(CURRENCY, entry.currency)
            put(ORIGINAL_AMOUNT_CENTS, entry.originalAmountCents)
            // The rules require exactly 1 for LKR; an integer avoids any 1 vs 1.0 doubt.
            put(EXCHANGE_RATE, if (entry.currency == "LKR") 1L else entry.exchangeRate.toDouble())
            put(LKR_AMOUNT_CENTS, entry.lkrAmountCents)
            entry.baseAmountCents?.let { put(BASE_AMOUNT_CENTS, it) }
            entry.bonusAmountCents?.let { put(BONUS_AMOUNT_CENTS, it) }
            entry.client?.let { put(CLIENT, it) }
            entry.project?.let { put(PROJECT, it) }
            entry.milestone?.let { put(MILESTONE, it) }
            entry.dueDate?.let { put(DUE_DATE, it.toTimestamp()) }
            entry.note?.let { put(NOTE, it) }
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        return doc.createAndConfirm(data) { it.toSaveError("income") }
    }

    private fun entries(uid: String) =
        firestore.collection("users").document(uid).collection("incomeEntries")

    private companion object {
        // Field names from firestore.rules (users/{uid}/incomeEntries).
        const val SOURCE_ID = "sourceId"
        const val SOURCE_TYPE = "sourceType"
        const val STATUS = "status"
        const val DATE = "date"
        const val CURRENCY = "currency"
        const val ORIGINAL_AMOUNT_CENTS = "originalAmountCents"
        const val EXCHANGE_RATE = "exchangeRate"
        const val LKR_AMOUNT_CENTS = "lkrAmountCents"
        const val BASE_AMOUNT_CENTS = "baseAmountCents"
        const val BONUS_AMOUNT_CENTS = "bonusAmountCents"
        const val CLIENT = "client"
        const val PROJECT = "project"
        const val MILESTONE = "milestone"
        const val DUE_DATE = "dueDate"
        const val NOTE = "note"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}

private const val TAG = "IncomeRepository"

// Long enough for a normal online save, short enough that the button never looks frozen offline.
private const val SERVER_CONFIRM_TIMEOUT_MS = 5_000L

/**
 * Creates the document and waits briefly for the server.
 * Firestore applies the write on the phone at once; the task completes when the server accepts it,
 * or fails if it is rejected (for example PERMISSION_DENIED). A rejection is returned as a readable
 * error, so the form can keep what the user typed. With no connection the server never answers in
 * time: the write stays queued on the phone and syncs later (FR-G1, FR-G3, NFR-03), and the result
 * says so through [SaveOutcome.pendingSync].
 */
internal suspend fun DocumentReference.createAndConfirm(
    data: Map<String, Any>,
    toError: (Throwable) -> RepositoryException,
): Result<SaveOutcome> = attempt(toError) {
    val write = set(data)
    val confirmed = withTimeoutOrNull(SERVER_CONFIRM_TIMEOUT_MS) { write.await(); true } ?: false
    if (!confirmed) write.addOnFailureListener { Log.w(TAG, "Queued write to $path was rejected later", it) }
    SaveOutcome(id, pendingSync = !confirmed)
}

/** Readable messages for a failed income or source save. [what] is "income" or "income source". */
internal fun Throwable.toSaveError(what: String): RepositoryException = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        RepositoryException("The server didn't accept this $what. Check the details and try again.", this)
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        RepositoryException("You're signed out. Sign in and try again.", this)
    else -> toRepositoryException() // network, server unavailable, or anything else
}
