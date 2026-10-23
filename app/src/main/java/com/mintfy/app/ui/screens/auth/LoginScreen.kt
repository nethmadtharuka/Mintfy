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
fun LoginScreen(viewModel: AuthViewModel, onRegisterClick: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LoginContent(form = form, onSignIn = viewModel::signIn, onRegisterClick = onRegisterClick)
}

/** Figma "01 Sign in / Register". */
@Composable
fun LoginContent(
    form: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // not saved to the instance state bundle

    Scaffold(topBar = { MintfyTopBar("Welcome to Mintfy") }) { innerPadding ->
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
                "Sign in to track income, expenses and your savings goal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                onDone = { onSignIn(email, password) },
            )
            ErrorBanner(form.error)
            PrimaryButton("Sign in", onClick = { onSignIn(email, password) }, isLoading = form.isLoading)
            SecondaryButton("Create account", onClick = onRegisterClick, enabled = !form.isLoading)
        }
    }
}

@Preview
@Composable
fun LoginContentPreview() {
    MintfyTheme {
        LoginContent(
            form = AuthFormState(error = "Email or password is incorrect."),
            onSignIn = { _, _ -> },
            onRegisterClick = {},
        )
    }
}
