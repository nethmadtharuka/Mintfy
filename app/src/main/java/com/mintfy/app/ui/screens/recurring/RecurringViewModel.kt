package com.mintfy.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.RecurringFrequency
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.PaymentMethodRepository
import com.mintfy.app.data.repository.RecurringRepository
import com.mintfy.app.data.repository.SaveOutcome
import com.mintfy.app.ui.screens.income.formatCents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The add / edit form, shown as a dialog. [id] is null when adding. */
data class RecurringEditorState(
    val id: String? = null,
    val name: String = "",
    val amount: String = "",
    val categoryId: String? = null,
    val paymentMethodId: String? = null,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val nextDueDate: LocalDate = LocalDate.now(),
    val active: Boolean = true,
    val errors: RecurringFieldErrors = RecurringFieldErrors(),
    val error: String? = null, // the save failed; the dialog stays open
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}

/** Everything the Recurring screen shows. The screen only reads this; it never changes it directly. */
data class RecurringUiState(
    val today: LocalDate = LocalDate.now(),
    val groups: RecurringGroups = RecurringGroups(emptyList(), emptyList(), emptyList()),
    val monthlyCents: Long = 0,
    val categoryNames: Map<String, String> = emptyMap(),
    val categories: List<Category> = emptyList(), // active ones, for the form
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null, // loading failed; offer a retry
    val editor: RecurringEditorState? = null, // null when the dialog is closed
    val savedOffline: Boolean = false, // the last change is on this phone, waiting to sync
)

private data class Loaded(
    val items: List<RecurringExpense>,
    val categories: List<Category>,
    val paymentMethods: List<PaymentMethod>,
)

private sealed interface RecurringData {
    data object Loading : RecurringData
    data class Failed(val message: String) : RecurringData
    data class Ready(val data: Loaded) : RecurringData
}

private data class DataState(val data: Loaded? = null, val isLoading: Boolean = true, val error: String? = null)

private data class Dialogs(val editor: RecurringEditorState? = null, val savedOffline: Boolean = false)

/**
 * Figma "09 Recurring" (FR-C6): every recurring cost in one list, grouped into due, coming up and
 * paused, with add, edit, pause and delete. Live listeners, so changes from other screens show at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listeners
    private val dialogs = MutableStateFlow(Dialogs())

    // Keep the last list visible while restarting or after an error (FR-H2).
    private val data: Flow<DataState> = attempt
        .flatMapLatest { load() }
        .scan(DataState()) { previous, d ->
            when (d) {
                RecurringData.Loading -> previous.copy(isLoading = previous.data == null, error = null)
                is RecurringData.Failed -> previous.copy(isLoading = false, error = d.message)
                is RecurringData.Ready -> DataState(d.data, isLoading = false)
            }
        }

    val uiState: StateFlow<RecurringUiState> = combine(data, dialogs, today) { d, dlg, day ->
        val items = d.data?.items.orEmpty()
        val categories = d.data?.categories.orEmpty()
        RecurringUiState(
            today = day,
            groups = groupRecurring(items, day),
            monthlyCents = monthlyEquivalentCents(items),
            categoryNames = categories.associate { it.id to it.name },
            categories = categories,
            paymentMethods = d.data?.paymentMethods.orEmpty(),
            isLoading = d.isLoading,
            error = d.error,
            editor = dlg.editor,
            savedOffline = dlg.savedOffline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun retry() = attempt.update { it + 1 }

    /** Called when the screen comes back into view, so "due" follows the calendar. */
    fun refresh() = today.update { LocalDate.now() }

    fun openNew() {
        val state = uiState.value
        dialogs.update {
            it.copy(
                editor = RecurringEditorState(
                    // Recurring costs are usually fixed obligations, so start on a committed category.
                    categoryId = state.categories.firstOrNull { c -> c.spendingType == SpendingType.COMMITTED }?.id
                        ?: state.categories.firstOrNull()?.id,
                    paymentMethodId = state.paymentMethods.firstOrNull()?.id,
                    nextDueDate = state.today,
                )
            )
        }
    }

    fun openItem(id: String) {
        val state = uiState.value
        val item = with(state.groups) { due + upcoming + paused }.firstOrNull { it.id == id } ?: return
        dialogs.update {
            it.copy(
                editor = RecurringEditorState(
                    id = item.id,
                    name = item.name,
                    amount = formatCents(item.amountCents),
                    categoryId = item.categoryId,
                    paymentMethodId = item.paymentMethodId,
                    frequency = item.frequency,
                    nextDueDate = item.nextDueDate,
                    active = item.active,
                )
            )
        }
    }

    fun onNameChange(text: String) =
        updateEditor { it.copy(name = text, errors = it.errors.copy(name = null), error = null) }

    fun onAmountChange(text: String) =
        updateEditor { it.copy(amount = text, errors = it.errors.copy(amount = null), error = null) }

    fun onCategorySelected(id: String) =
        updateEditor { it.copy(categoryId = id, errors = it.errors.copy(category = null), error = null) }

    fun onPaymentMethodSelected(id: String) =
        updateEditor { it.copy(paymentMethodId = id, errors = it.errors.copy(paymentMethod = null), error = null) }

    fun onFrequencySelected(frequency: RecurringFrequency) = updateEditor { it.copy(frequency = frequency) }

    fun onNextDueDateSelected(date: LocalDate) = updateEditor { it.copy(nextDueDate = date) }

    fun closeEditor() = dialogs.update { it.copy(editor = null) }

    fun save() {
        val editor = dialogs.value.editor ?: return
        if (editor.isSaving) return
        val input = RecurringInput(
            editor.name, editor.amount, editor.categoryId, editor.paymentMethodId, editor.frequency, editor.nextDueDate,
        )
        when (val check = checkRecurring(input, id = editor.id.orEmpty(), active = editor.active)) {
            is RecurringCheck.Invalid -> updateEditor { it.copy(errors = check.errors) }
            is RecurringCheck.Valid -> write {
                if (editor.isNew) repository.addRecurring(check.item) else repository.updateRecurring(check.item)
            }
        }
    }

    /** Pausing keeps the item in the list but stops it coming due, for example a cancelled subscription. */
    fun togglePaused() {
        val editor = dialogs.value.editor ?: return
        val id = editor.id ?: return
        write { repository.setActive(id, !editor.active) }
    }

    fun askDelete() = updateEditor { it.copy(confirmDelete = true) }

    fun cancelDelete() = updateEditor { it.copy(confirmDelete = false) }

    /** Expenses already recorded from this item are kept; they are real spending. */
    fun delete() {
        val id = dialogs.value.editor?.id ?: return
        updateEditor { it.copy(confirmDelete = false) }
        write { repository.deleteRecurring(id) }
    }

    fun onOfflineMessageShown() = dialogs.update { it.copy(savedOffline = false) }

    private fun write(block: suspend () -> Result<SaveOutcome>) {
        if (dialogs.value.editor?.isSaving == true) return
        updateEditor { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess { outcome -> dialogs.update { it.copy(editor = null, savedOffline = outcome.pendingSync) } }
                .onFailure { e -> updateEditor { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
        }
    }

    private fun updateEditor(change: (RecurringEditorState) -> RecurringEditorState) =
        dialogs.update { d -> d.editor?.let { d.copy(editor = change(it)) } ?: d }

    private fun load(): Flow<RecurringData> = combine(
        repository.observeRecurring(),
        // Names and form options only: a problem here must not hide the list itself.
        categoryRepository.observeCategories().catch { emit(emptyList()) },
        paymentMethodRepository.observePaymentMethods().catch { emit(emptyList()) },
    ) { items, categories, methods ->
        RecurringData.Ready(Loaded(items, categories, methods)) as RecurringData
    }
        .onStart { emit(RecurringData.Loading) }
        .catch { e -> emit(RecurringData.Failed(e.message ?: "Your recurring expenses couldn't be loaded.")) }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
