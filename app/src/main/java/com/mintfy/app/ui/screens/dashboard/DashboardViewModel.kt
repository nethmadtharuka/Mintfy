package com.mintfy.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.repository.CategoryRepository
import com.mintfy.app.data.repository.ExpenseRepository
import com.mintfy.app.data.repository.IncomeRepository
import com.mintfy.app.data.repository.IncomeSourceRepository
import com.mintfy.app.data.repository.UserProfileRepository
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
import java.time.YearMonth
import javax.inject.Inject

/** Everything the dashboard insights show. The screen only reads this; it never changes it directly. */
data class DashboardUiState(
    val month: YearMonth = YearMonth.now(),
    val summary: MonthlySummary? = null, // the last good figures; kept on screen if a later update fails
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** What the listeners have delivered for the current month. */
private sealed interface DashboardData {
    data class Loading(val month: YearMonth) : DashboardData
    data class Failed(val month: YearMonth, val message: String) : DashboardData
    data class Loaded(val summary: MonthlySummary) : DashboardData
}

/**
 * This month's income, spending, savings and breakdowns (FR-F1, FR-F2, FR-F3, FR-F6, FR-B8).
 * Every input is a live Firestore listener, so a new or edited entry updates the dashboard within a
 * second with no refresh (FR-F5, NFR-02), and the phone's cached copy is shown when offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val sourceRepository: IncomeSourceRepository,
    private val profileRepository: UserProfileRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val attempt = MutableStateFlow(0) // bumped by retry() to restart the listeners

    val uiState: StateFlow<DashboardUiState> = combine(month, attempt) { m, _ -> m }
        .flatMapLatest(::load)
        .scan(DashboardUiState()) { previous, data ->
            when (data) {
                // Keep showing this month's figures while the listeners restart (for example after a retry).
                is DashboardData.Loading -> {
                    val kept = previous.summary?.takeIf { it.month == data.month }
                    DashboardUiState(data.month, kept, isLoading = kept == null)
                }
                // FR-H2: show the error with a retry, but leave the last figures visible.
                is DashboardData.Failed -> previous.copy(month = data.month, isLoading = false, error = data.message)
                is DashboardData.Loaded -> DashboardUiState(data.summary.month, data.summary, isLoading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun retry() = attempt.update { it + 1 }

    /** Called when the dashboard comes back into view, so it rolls over to a new month on the 1st. */
    fun refresh() = month.update { YearMonth.now() } // no restart if the month has not changed

    private fun load(m: YearMonth): Flow<DashboardData> = combine(
        expenseRepository.observeExpenses(m.atDay(1), m.atEndOfMonth()),
        incomeRepository.observeEntries(m.atDay(1), m.atEndOfMonth()),
        categoryRepository.observeCategories(),
        sourceRepository.observeSources(),
        // The estimate is optional, so a profile problem must not hide the rest of the dashboard.
        profileRepository.observeCurrentProfile().catch { emit(null) },
    ) { expenses, income, categories, sources, profile ->
        DashboardData.Loaded(
            monthlySummary(
                month = m,
                expenses = expenses,
                incomeEntries = income,
                categories = categories,
                sources = sources,
                estimateMinCents = profile?.estimateMinCents,
                estimateMaxCents = profile?.estimateMaxCents,
            )
        ) as DashboardData
    }
        .onStart { emit(DashboardData.Loading(m)) }
        .catch { e -> emit(DashboardData.Failed(m, e.message ?: "Your dashboard couldn't be loaded.")) }
}
