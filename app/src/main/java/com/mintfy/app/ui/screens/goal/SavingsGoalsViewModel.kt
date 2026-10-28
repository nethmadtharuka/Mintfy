
package com.mintfy.app.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.domain.savingsgoal.SavingsGoalCalculations
import com.mintfy.app.data.domain.savingsgoal.SavingsGoalProgress
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.repository.SavingsGoalFinancialData
import com.mintfy.app.data.repository.SavingsGoalFinancialRepository
import com.mintfy.app.data.repository.SavingsGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavingsGoalItemUiModel(
    val goal: SavingsGoal,
    val progress: SavingsGoalProgress,
)

data class SavingsGoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<SavingsGoalItemUiModel> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val goalRepository: SavingsGoalRepository,
    private val financialRepository: SavingsGoalFinancialRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsGoalsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeGoalsAndFinancialData()
    }

    private fun observeGoalsAndFinancialData() {
        viewModelScope.launch {
            combine(
                goalRepository.observeGoals(),
                financialRepository.observeFinancialData(),
            ) { goals, financialData ->
                val today = LocalDate.now()

                goals.map { goal ->
                    val currentSaved = calculateCurrentSaved(
                        goal = goal,
                        financialData = financialData,
                        today = today,
                    )

                    val monthlySavings = calculateCurrentMonthSavings(
                        financialData = financialData,
                        today = today,
                    )

                    SavingsGoalItemUiModel(
                        goal = goal,
                        progress = SavingsGoalCalculations.calculateProgress(
                            targetAmountCents = goal.targetAmountCents,
                            currentSavedCents = currentSaved,
                            deadline = goal.deadline,
                            today = today,
                            actualMonthlySavingsCents = monthlySavings,
                        ),
                    )
                }
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Unable to load savings goal progress.",
                    )
                }
            }.collect { goalItems ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        goals = goalItems,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun calculateCurrentSaved(
        goal: SavingsGoal,
        financialData: SavingsGoalFinancialData,
        today: LocalDate,
    ): Long {
        val incomeSinceStart = financialData.receivedIncome
            .asSequence()
            .filter { !it.date.isBefore(goal.startDate) }
            .filter { !it.date.isAfter(today) }
            .sumOf { it.amountCents }

        val expensesSinceStart = financialData.expenses
            .asSequence()
            .filter { !it.date.isBefore(goal.startDate) }
            .filter { !it.date.isAfter(today) }
            .sumOf { it.amountCents }

        val netSavingsSinceStart = incomeSinceStart - expensesSinceStart

        return (goal.startingSavedCents + netSavingsSinceStart)
            .coerceAtLeast(0L)
    }

    private fun calculateCurrentMonthSavings(
        financialData: SavingsGoalFinancialData,
        today: LocalDate,
    ): Long {
        val monthStart = today.withDayOfMonth(1)

        val monthlyIncome = financialData.receivedIncome
            .asSequence()
            .filter { !it.date.isBefore(monthStart) }
            .filter { !it.date.isAfter(today) }
            .sumOf { it.amountCents }

        val monthlyExpenses = financialData.expenses
            .asSequence()
            .filter { !it.date.isBefore(monthStart) }
            .filter { !it.date.isAfter(today) }
            .sumOf { it.amountCents }

        return monthlyIncome - monthlyExpenses
    }
}
