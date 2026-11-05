package com.mintfy.app.ui.screens.categories

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.ui.components.ErrorBanner
import com.mintfy.app.ui.components.MintfyCard
import com.mintfy.app.ui.components.MintfyTextField
import com.mintfy.app.ui.components.MintfyTopBar
import com.mintfy.app.ui.components.PrimaryButton
import com.mintfy.app.ui.components.SecondaryButton
import com.mintfy.app.ui.components.SectionLabel
import com.mintfy.app.ui.components.SelectChip
import com.mintfy.app.ui.screens.dashboard.LoadingCard
import com.mintfy.app.ui.screens.expense.offlineMessage
import com.mintfy.app.ui.theme.MintfyTheme

/** Everything the user can do on the screen, so the content stays stateless and previewable. */
data class CategoriesActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpen: (id: String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onTypeChange: (SpendingType) -> Unit = {},
    val onSave: () -> Unit = {},
    val onArchive: () -> Unit = {},
    val onClose: () -> Unit = {},
)

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.savedOffline) {
        if (!state.savedOffline) return@LaunchedEffect
        Toast.makeText(context, offlineMessage(deleted = false), Toast.LENGTH_LONG).show()
        viewModel.onOfflineMessageShown()
    }

    CategoriesContent(
        state = state,
        actions = CategoriesActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onAdd = viewModel::openNewCategory,
            onOpen = viewModel::openCategory,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onSave = viewModel::save,
            onArchive = viewModel::archive,
            onClose = viewModel::closeEditor,
        ),
    )
}

/** Figma "08 Categories". Stateless: it only draws [state] and reports what the user does. */
@Composable
fun CategoriesContent(state: CategoriesUiState, actions: CategoriesActions) {
    Scaffold(topBar = { MintfyTopBar("Categories", onBack = actions.onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.error != null) {
                ErrorBanner(state.error)
                SecondaryButton("Try again", onClick = actions.onRetry)
            }
            if (state.isLoading) {
                LoadingCard()
            } else {
                CategoryGroup("Committed", state.groups.committed, actions.onOpen)
                CategoryGroup("Discretionary", state.groups.discretionary, actions.onOpen)
                SectionLabel(
                    "Tap a category to rename it or change its type. Your dashboard follows the change straight away."
                )
            }
            PrimaryButton("Add category", onClick = actions.onAdd)
        }
    }
    state.editor?.let { CategoryDialog(it, actions) }
}

@Composable
private fun CategoryGroup(title: String, categories: List<Category>, onOpen: (String) -> Unit) {
    SectionLabel(title)
    MintfyCard {
        if (categories.isEmpty()) {
            Text(
                "No ${title.lowercase()} categories yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        categories.forEach { CategoryRow(it, onClick = { onOpen(it.id) }) }
    }
}

/** Figma row: name on the left, the spending type on the right (green when discretionary). */
@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    val isDiscretionary = category.spendingType == SpendingType.DISCRETIONARY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp) // a comfortable touch target while keeping the Figma spacing
            .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick),
    ) {
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            category.spendingType.displayName(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isDiscretionary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryDialog(editor: CategoryEditorState, actions: CategoriesActions) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = {
            Text(if (editor.isNew) "Add category" else "Edit category", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MintfyTextField(
                    value = editor.name,
                    onValueChange = actions.onNameChange,
                    label = "Name",
                    error = editor.nameError,
                    enabled = !editor.isSaving,
                    imeAction = ImeAction.Done,
                    onImeAction = actions.onSave,
                )
                SectionLabel("Spending type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpendingType.entries.forEach { type ->
                        SelectChip(
                            label = type.displayName(),
                            selected = editor.spendingType == type,
                            onClick = { if (!editor.isSaving) actions.onTypeChange(type) },
                        )
                    }
                }
                SectionLabel(
                    if (editor.spendingType == SpendingType.COMMITTED) {
                        "Committed: a fixed cost you have to pay, such as rent."
                    } else {
                        "Discretionary: spending you could choose to cut, such as eating out."
                    }
                )
                ErrorBanner(editor.error)
                if (!editor.isNew) {
                    SectionLabel("Archiving hides this category when adding expenses. Expenses already recorded keep it.")
                    TextButton(
                        onClick = actions.onArchive,
                        enabled = !editor.isSaving,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "Archive category",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSave, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (editor.isNew) "Add" else "Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onClose, enabled = !editor.isSaving) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

// ---------- Previews (Figma sample data) ----------

private val previewCategories = listOf(
    Category("cat_rent", "Rent", SpendingType.COMMITTED, builtIn = true),
    Category("cat_utilities", "Utilities", SpendingType.COMMITTED, builtIn = true),
    Category("c_gym", "Gym", SpendingType.COMMITTED),
    Category("cat_subscriptions", "Subscriptions", SpendingType.COMMITTED, builtIn = true),
    Category("cat_food", "Food & Coffee", SpendingType.DISCRETIONARY, builtIn = true),
    Category("cat_transport", "Transport", SpendingType.DISCRETIONARY, builtIn = true),
    Category("cat_entertainment", "Entertainment", SpendingType.DISCRETIONARY, builtIn = true),
    Category("cat_shopping", "Shopping", SpendingType.DISCRETIONARY, builtIn = true),
)

@Preview
@Composable
fun CategoriesContentPreview() {
    MintfyTheme {
        CategoriesContent(
            state = CategoriesUiState(groups = groupCategories(previewCategories), isLoading = false),
            actions = CategoriesActions(),
        )
    }
}

@Preview
@Composable
fun CategoryDialogPreview() {
    MintfyTheme {
        CategoriesContent(
            state = CategoriesUiState(
                groups = groupCategories(previewCategories),
                isLoading = false,
                editor = CategoryEditorState("cat_subscriptions", "Subscriptions", SpendingType.COMMITTED),
            ),
            actions = CategoriesActions(),
        )
    }
}
