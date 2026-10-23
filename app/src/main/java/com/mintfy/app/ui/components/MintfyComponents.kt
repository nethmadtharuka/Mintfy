package com.mintfy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.theme.MintfyTheme

// Shared components from the Figma "Design System" frame. Colours, type and radii come from MintfyTheme.

/** Figma "Top bar": 22 Bold title, 16dp padding, below the status bar. */
@Composable
fun MintfyTopBar(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
    )
}

/** Figma "Input": surface fill, 12dp radius, 1dp border that turns 2dp green when focused, label inside. */
@Composable
fun MintfyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val borderColor = when {
        error != null -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }

    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.titleSmall,
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            shape = shape,
            visualTransformation = visualTransformation,
            trailingIcon = trailingContent,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
            interactionSource = interactionSource,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                disabledContainerColor = colors.surfaceContainer,
                errorContainerColor = colors.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.onSurfaceVariant,
                errorLabelColor = colors.error,
                cursorColor = colors.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(if (focused) 2.dp else 1.dp, borderColor, shape),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun MintfyPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    MintfyTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        error = error,
        enabled = enabled,
        keyboardType = KeyboardType.Password,
        imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        onImeAction = onDone,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingContent = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide" else "Show", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

private val PillPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

/** Figma "Button/Primary": green pill, black 16 SemiBold label. Stays green while loading. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, isLoading: Boolean = false) {
    Button(
        onClick = { if (!isLoading) onClick() },
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PillPadding,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Figma "Button/Outlined": 1.5dp green border, green 16 SemiBold label. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.5.dp, if (enabled) primary else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = primary),
        contentPadding = PillPadding,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Figma "Error": card fill, 1dp red border, red 13 Medium text. Announced by screen readers. */
@Composable
fun ErrorBanner(message: String?) {
    if (message == null) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp))
    }
}

/** Figma "Card": card fill, 1dp border, 16dp radius and padding. */
@Composable
fun MintfyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Preview
@Composable
fun MintfyComponentsPreview() {
    MintfyTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MintfyTextField(value = "kavindu@example.com", onValueChange = {}, label = "Email")
                MintfyPasswordField(value = "secret", onValueChange = {}, label = "Password")
                ErrorBanner("Email or password is incorrect.")
                PrimaryButton("Sign in", onClick = {})
                PrimaryButton("Sign in", onClick = {}, isLoading = true)
                SecondaryButton("Create account", onClick = {})
                MintfyCard {
                    Text("Received income", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("LKR 140,500", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
