package com.mintfy.app.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.local.ExpensePreferences
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.model.RecurringExpense
import com.mintfy.app.data.model.SpendingType
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.ExpenseRepository
import com.mintfy.app.data.repository.PaymentMethodRepository
import com.mintfy.app.data.repository.RecurringRepository
import com.mintfy.app.data.repository.SaveOutcome
import com.mintfy.app.data.repository.toLocalDate
import com.mintfy.app.ui.navigation.Routes
import com.mintfy.app.ui.screens.income.formatCents
import com.mintfy.app.ui.screens.recurring.next
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the Add Expense screen shows. The screen only reads this; it never changes it directly. */
data class AddExpenseUiState(
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedPaymentMethodId: String? = null,
    val isLoadingOptions: Boolean = true,
    val amountError: String? = null,
    val dateError: String? = null,
    val noteError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedOffline: Boolean = false,
    val isDeleted: Boolean = false,
    val isEditing: Boolean = false,
    val recordingFor: String? = null, // the recurring item's name when recording one of its periods
)

/** What the user has typed or picked so far. Choices left null fall back to the defaults below. */
private data class ExpenseForm(
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val categoryId: String? = null,
    val paymentMethodId: String? = null,
    val amountError: String? = null,
    val dateError: String? = null,
    val noteError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedOffline: Boolean = false,
    val isDeleted: Boolean = false,
    val isEditing: Boolean = false,
    val isLoadingEntry: Boolean = false,
    val loadedDate: LocalDate? = null, // the saved date, so an unchanged one is not rewritten
    val recurring: RecurringExpense? = null, // set when recording a period of a recurring item
)

/**
 * Add an expense, or edit a saved one when opened with an expense ID (FR-C5), or record this period
 * of a recurring item when opened with a recurring ID (FR-C6).
 */
@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    categoryRepository: CategoryRepository,
    paymentMethodRepository: PaymentMethodRepository,
    private val expenseRepository: ExpenseRepository,
    private val recurringRepository: RecurringRepository,
    private val preferences: ExpensePreferences,
) : ViewModel() {

    private val editId: String? = savedStateHandle[Routes.ARG_ID]
    private val recurringId: String? = if (editId == null) savedStateHandle[Routes.ARG_RECURRING_ID] else null

    private val form = MutableStateFlow(
        ExpenseForm(isEditing = editId != null, isLoadingEntry = editId != null || recurringId != null)
    )

    // null means "still loading"; an empty list means loaded but nothing there yet.
    private val categories = categoryRepository.observeCategories().loadingThenList()
    private val paymentMethods = paymentMethodRepository.observePaymentMethods().loadingThenList()

    val uiState: StateFlow<AddExpenseUiState> = combine(form, categories, paymentMethods) { f, cats, methods ->
        // Figma "05 Add expense": everyday (discretionary) categories first, then committed ones.
        // sortedBy is stable, so each group keeps the repository's order.
        val categoryList = cats.orEmpty().sortedBy { it.spendingType != SpendingType.DISCRETIONARY }
        val methodList = methods.orEmpty()
        AddExpenseUiState(
            amount = f.amount,
            date = f.date,
            note = f.note,
            categories = categoryList,
            paymentMethods = methodList,
            selectedCategoryId = pick(
                f.categoryId, preferences.lastCategoryId, FIRST_TIME_CATEGORY, categoryList.map { it.id },
            ),
            selectedPaymentMethodId = pick(
                f.paymentMethodId, preferences.lastPaymentMethodId, null, methodList.map { it.id },
            ),
            isLoadingOptions = cats == null || methods == null || f.isLoadingEntry,
            amountError = f.amountError,
            dateError = f.dateError,
            noteError = f.noteError,
            error = f.error,
            isSaving = f.isSaving,
            isSaved = f.isSaved,
            savedOffline = f.savedOffline,
            isDeleted = f.isDeleted,
            isEditing = f.isEditing,
            recordingFor = f.recurring?.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState(isEditing = editId != null))

    init {
        if (editId != null) {
            viewModelScope.launch {
                expenseRepository.getExpense(editId)
                    .onSuccess { e ->
                        val date = e.date.toLocalDate()
                        form.update {
                            it.copy(
                                amount = formatCents(e.amountCents),
                                date = date,
                                note = e.note.orEmpty(),
                                categoryId = e.categoryId,
                                paymentMethodId = e.paymentMethodId,
                                isLoadingEntry = false,
                                loadedDate = date,
                            )
                        }
                    }
                    .onFailure { e -> form.update { it.copy(isLoadingEntry = false, error = e.message ?: GENERIC_ERROR) } }
            }
        }
        if (recurringId != null) {
            viewModelScope.launch {
                recurringRepository.getRecurring(recurringId)
                    .onSuccess { r ->
                        form.update {
                            it.copy(
                                // The usual amount, category and payment method; all can be changed,
                                // because committed costs such as utilities vary each month (FR-D3).
                                amount = formatCents(r.amountCents),
                                // Dated the day it fell due, so a late entry still lands in the right month.
                                date = minOf(r.nextDueDate, LocalDate.now()),
                                note = r.name,
                                categoryId = r.categoryId,
                                paymentMethodId = r.paymentMethodId,
                                isLoadingEntry = false,
                                recurring = r,
                            )
                        }
                    }
                    .onFailure { e -> form.update { it.copy(isLoadingEntry = false, error = e.message ?: GENERIC_ERROR) } }
            }
        }
    }

    fun onAmountChange(text: String) = form.update { it.copy(amount = text, amountError = null, error = null) }

    fun onCategorySelected(id: String) = form.update { it.copy(categoryId = id, error = null) }

    fun onPaymentMethodSelected(id: String) = form.update { it.copy(paymentMethodId = id, error = null) }

    fun onDateSelected(date: LocalDate) = form.update { it.copy(date = date, dateError = null) }

    fun onNoteChange(text: String) = form.update { it.copy(note = text, noteError = null) }

    /** Validates, then saves. On success the screen sees isSaved and closes. */
    fun save() {
        val current = form.value
        if (current.isSaving || current.isSaved || current.isLoadingEntry) return
        val state = uiState.value

        val amountError = amountError(state.amount)
        val dateError = dateError(state.date, LocalDate.now())
        val noteError = noteError(state.note)
        val categoryId = state.selectedCategoryId
        val paymentMethodId = state.selectedPaymentMethodId
        val choiceError = when {
            categoryId == null -> "Choose a category."
            paymentMethodId == null -> "Choose how you paid."
            else -> null
        }
        if (amountError != null || dateError != null || noteError != null || choiceError != null) {
            form.update {
                it.copy(amountError = amountError, dateError = dateError, noteError = noteError, error = choiceError)
            }
            return
        }

        val amountCents = parseAmountToCents(state.amount) ?: return

        if (editId != null) {
            // Editing waits briefly for the server so a rejected change is shown, not lost.
            // The "last used" defaults are only for new expenses, so they are left alone.
            form.update { it.copy(isSaving = true, error = null) }
            viewModelScope.launch {
                finish(
                    expenseRepository.updateExpense(
                        id = editId,
                        amountCents = amountCents,
                        date = state.date,
                        dateChanged = state.date != current.loadedDate,
                        categoryId = categoryId!!,
                        paymentMethodId = paymentMethodId!!,
                        note = state.note,
                    )
                )
            }
            return
        }

        val recurring = current.recurring
        expenseRepository.addExpense(
            amountCents = amountCents,
            date = state.date,
            categoryId = categoryId!!,
            paymentMethodId = paymentMethodId!!,
            note = state.note,
            recurringId = recurring?.id,
        )
            .onSuccess {
                if (recurring != null) {
                    // This period is recorded, so the item moves on to its next due date.
                    recurringRepository.advanceTo(recurring.id, recurring.frequency.next(recurring.nextDueDate))
                } else {
                    // Rent and bills are not everyday spending, so they don't change the quick-entry defaults.
                    preferences.remember(categoryId, paymentMethodId)
                }
                form.update { it.copy(isSaved = true, error = null) }
            }
            .onFailure { e -> form.update { it.copy(error = e.message) } }
    }

    /** Deletes the expense being edited. The screen asks for confirmation first (FR-H5). */
    fun delete() {
        val id = editId ?: return
        val current = form.value
        if (current.isSaving || current.isSaved || current.isLoadingEntry) return
        form.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch { finish(expenseRepository.deleteExpense(id), deleted = true) }
    }

    private fun finish(result: Result<SaveOutcome>, deleted: Boolean = false) {
        result
            .onSuccess { outcome ->
                form.update {
                    it.copy(isSaving = false, isSaved = true, savedOffline = outcome.pendingSync, isDeleted = deleted)
                }
            }
            .onFailure { e -> form.update { it.copy(isSaving = false, error = e.message ?: GENERIC_ERROR) } }
    }

    /** Shows a loading state until the first list arrives; on an error, shows the message and an empty list. */
    private fun <T> Flow<List<T>>.loadingThenList(): Flow<List<T>?> =
        map<List<T>, List<T>?> { it }
            .onStart { emit(null) }
            .catch { e ->
                form.update { it.copy(error = e.message ?: "Something went wrong. Please try again.") }
                emit(emptyList())
            }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."

        // Before the first save there is no "last used" category. Coffee and casual dining were Kavindu's
        // biggest unseen cost (LKR 24,000 in one month), so the most frequent quick entry is pre-selected.
        const val FIRST_TIME_CATEGORY = "default_food_coffee"

        /** The user's choice, else the last used, else the first-time default, else the first in the list. */
        fun pick(chosen: String?, lastUsed: String?, firstTime: String?, available: List<String>): String? =
            listOf(chosen, lastUsed, firstTime).firstOrNull { it != null && it in available }
                ?: available.firstOrNull()
    }
}
