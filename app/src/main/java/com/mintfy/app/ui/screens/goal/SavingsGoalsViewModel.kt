
package com.mintfy.app.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.repository.SavingsGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavingsGoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<SavingsGoal> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val repository: SavingsGoalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsGoalsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeGoals()
    }

    private fun observeGoals() {
        viewModelScope.launch {
            repository.observeGoals()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?: "Unable to load savings goals.",
                        )
                    }
                }
                .collect { goals ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            goals = goals,
                            errorMessage = null,
                        )
                    }
                }
        }
    }
}
