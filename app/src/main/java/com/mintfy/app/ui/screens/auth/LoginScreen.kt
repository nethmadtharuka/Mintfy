package com.mintfy.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.mintfy.app.ui.theme.MintfyTheme

@Composable
fun LoginScreen(viewModel: AuthViewModel, onRegisterClick: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LoginContent(form = form, onSignIn = viewModel::signIn, onRegisterClick = onRegisterClick)
}

@Composable
fun LoginContent(
    form: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // not saved to the instance state bundle

    Scaffold { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in to see your money clearly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                error = form.emailError,
                enabled = !form.isLoading,
                keyboardType = KeyboardType.Email,
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                error = form.passwordError,
                enabled = !form.isLoading,
                onDone = { onSignIn(email, password) },
            )
            FormError(form.error)
            SubmitButton("Sign in", form.isLoading) { onSignIn(email, password) }
            TextButton(onClick = onRegisterClick, enabled = !form.isLoading) {
                Text("New to Mintfy? Create an account")
            }
        }
    }
}

@Preview(showBackground = true)
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
