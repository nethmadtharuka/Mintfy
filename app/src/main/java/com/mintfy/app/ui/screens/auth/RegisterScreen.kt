package com.mintfy.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyPasswordField
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.theme.MintfyTheme

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onSignInClick: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    RegisterContent(form = form, onRegister = viewModel::register, onSignInClick = onSignInClick)
}

/** Not drawn separately in Figma; built from the same "01 Sign in / Register" components. */
@Composable
fun RegisterContent(
    form: AuthFormState,
    onRegister: (displayName: String, email: String, password: String, confirmPassword: String) -> Unit,
    onSignInClick: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val submit = { onRegister(displayName, email, password, confirmPassword) }

    Scaffold(topBar = { MintfyTopBar("Create your account") }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Track every income source and see your savings goal move.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MintfyTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Name (optional)",
                error = form.displayNameError,
                enabled = !form.isLoading,
            )
            MintfyTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                error = form.emailError,
                enabled = !form.isLoading,
                keyboardType = KeyboardType.Email,
            )
            MintfyPasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                error = form.passwordError,
                enabled = !form.isLoading,
            )
            MintfyPasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm password",
                error = form.confirmPasswordError,
                enabled = !form.isLoading,
                onDone = submit,
            )
            ErrorBanner(form.error)
            PrimaryButton("Create account", onClick = submit, isLoading = form.isLoading)
            SecondaryButton("Back to sign in", onClick = onSignInClick, enabled = !form.isLoading)
        }
    }
}

@Preview
@Composable
fun RegisterContentPreview() {
    MintfyTheme {
        RegisterContent(
            form = AuthFormState(passwordError = "Use at least 6 characters."),
            onRegister = { _, _, _, _ -> },
            onSignInClick = {},
        )
    }
}
