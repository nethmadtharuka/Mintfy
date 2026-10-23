package com.mintfy.app.ui.screens.auth

// Plain Kotlin so it runs in JVM unit tests (android.util.Patterns does not).
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

const val MIN_PASSWORD_LENGTH = 6 // Firebase Auth's minimum
const val MAX_DISPLAY_NAME_LENGTH = 60 // firestore.rules: displayName

fun emailError(email: String): String? = when {
    email.isBlank() -> "Enter your email."
    !EMAIL_PATTERN.matches(email.trim()) -> "Enter a valid email address."
    else -> null
}

fun signInPasswordError(password: String): String? =
    if (password.isEmpty()) "Enter your password." else null

fun newPasswordError(password: String): String? = when {
    password.isEmpty() -> "Choose a password."
    password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
    else -> null
}

fun confirmPasswordError(password: String, confirm: String): String? =
    if (password != confirm) "Passwords don't match." else null

fun displayNameError(name: String): String? =
    if (name.trim().length > MAX_DISPLAY_NAME_LENGTH) "Use at most $MAX_DISPLAY_NAME_LENGTH characters." else null
