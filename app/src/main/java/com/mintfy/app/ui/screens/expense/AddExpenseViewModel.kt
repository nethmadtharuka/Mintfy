package com.mintfy.app.ui.screens.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.local.ExpensePreferences
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.model.PaymentMethod
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.ExpenseRepository
import com.mintfy.app.data.repository.PaymentMethodRepository
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
    val isSaved: Boolean = false,
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
    val isSaved: Boolean = false,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    paymentMethodRepository: PaymentMethodRepository,
    private val expenseRepository: ExpenseRepository,
    private val preferences: ExpensePreferences,
) : ViewModel() {

    private val form = MutableStateFlow(ExpenseForm())

    // null means "still loading"; an empty list means loaded but nothing there yet.
    private val categories = categoryRepository.observeCategories().loadingThenList()
    private val paymentMethods = paymentMethodRepository.observePaymentMethods().loadingThenList()

    val uiState: StateFlow<AddExpenseUiState> = combine(form, categories, paymentMethods) { f, cats, methods ->
        val categoryList = cats.orEmpty()
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
            isLoadingOptions = cats == null || methods == null,
            amountError = f.amountError,
            dateError = f.dateError,
            noteError = f.noteError,
            error = f.error,
            isSaved = f.isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState())

    fun onAmountChange(text: String) = form.update { it.copy(amount = text, amountError = null, error = null) }

    fun onCategorySelected(id: String) = form.update { it.copy(categoryId = id, error = null) }

    fun onPaymentMethodSelected(id: String) = form.update { it.copy(paymentMethodId = id, error = null) }

    fun onDateSelected(date: LocalDate) = form.update { it.copy(date = date, dateError = null) }

    fun onNoteChange(text: String) = form.update { it.copy(note = text, noteError = null) }

    /** Validates, then saves. On success the screen sees isSaved and closes. */
    fun save() {
        val state = uiState.value
        if (state.isSaved) return

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

        expenseRepository.addExpense(
            amountCents = parseAmountToCents(state.amount) ?: return,
            date = state.date,
            categoryId = categoryId!!,
            paymentMethodId = paymentMethodId!!,
            note = state.note,
        )
            .onSuccess {
                preferences.remember(categoryId, paymentMethodId)
                form.update { it.copy(isSaved = true, error = null) }
            }
            .onFailure { e -> form.update { it.copy(error = e.message) } }
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
        // Before the first save there is no "last used" category. Coffee and casual dining were Kavindu's
        // biggest unseen cost (LKR 24,000 in one month), so the most frequent quick entry is pre-selected.
        const val FIRST_TIME_CATEGORY = "default_food_coffee"

        /** The user's choice, else the last used, else the first-time default, else the first in the list. */
        fun pick(chosen: String?, lastUsed: String?, firstTime: String?, available: List<String>): String? =
            listOf(chosen, lastUsed, firstTime).firstOrNull { it != null && it in available }
                ?: available.firstOrNull()
    }
}
