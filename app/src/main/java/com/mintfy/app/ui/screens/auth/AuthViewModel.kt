package com.mintfy.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val email: String?) : Session
}

data class AuthFormState(
    val isLoading: Boolean = false,
    val displayNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val error: String? = null,
) {
    val hasFieldErrors: Boolean
        get() = listOf(displayNameError, emailError, passwordError, confirmPasswordError).any { it != null }
}

/**
 * Scoped to the activity, not a screen: registering signs the user in (and navigates away)
 * before the profile is written, so a screen-scoped ViewModel would cancel that write.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session> = authRepository.authState
        .map { user -> if (user == null) Session.SignedOut else Session.SignedIn(user.email) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun signIn(email: String, password: String) = submit(
        AuthFormState(emailError = emailError(email), passwordError = signInPasswordError(password))
    ) { authRepository.signIn(email, password) }

    fun register(displayName: String, email: String, password: String, confirmPassword: String) = submit(
        AuthFormState(
            displayNameError = displayNameError(displayName),
            emailError = emailError(email),
            passwordError = newPasswordError(password),
            confirmPasswordError = confirmPasswordError(password, confirmPassword),
        )
    ) { authRepository.register(email, password, displayName) }

    fun signOut() {
        authRepository.signOut()
        _form.value = AuthFormState()
    }

    /** Clears old errors when switching between the sign-in and register screens. */
    fun clearErrors() {
        if (!_form.value.isLoading) _form.value = AuthFormState()
    }

    private fun submit(validation: AuthFormState, action: suspend () -> Result<Unit>) {
        if (_form.value.isLoading) return
        if (validation.hasFieldErrors) {
            _form.value = validation
            return
        }
        _form.value = AuthFormState(isLoading = true)
        viewModelScope.launch {
            _form.value = AuthFormState(error = action().exceptionOrNull()?.message)
        }
    }
}
