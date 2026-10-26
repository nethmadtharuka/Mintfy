package com.mintfy.app.data.repository

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.mintfy.app.data.model.IncomeEntry
import com.mintfy.app.data.model.IncomeSourceType
import com.mintfy.app.data.model.IncomeStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.LocalDate
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

        val data = buildMap<String, Any> {
            entry.fields().forEach { (key, value) -> if (value != null) put(key, value) }
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        return doc.createAndConfirm(data) { it.toSaveError("income") }
    }

    /**
     * Income dated [from] to [to] inclusive, newest first, in real time, including unpaid entries.
     * Served from the phone's cache first, so it also works offline. The listener is removed when collection ends.
     */
    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<IncomeEntry>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val registration = entries(uid)
            .whereGreaterThanOrEqualTo(DATE, from.startOfDay())
            .whereLessThan(DATE, to.plusDays(1).startOfDay())
            .orderBy(DATE, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toIncomeEntry() })
            }
        awaitClose { registration.remove() }
    }

    suspend fun getEntry(id: String): Result<IncomeEntry> = attempt {
        val uid = auth.currentUser?.uid ?: throw signedOut()
        entries(uid).document(id).getPreferCache()?.toIncomeEntry() ?: throw entryNotFound()
    }

    /**
     * Changes an existing entry (FR-C5). Inputs must already be validated. createdAt is left alone and every
     * optional field that no longer applies is removed, so a cleared bonus or a paid invoice's due date
     * cannot linger (the rules check base + bonus against the total). The source type must not change.
     * The date is only rewritten when [dateChanged], so an unchanged entry keeps its time of day.
     */
    suspend fun updateEntry(id: String, entry: IncomeEntry, dateChanged: Boolean): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = buildMap<String, Any> {
            entry.fields().forEach { (key, value) -> put(key, value ?: FieldValue.delete()) }
            if (!dateChanged) remove(DATE)
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        return entries(uid).document(id).confirmWrite({ it.toSaveError("income") }) { update(data) }
    }

    suspend fun deleteEntry(id: String): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        return entries(uid).document(id).confirmWrite({ it.toSaveError("income") }) { delete() }
    }

    private fun entries(uid: String) =
        firestore.collection("users").document(uid).collection("incomeEntries")

    /** Every field an entry can have; null means the field does not apply to this entry. */
    private fun IncomeEntry.fields(): Map<String, Any?> = mapOf(
        SOURCE_ID to sourceId,
        SOURCE_TYPE to sourceType.name,
        STATUS to status.name,
        DATE to date.toTimestamp(),
        CURRENCY to currency,
        ORIGINAL_AMOUNT_CENTS to originalAmountCents,
        // The rules require exactly 1 for LKR; an integer avoids any 1 vs 1.0 doubt.
        EXCHANGE_RATE to if (currency == "LKR") 1L else exchangeRate.toDouble(),
        LKR_AMOUNT_CENTS to lkrAmountCents,
        BASE_AMOUNT_CENTS to baseAmountCents,
        BONUS_AMOUNT_CENTS to bonusAmountCents,
        CLIENT to client,
        PROJECT to project,
        MILESTONE to milestone,
        DUE_DATE to dueDate?.toTimestamp(),
        NOTE to note,
    )

    /** Null for a document that does not match the schema, so one bad document cannot break the list. */
    private fun DocumentSnapshot.toIncomeEntry(): IncomeEntry? {
        val type = getString(SOURCE_TYPE)?.let { runCatching { IncomeSourceType.valueOf(it) }.getOrNull() }
            ?: return null
        val status = getString(STATUS)?.let { runCatching { IncomeStatus.valueOf(it) }.getOrNull() }
            ?: return null
        return IncomeEntry(
            id = id,
            sourceId = getString(SOURCE_ID) ?: return null,
            sourceType = type,
            status = status,
            date = getTimestamp(DATE)?.toLocalDate() ?: return null,
            currency = getString(CURRENCY) ?: return null,
            originalAmountCents = getLong(ORIGINAL_AMOUNT_CENTS) ?: return null,
            exchangeRate = getDouble(EXCHANGE_RATE)?.let { BigDecimal.valueOf(it) } ?: return null,
            lkrAmountCents = getLong(LKR_AMOUNT_CENTS) ?: return null,
            baseAmountCents = getLong(BASE_AMOUNT_CENTS),
            bonusAmountCents = getLong(BONUS_AMOUNT_CENTS),
            client = getString(CLIENT),
            project = getString(PROJECT),
            milestone = getString(MILESTONE),
            dueDate = getTimestamp(DUE_DATE)?.toLocalDate(),
            note = getString(NOTE),
        )
    }

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

/** Creates the document and waits briefly for the server (see [confirmWrite]). */
internal suspend fun DocumentReference.createAndConfirm(
    data: Map<String, Any>,
    toError: (Throwable) -> RepositoryException,
): Result<SaveOutcome> = confirmWrite(toError) { set(data) }

/**
 * Runs [write] (a set, update or delete) and waits briefly for the server.
 * Firestore applies the write on the phone at once; the task completes when the server accepts it,
 * or fails if it is rejected (for example PERMISSION_DENIED). A rejection is returned as a readable
 * error, so the form can keep what the user typed. With no connection the server never answers in
 * time: the write stays queued on the phone and syncs later (FR-G1, FR-G3, NFR-03), and the result
 * says so through [SaveOutcome.pendingSync].
 */
internal suspend fun DocumentReference.confirmWrite(
    toError: (Throwable) -> RepositoryException,
    write: DocumentReference.() -> Task<Void>,
): Result<SaveOutcome> = attempt(toError) {
    val task = write()
    val confirmed = withTimeoutOrNull(SERVER_CONFIRM_TIMEOUT_MS) { task.await(); true } ?: false
    if (!confirmed) task.addOnFailureListener { Log.w(TAG, "Queued write to $path was rejected later", it) }
    SaveOutcome(id, pendingSync = !confirmed)
}

/**
 * Reads a document from the phone's cache, or from the server when it is not cached.
 * The cache answers at once, even offline; a server-only read would wait for a connection.
 * Null when the document does not exist.
 */
internal suspend fun DocumentReference.getPreferCache(): DocumentSnapshot? {
    val cached = runCatching { get(Source.CACHE).await() }.getOrNull()
    val snapshot = if (cached != null && cached.exists()) cached else get().await()
    return snapshot.takeIf { it.exists() }
}

internal fun entryNotFound() = RepositoryException("This entry no longer exists. It may have been deleted.")

/** Readable messages for a failed save. [what] is "income", "income source" or "expense". */
internal fun Throwable.toSaveError(what: String): RepositoryException = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        RepositoryException("The server didn't accept this $what. Check the details and try again.", this)
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        RepositoryException("You're signed out. Sign in and try again.", this)
    else -> toRepositoryException() // network, server unavailable, or anything else
}
