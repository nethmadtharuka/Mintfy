package com.mintfy.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.DefaultReferenceData
import com.mintfy.app.data.model.SpendingType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /**
     * Emits the signed-in user's categories that are not archived (schema query Q10), committed first.
     * Updates in real time, including offline writes. The listener is removed when collection ends.
     */
    fun observeCategories(): Flow<List<Category>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val registration = categories(uid)
            .whereEqualTo(ARCHIVED, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { it.toCategory() }
                trySend(list.sortedWith(displayOrder))
            }
        awaitClose { registration.remove() }
    }

    /**
     * Creates any default category the user does not have yet (FR-D1). Safe to call every time:
     * existing documents are never rewritten, because the rules forbid changing createdAt.
     */
    suspend fun ensureDefaults(): Result<Unit> = attempt(::toSeedError) {
        val uid = auth.currentUser?.uid ?: throw signedOut()
        val existingIds = categories(uid).get().await().documents.map { it.id }.toSet()
        val missing = DefaultReferenceData.categories.filter { it.id !in existingIds }
        if (missing.isEmpty()) return@attempt

        val batch = firestore.batch()
        missing.forEach { batch.set(categories(uid).document(it.id), it.toNewDocument()) }
        batch.commit().await()
    }

    private fun categories(uid: String) =
        firestore.collection("users").document(uid).collection("categories")

    private fun Category.toNewDocument() = buildMap {
        put(NAME, name)
        put(SPENDING_TYPE, spendingType.name)
        if (icon != null) put(ICON, icon)
        put(BUILT_IN, builtIn)
        put(ARCHIVED, archived)
        put(CREATED_AT, FieldValue.serverTimestamp())
        put(UPDATED_AT, FieldValue.serverTimestamp())
    }

    /** Null for a document that does not match the schema, so one bad document cannot break the list. */
    private fun DocumentSnapshot.toCategory(): Category? {
        val type = getString(SPENDING_TYPE)
            ?.let { runCatching { SpendingType.valueOf(it) }.getOrNull() }
            ?: return null
        return Category(
            id = id,
            name = getString(NAME) ?: return null,
            spendingType = type,
            icon = getString(ICON),
            builtIn = getBoolean(BUILT_IN) ?: false,
            archived = getBoolean(ARCHIVED) ?: false,
        )
    }

    private fun toSeedError(e: Throwable): RepositoryException =
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            RepositoryException("Your categories couldn't be set up. Please try again.", e)
        } else {
            e.toRepositoryException()
        }

    private companion object {
        // Field names from firestore.rules (users/{uid}/categories).
        const val NAME = "name"
        const val SPENDING_TYPE = "spendingType"
        const val ICON = "icon"
        const val BUILT_IN = "builtIn"
        const val ARCHIVED = "archived"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"

        private val defaultIds = DefaultReferenceData.categories.map { it.id }

        // Committed first, then the defaults in their usual order, then the user's own by name.
        val displayOrder = compareBy<Category>(
            { it.spendingType },
            { defaultIds.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        )
    }
}
