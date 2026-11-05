package com.mintfy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mintfy.app.ui.theme.MintfyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Shared components from the Figma "Design System" frame. Colours, type and radii come from MintfyTheme.

/**
 * Figma "Top bar": 22 Bold title, 16dp padding, below the status bar.
 * With [onBack], shows the green "‹" back arrow used on the Add income and Add expense frames.
 * [actions] go on the right, for example the dashboard's "⋮" menu.
 */
@Composable
fun MintfyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
    ) {
        if (onBack != null) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .semantics { contentDescription = "Back" },
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
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

/** Figma section label: 12 Regular, secondary text. */
@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Chips that wrap onto new lines, exactly one selected. Shows a spinner while the options load. */
@Composable
fun ChipGroup(
    isLoading: Boolean,
    emptyMessage: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    when {
        isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        options.isEmpty() && trailing == null -> Text(
            emptyMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (id, name) ->
                SelectChip(label = name, selected = id == selectedId, onClick = { onSelect(id) })
            }
            trailing?.invoke()
        }
    }
}

/**
 * Figma "Chip": 8dp radius, 14 x 8 padding, 13 Medium label.
 * Selected is green with black text; unselected is the card colour with a 1dp border.
 */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(14.dp, 8.dp))
    }
}

/** Material 3 confirmation before an entry is deleted (FR-H5). Delete is red so it is never tapped by habit. */
@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this $what?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("It will be removed from your history and totals. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
        },
    )
}

/** Looks like the Figma "Input" but opens a picker instead of the keyboard. */
@Composable
fun PickerField(label: String, value: String, error: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, if (error != null) colors.error else colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

/**
 * Material 3 date picker. By default only today or earlier can be picked;
 * [allowFuture] is for dates that have not happened yet, such as when an invoice should be paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MintfyDatePicker(
    selected: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    allowFuture: Boolean = false,
    title: String? = null, // replaces the default "Select date" when the date means something specific
) {
    // The Material date picker works in UTC milliseconds, so convert at the edges only.
    val todayMillis = LocalDate.now().toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = allowFuture || year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        if (title == null) {
            DatePicker(state = pickerState)
        } else {
            DatePicker(
                state = pickerState,
                title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            )
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "Today · 20 Oct 2026", "Yesterday · 19 Oct 2026", or "Mon · 13 Oct 2026". */
fun dateLabel(date: LocalDate, today: LocalDate): String {
    val prefix = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }
    return "$prefix · ${date.format(dateFormat)}"
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

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
