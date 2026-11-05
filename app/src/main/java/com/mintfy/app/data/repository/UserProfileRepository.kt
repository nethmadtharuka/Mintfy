package com.mintfy.app.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.mintfy.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** An error whose message is safe to show to the user. The cause is kept for logging only. */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class UserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /** Emits the profile, or null while it does not exist. The listener is removed when collection ends. */
    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = profileDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toRepositoryException())
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toUserProfile())
        }
        awaitClose { registration.remove() }
    }

    /** The signed-in user's profile, live; null while signed out or before the profile exists. */
    fun observeCurrentProfile(): Flow<UserProfile?> {
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return observeProfile(uid)
    }

    /**
     * Creates the signed-in user's profile if it does not exist yet. An existing profile is never
     * rewritten, so its createdAt is kept (the rules would reject a changed createdAt anyway).
     */
    suspend fun ensureProfile(displayName: String? = null): Result<Unit> = attempt {
        val user = auth.currentUser ?: throw signedOut()
        val email = user.email ?: throw RepositoryException("Your account has no email address.")
        val name = checkDisplayName(displayName)
        val doc = profileDoc(user.uid)
        if (doc.get(Source.SERVER).await().exists()) return@attempt

        val data = buildMap {
            put(EMAIL, email)
            put(BASE_CURRENCY, "LKR")
            if (name != null) put(DISPLAY_NAME, name)
            put(CREATED_AT, FieldValue.serverTimestamp())
            put(UPDATED_AT, FieldValue.serverTimestamp())
        }
        doc.set(data).await()
    }

    /** Replaces the editable fields. A null value removes that field. createdAt is left untouched. */
    suspend fun updateProfile(
        displayName: String?,
        estimateMinCents: Long?,
        estimateMaxCents: Long?,
    ): Result<Unit> = attempt {
        val user = auth.currentUser ?: throw signedOut()
        val name = checkDisplayName(displayName)
        if ((estimateMinCents ?: 0) < 0 || (estimateMaxCents ?: 0) < 0) {
            throw RepositoryException("Income estimates can't be negative.")
        }
        if (estimateMinCents != null && estimateMaxCents != null && estimateMinCents > estimateMaxCents) {
            throw RepositoryException("The minimum estimate can't be more than the maximum.")
        }

        profileDoc(user.uid).update(
            mapOf(
                DISPLAY_NAME to (name ?: FieldValue.delete()),
                ESTIMATE_MIN_CENTS to (estimateMinCents ?: FieldValue.delete()),
                ESTIMATE_MAX_CENTS to (estimateMaxCents ?: FieldValue.delete()),
                UPDATED_AT to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /**
     * Sets or clears the monthly income estimate (FR-B8); both null removes it. Inputs must already be
     * validated (min ≤ max, as the rules require). Only these two fields and updatedAt change, and the
     * write is offline-safe: it waits briefly for the server, then reports it as waiting to sync.
     */
    suspend fun updateEstimate(minCents: Long?, maxCents: Long?): Result<SaveOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val data = mapOf<String, Any>(
            ESTIMATE_MIN_CENTS to (minCents ?: FieldValue.delete()),
            ESTIMATE_MAX_CENTS to (maxCents ?: FieldValue.delete()),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        return profileDoc(uid).confirmWrite({ it.toSaveError("income estimate") }) { update(data) }
    }

    private fun profileDoc(uid: String) = firestore.collection("users").document(uid)

    private fun DocumentSnapshot.toUserProfile() = UserProfile(
        uid = id,
        email = getString(EMAIL).orEmpty(),
        displayName = getString(DISPLAY_NAME),
        baseCurrency = getString(BASE_CURRENCY) ?: "LKR",
        estimateMinCents = getLong(ESTIMATE_MIN_CENTS),
        estimateMaxCents = getLong(ESTIMATE_MAX_CENTS),
        createdAt = getTimestamp(CREATED_AT),
        updatedAt = getTimestamp(UPDATED_AT),
    )

    private companion object {
        // Field names from firestore.rules (users/{uid}).
        const val EMAIL = "email"
        const val DISPLAY_NAME = "displayName"
        const val BASE_CURRENCY = "baseCurrency"
        const val ESTIMATE_MIN_CENTS = "estimateMinCents"
        const val ESTIMATE_MAX_CENTS = "estimateMaxCents"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}

/** Trims the name; blank becomes null. Rejects names the rules would reject (over 60 characters). */
internal fun checkDisplayName(displayName: String?): String? {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
    if (name != null && name.length > 60) {
        throw RepositoryException("Your name can be at most 60 characters.")
    }
    return name
}

internal fun signedOut() = RepositoryException("You're signed out. Sign in and try again.")

internal fun Throwable.toRepositoryException(): RepositoryException = when (this) {
    is RepositoryException -> this
    is FirebaseNetworkException ->
        RepositoryException("No internet connection. Check your connection and try again.", this)
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            RepositoryException("Mintfy can't reach the server right now. Try again in a moment.", this)
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            RepositoryException("Your profile couldn't be saved. Check the details and try again.", this)
        else -> RepositoryException("Something went wrong. Please try again.", this)
    }
    else -> RepositoryException("Something went wrong. Please try again.", this)
}

/** Runs [block] and wraps failures in a user-friendly [RepositoryException]. Cancellation is not caught. */
internal suspend fun <T> attempt(
    toError: (Throwable) -> RepositoryException = { it.toRepositoryException() },
    block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(toError(e))
}
