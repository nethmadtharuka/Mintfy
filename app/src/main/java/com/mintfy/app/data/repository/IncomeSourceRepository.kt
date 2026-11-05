package com.mintfy.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mintfy.app.data.local.CacheDao
import com.mintfy.app.data.local.toCached
import com.mintfy.app.data.local.toModel
import com.mintfy.app.data.model.IncomeSource
import com.mintfy.app.data.model.IncomeSourceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Income sources (FR-B1). There are no defaults: the user creates their own. A source's type is set
 * when it is created and there is no way to change it, and there is no delete (the rules forbid both):
 * sources are archived instead, and archived ones are hidden here.
 */
@Singleton
class IncomeSourceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cache: CacheDao,
) {

    /** Emits the signed-in user's sources that are not archived, in real time, including offline writes. */
    fun observeSources(): Flow<List<IncomeSource>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val cacheFirst = CacheFirst(this, "income sources") { list ->
            cache.replaceIncomeSources(uid, list.mapIndexed { i, item -> item.toCached(uid, i) })
        }
        cacheFirst.startFrom { cache.incomeSources(uid).mapNotNull { it.toModel() } }
        val registration = sources(uid)
            .whereEqualTo(ARCHIVED, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { it.toIncomeSource() }
                cacheFirst.onLive(list.sortedWith(displayOrder))
            }
        awaitClose { registration.remove() }
    }

    /** Creates a source and returns its ID. Inputs must already be validated (see IncomeValidation). */
    suspend fun addSource(
        name: String,
        type: IncomeSourceType,
        defaultCurrency: String,
        expectedAmountCents: Long?,
    ): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = buildMap {
            put(NAME, name.trim())
            put(TYPE, type.name)
            put(DEFAULT_CURRENCY, defaultCurrency)
            put(ARCHIVED, false)
            expectedAmountCents?.let { put(EXPECTED_AMOUNT_CENTS, it) }
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        return sources(uid).document().createAndConfirm(data) { it.toSaveError("income source") }
    }

    /**
     * Renames a source and sets or clears its expected salary (FR-B1, FR-B3). Inputs must already be
     * validated. The type and currency are not touched; the rules keep the type fixed for good.
     */
    suspend fun updateSource(id: String, name: String, expectedAmountCents: Long?): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = mapOf<String, Any>(
            NAME to name.trim(),
            EXPECTED_AMOUNT_CENTS to (expectedAmountCents ?: FieldValue.delete()),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        return sources(uid).document(id).confirmWrite({ it.toSaveError("income source") }) { update(data) }
    }

    /**
     * Hides a source from the add-income form. Sources are archived, never deleted (schema D-07),
     * so income already recorded against it keeps its name in history and totals.
     */
    suspend fun archiveSource(id: String): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = mapOf<String, Any>(ARCHIVED to true, UPDATED_AT to FieldValue.serverTimestamp())
        return sources(uid).document(id).confirmWrite({ it.toSaveError("income source") }) { update(data) }
    }

    private fun sources(uid: String) =
        firestore.collection("users").document(uid).collection("incomeSources")

    /** Null for a document that does not match the schema, so one bad document cannot break the list. */
    private fun DocumentSnapshot.toIncomeSource(): IncomeSource? {
        val type = getString(TYPE)
            ?.let { runCatching { IncomeSourceType.valueOf(it) }.getOrNull() }
            ?: return null
        return IncomeSource(
            id = id,
            name = getString(NAME) ?: return null,
            type = type,
            defaultCurrency = getString(DEFAULT_CURRENCY) ?: return null,
            expectedAmountCents = getLong(EXPECTED_AMOUNT_CENTS),
            archived = getBoolean(ARCHIVED) ?: false,
        )
    }

    private companion object {
        // Field names from firestore.rules (users/{uid}/incomeSources).
        const val NAME = "name"
        const val TYPE = "type"
        const val DEFAULT_CURRENCY = "defaultCurrency"
        const val EXPECTED_AMOUNT_CENTS = "expectedAmountCents"
        const val ARCHIVED = "archived"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"

        // Grouped by type in the order of the enum (salary first), then by name.
        val displayOrder = compareBy<IncomeSource>({ it.type }, { it.name.lowercase() })
    }
}
