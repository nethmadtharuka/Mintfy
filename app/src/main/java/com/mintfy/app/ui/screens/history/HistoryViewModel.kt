package com.mintfy.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.Category
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.ExpenseRepository
import com.mintfy.app.data.repository.IncomeRepository
import com.mintfy.app.data.repository.IncomeSourceRepository
import com.mintfy.app.data.repository.PaymentMethodRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** Everything the History screen shows. The screen only reads this; it never changes it directly. */
data class HistoryUiState(
    val range: DateRange = DateRange.monthOf(LocalDate.now()),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val rows: List<HistoryRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** What the listeners have delivered for the current date range. */
private sealed interface HistoryData {
    data object Loading : HistoryData
    data class Failed(val message: String) : HistoryData
    data class Loaded(val build: (categoryId: String?) -> List<HistoryRow>, val categories: List<Category>) : HistoryData
}

/**
 * Income and expenses for a date range, newest first (FR-C7). Every list is a live Firestore listener,
 * so adds, edits and deletes (from this phone or another) appear without a refresh, and the cached
 * copy is shown when there is no connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val sourceRepository: IncomeSourceRepository,
) : ViewModel() {

    private val range = MutableStateFlow(DateRange.monthOf(LocalDate.now()))
    private val categoryId = MutableStateFlow<String?>(null)
    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listeners

    // Changing the category only filters what is already loaded; a new range or a retry restarts the listeners.
    private val data: Flow<HistoryData> = combine(range, attempt) { r, _ -> r }
        .flatMapLatest(::load)

    val uiState: StateFlow<HistoryUiState> = combine(data, range, categoryId) { d, r, c ->
        when (d) {
            HistoryData.Loading -> HistoryUiState(range = r, selectedCategoryId = c, isLoading = true)
            is HistoryData.Failed -> HistoryUiState(range = r, selectedCategoryId = c, isLoading = false, error = d.message)
            is HistoryData.Loaded -> HistoryUiState(
                range = r,
                categories = d.categories,
                // A category can be archived while selected; then show everything rather than nothing.
                selectedCategoryId = c?.takeIf { id -> d.categories.any { it.id == id } },
                rows = d.build(c),
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** A start date after the end date moves the end date with it, so the range is never empty by mistake. */
    fun onFromSelected(date: LocalDate) = range.update { DateRange(date, maxOf(date, it.to)) }

    fun onToSelected(date: LocalDate) = range.update { DateRange(minOf(date, it.from), date) }

    fun onCategorySelected(id: String?) = categoryId.update { id }

    fun retry() = attempt.update { it + 1 }

    private fun load(r: DateRange): Flow<HistoryData> = combine(
        expenseRepository.observeExpenses(r.from, r.to),
        incomeRepository.observeEntries(r.from, r.to),
        categoryRepository.observeCategories(),
        paymentMethodRepository.observePaymentMethods(),
        sourceRepository.observeSources(),
    ) { expenses, income, categories, methods, sources ->
        val categoryNames = categories.associate { it.id to it.name }
        val methodNames = methods.associate { it.id to it.name }
        val sourceNames = sources.associate { it.id to it.name }
        HistoryData.Loaded(
            build = { c -> historyRows(expenses, income, r, c, categoryNames, methodNames, sourceNames) },
            categories = categories,
        ) as HistoryData
    }
        .onStart { emit(HistoryData.Loading) }
        .catch { e -> emit(HistoryData.Failed(e.message ?: "Your history couldn't be loaded.")) }
}
