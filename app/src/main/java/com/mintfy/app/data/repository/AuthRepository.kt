package com.mintfy.app.data.repository

import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val profileRepository: UserProfileRepository,
) {

    /** Emits the signed-in user, or null when signed out. The listener is removed when collection ends. */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Signs in, then creates the profile if an earlier registration did not get that far. */
    suspend fun signIn(email: String, password: String): Result<Unit> = attempt(::toAuthError) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        profileRepository.ensureProfile().getOrThrow()
    }

    suspend fun register(email: String, password: String, displayName: String?): Result<Unit> =
        attempt(::toAuthError) {
            // Check the name first so an invalid one doesn't leave an account without a profile.
            val name = checkDisplayName(displayName)
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
            profileRepository.ensureProfile(name).getOrThrow()
        }

    fun signOut() = auth.signOut()

    // Email enumeration protection is on, so a wrong password and an unknown email look the same.
    private fun toAuthError(e: Throwable): RepositoryException = when {
        e is FirebaseAuthWeakPasswordException ->
            RepositoryException("Choose a stronger password (at least 6 characters).", e)
        e is FirebaseAuthUserCollisionException ->
            RepositoryException("An account with this email already exists.", e)
        e is FirebaseAuthException && e.errorCode == "ERROR_INVALID_EMAIL" ->
            RepositoryException("Enter a valid email address.", e)
        e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException ->
            RepositoryException("Email or password is incorrect.", e)
        e is FirebaseTooManyRequestsException ->
            RepositoryException("Too many attempts. Wait a few minutes and try again.", e)
        else -> e.toRepositoryException()
    }
}
