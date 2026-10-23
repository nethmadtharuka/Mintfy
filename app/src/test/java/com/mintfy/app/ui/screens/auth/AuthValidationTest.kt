package com.mintfy.app.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun email() {
        assertNull(emailError(" kavindu@example.com "))
        assertEquals("Enter your email.", emailError("  "))
        assertEquals("Enter a valid email address.", emailError("kavindu@example"))
        assertEquals("Enter a valid email address.", emailError("kavindu example.com"))
    }

    @Test
    fun passwords() {
        assertNotNull(signInPasswordError(""))
        assertNull(signInPasswordError("x"))
        assertNotNull(newPasswordError("12345"))
        assertNull(newPasswordError("123456"))
        assertNotNull(confirmPasswordError("123456", "123457"))
        assertNull(confirmPasswordError("123456", "123456"))
    }

    @Test
    fun displayName() {
        assertNull(displayNameError(""))
        assertNull(displayNameError("a".repeat(60)))
        assertNotNull(displayNameError("a".repeat(61)))
    }
}
