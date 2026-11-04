package com.mintfy.app.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.IncomeStatus
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalProgress
import com.mintfy.app.data.repository.ExpenseRepository
import com.mintfy.app.data.repository.IncomeRepository
import com.mintfy.app.data.repository.SavingsGoalRepository
import com.mintfy.app.data.repository.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalUiState(
    val isLoading: Boolean = true,
    val goal: SavingsGoal? = null,
    val progress: SavingsGoalProgress? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isRetrying: Boolean = false
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: SavingsGoalRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    private var financialDataJob: Job? = null

    init {
        observeGoal()
    }

    private fun observeGoal() {
        viewModelScope.launch {
            goalRepository.observeActiveGoal()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Unable to load savings goal."
                    )
                }
                .collect { goal ->
                    financialDataJob?.cancel()

                    if (goal == null) {
                        _uiState.value = GoalUiState(
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            goal = goal,
                            error = null
                        )
                        observeFinancialData(goal)
                    }
                }
        }
    }

    private fun observeFinancialData(goal: SavingsGoal) {
        financialDataJob = viewModelScope.launch {
            val today = LocalDate.now()
            val monthStart = maxOf(
                today.withDayOfMonth(1),
                goal.startDate
            )

            if (goal.startDate.isAfter(today)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    progress = SavingsGoalCalculator.calculate(
                        goal = goal,
                        receivedIncomeSinceStartCents = 0L,
                        expensesSinceStartCents = 0L,
                        actualMonthlySavingsCents = 0L,
                        today = today
                    )
                )
                return@launch
            }

            val incomeSinceStart = incomeRepository.observeEntries(
                goal.startDate,
                today
            )

            val expensesSinceStart = expenseRepository.observeExpenses(
                goal.startDate,
                today
            )

            val incomeThisMonth = incomeRepository.observeEntries(
                monthStart,
                today
            )

            val expensesThisMonth = expenseRepository.observeExpenses(
                monthStart,
                today
            )

            combine(
                incomeSinceStart,
                expensesSinceStart,
                incomeThisMonth,
                expensesThisMonth
            ) { allIncome, allExpenses, monthlyIncome, monthlyExpenses ->

                val receivedIncomeTotal = allIncome
                    .filter { it.status == IncomeStatus.RECEIVED }
                    .sumOf { it.lkrAmountCents }

                val expenseTotal = allExpenses.sumOf { it.amountCents }

                val receivedMonthlyIncome = monthlyIncome
                    .filter { it.status == IncomeStatus.RECEIVED }
                    .sumOf { it.lkrAmountCents }

                val monthlyExpenseTotal = monthlyExpenses
                    .sumOf { it.amountCents }

                val actualMonthlySavings = safeSubtract(
                    receivedMonthlyIncome,
                    monthlyExpenseTotal
                )

                SavingsGoalCalculator.calculate(
                    goal = goal,
                    receivedIncomeSinceStartCents = receivedIncomeTotal,
                    expensesSinceStartCents = expenseTotal,
                    actualMonthlySavingsCents = actualMonthlySavings,
                    today = LocalDate.now()
                )
            }
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message
                            ?: "Unable to calculate savings goal progress."
                    )
                }
                .collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        goal = goal,
                        progress = progress,
                        error = null
                    )
                }
        }
    }

    fun createGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                error = null
            )

            val result = goalRepository.createGoal(goal)

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRetrying = true,
                error = null
            )

            val result = goalRepository.retrySync()

            _uiState.value = _uiState.value.copy(
                isRetrying = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    /**
     * Call when the screen resumes so date-dependent calculations
     * are refreshed after the app has been in the background.
     */
    fun refresh() {
        val goal = _uiState.value.goal ?: return
        financialDataJob?.cancel()
        observeFinancialData(goal)
    }

    private fun safeSubtract(first: Long, second: Long): Long {
        return try {
            Math.subtractExact(first, second)
        } catch (_: ArithmeticException) {
            if (second < 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }
}