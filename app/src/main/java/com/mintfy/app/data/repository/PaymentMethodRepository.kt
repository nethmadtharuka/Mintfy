package com.mintfy.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.mintfy.app.data.model.DefaultReferenceData
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.PaymentMethodType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentMethodRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /** Emits the signed-in user's payment methods that are not archived, defaults first, in real time. */
    fun observePaymentMethods(): Flow<List<PaymentMethod>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }
        val registration = paymentMethods(uid)
            .whereEqualTo(ARCHIVED, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().mapNotNull { it.toPaymentMethod() }
                trySend(list.sortedWith(displayOrder))
            }
        awaitClose { registration.remove() }
    }

    /** Creates Cash, Bank card and Secondary account if missing (FR-C2). Never rewrites existing ones. */
    suspend fun ensureDefaults(): Result<Unit> = attempt(::toSeedError) {
        val uid = auth.currentUser?.uid ?: throw signedOut()
        val existingIds = paymentMethods(uid).get().await().documents.map { it.id }.toSet()
        val missing = DefaultReferenceData.paymentMethods.filter { it.id !in existingIds }
        if (missing.isEmpty()) return@attempt

        val batch = firestore.batch()
        missing.forEach { batch.set(paymentMethods(uid).document(it.id), it.toNewDocument()) }
        batch.commit().await()
    }

    private fun paymentMethods(uid: String) =
        firestore.collection("users").document(uid).collection("paymentMethods")

    // The rules allow exactly these five fields for a payment method.
    private fun PaymentMethod.toNewDocument() = mapOf(
        NAME to name,
        TYPE to type.name,
        ARCHIVED to archived,
        CREATED_AT to FieldValue.serverTimestamp(),
        UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toPaymentMethod(): PaymentMethod? {
        val type = getString(TYPE)
            ?.let { runCatching { PaymentMethodType.valueOf(it) }.getOrNull() }
            ?: return null
        return PaymentMethod(
            id = id,
            name = getString(NAME) ?: return null,
            type = type,
            archived = getBoolean(ARCHIVED) ?: false,
        )
    }

    private fun toSeedError(e: Throwable): RepositoryException =
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            RepositoryException("Your payment methods couldn't be set up. Please try again.", e)
        } else {
            e.toRepositoryException()
        }

    private companion object {
        // Field names from firestore.rules (users/{uid}/paymentMethods).
        const val NAME = "name"
        const val TYPE = "type"
        const val ARCHIVED = "archived"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"

        private val defaultIds = DefaultReferenceData.paymentMethods.map { it.id }

        val displayOrder = compareBy<PaymentMethod>(
            { defaultIds.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        )
    }
}
