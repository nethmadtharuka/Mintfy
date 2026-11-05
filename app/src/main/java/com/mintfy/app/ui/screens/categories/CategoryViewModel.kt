package com.mintfy.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.SaveOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The add / edit dialog. [categoryId] is null when adding a new category. */
data class CategoryEditorState(
    val categoryId: String? = null,
    val name: String = "",
    val spendingType: SpendingType = SpendingType.DISCRETIONARY,
    val nameError: String? = null,
    val error: String? = null, // the save failed; the dialog stays open
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = categoryId == null
}

/** Everything the Categories screen shows. The screen only reads this; it never changes it directly. */
data class CategoriesUiState(
    val groups: CategoryGroups = CategoryGroups(emptyList(), emptyList()),
    val isLoading: Boolean = true,
    val error: String? = null, // loading failed; offer a retry
    val editor: CategoryEditorState? = null, // null when the dialog is closed
    val savedOffline: Boolean = false, // the last change is on this phone, waiting to sync
)

private sealed interface CategoryData {
    data object Loading : CategoryData
    data class Failed(val message: String) : CategoryData
    data class Loaded(val categories: List<Category>) : CategoryData
}

private data class ListState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

private data class Dialogs(val editor: CategoryEditorState? = null, val savedOffline: Boolean = false)

/**
 * Figma "08 Categories" (FR-D2, FR-D4): the user's categories grouped by spending type, with add,
 * rename, change type and archive. A live listener, so the dashboard donut and this list both follow
 * a change at once (demo step: change a category's type and show the donut change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listener
    private val dialogs = MutableStateFlow(Dialogs())

    // Keep the last list visible while restarting or after an error (FR-H2).
    private val list: Flow<ListState> = attempt
        .flatMapLatest { load() }
        .scan(ListState()) { previous, d ->
            when (d) {
                CategoryData.Loading -> previous.copy(isLoading = previous.categories.isEmpty(), error = null)
                is CategoryData.Failed -> previous.copy(isLoading = false, error = d.message)
                is CategoryData.Loaded -> ListState(d.categories, isLoading = false)
            }
        }

    val uiState: StateFlow<CategoriesUiState> = combine(list, dialogs) { l, dlg ->
        CategoriesUiState(
            groups = groupCategories(l.categories),
            isLoading = l.isLoading,
            error = l.error,
            editor = dlg.editor,
            savedOffline = dlg.savedOffline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private var categories: List<Category> = emptyList() // latest list, for the duplicate-name check

    fun retry() = attempt.update { it + 1 }

    fun openNewCategory() = dialogs.update { it.copy(editor = CategoryEditorState()) }

    fun openCategory(id: String) {
        val category = categories.firstOrNull { it.id == id } ?: return
        dialogs.update {
            it.copy(editor = CategoryEditorState(category.id, category.name, category.spendingType))
        }
    }

    fun onNameChange(text: String) = updateEditor { it.copy(name = text, nameError = null, error = null) }

    fun onTypeChange(type: SpendingType) = updateEditor { it.copy(spendingType = type, error = null) }

    fun closeEditor() = dialogs.update { it.copy(editor = null) }

    fun save() {
        val editor = dialogs.value.editor ?: return
        if (editor.isSaving) return
        val nameError = categoryNameError(editor.name, categories, editor.categoryId)
        if (nameError != null) {
            updateEditor { it.copy(nameError = nameError) }
            return
        }
        updateEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val id = editor.categoryId
            val result = if (id == null) {
                repository.addCategory(editor.name, editor.spendingType)
            } else {
                repository.updateCategory(id, editor.name, editor.spendingType)
            }
            result
                .onSuccess(::closeAfterSave)
                .onFailure { e -> updateEditor { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    /** Hidden from the add-expense form; expenses already recorded keep their category. */
    fun archive() {
        val editor = dialogs.value.editor ?: return
        val id = editor.categoryId ?: return
        if (editor.isSaving) return
        updateEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.archiveCategory(id)
                .onSuccess(::closeAfterSave)
                .onFailure { e -> updateEditor { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    fun onOfflineMessageShown() = dialogs.update { it.copy(savedOffline = false) }

    private fun closeAfterSave(outcome: SaveOutcome) =
        dialogs.update { it.copy(editor = null, savedOffline = outcome.pendingSync) }

    private fun updateEditor(change: (CategoryEditorState) -> CategoryEditorState) =
        dialogs.update { d -> d.editor?.let { d.copy(editor = change(it)) } ?: d }

    private fun load(): Flow<CategoryData> = repository.observeCategories()
        .map { list ->
            categories = list
            CategoryData.Loaded(list) as CategoryData
        }
        .onStart { emit(CategoryData.Loading) }
        .catch { e -> emit(CategoryData.Failed(e.message ?: "Your categories couldn't be loaded.")) }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
