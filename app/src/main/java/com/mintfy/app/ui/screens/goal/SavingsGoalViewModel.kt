
package com.mintfy.app.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalStatus
import com.mintfy.app.data.repository.SavingsGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGoalUiState(
    val name: String = "",
    val targetAmount: String = "",
    val startingSavedAmount: String = "",
    val deadline: LocalDate = LocalDate.now().plusMonths(12),
    val nameError: String? = null,
    val targetAmountError: String? = null,
    val startingSavedAmountError: String? = null,
    val deadlineError: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SavingsGoalViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGoalUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.update {
            it.copy(
                name = value,
                nameError = null,
                errorMessage = null,
            )
        }
    }

    fun onTargetAmountChange(value: String) {
        _uiState.update {
            it.copy(
                targetAmount = value,
                targetAmountError = null,
                errorMessage = null,
            )
        }
    }

    fun onStartingSavedAmountChange(value: String) {
        _uiState.update {
            it.copy(
                startingSavedAmount = value,
                startingSavedAmountError = null,
                errorMessage = null,
            )
        }
    }

    fun onDeadlineChange(value: LocalDate) {
        _uiState.update {
            it.copy(
                deadline = value,
                deadlineError = null,
                errorMessage = null,
            )
        }
    }

    fun saveGoal(onSaved: () -> Unit) {
        val current = _uiState.value

        if (current.isSaving) return

        val name = current.name.trim()
        val targetCents = parseAmountToCents(current.targetAmount)

        val startingSavedCents = parseAmountToCents(
            current.startingSavedAmount.ifBlank { "0" }
        )

        val today = LocalDate.now()

        val nameError = when {
            name.isBlank() -> "Enter a goal name."
            name.length > 60 -> "The name cannot exceed 60 characters."
            else -> null
        }

        val targetError = when {
            current.targetAmount.isBlank() ->
                "Enter a target amount."

            targetCents == null ->
                "Enter a valid amount with up to 2 decimal places."

            targetCents <= 0L ->
                "The target amount must be greater than zero."

            else -> null
        }

        val savedError = when {
            startingSavedCents == null ->
                "Enter a valid amount with up to 2 decimal places."

            startingSavedCents < 0L ->
                "The amount already saved cannot be negative."

            targetCents != null && startingSavedCents > targetCents ->
                "The amount already saved cannot exceed the target."

            else -> null
        }

        val deadlineError = when {
            !current.deadline.isAfter(today) ->
                "The deadline must be after today."

            else -> null
        }

        _uiState.update {
            it.copy(
                nameError = nameError,
                targetAmountError = targetError,
                startingSavedAmountError = savedError,
                deadlineError = deadlineError,
                errorMessage = null,
            )
        }

        if (
            nameError != null ||
            targetError != null ||
            savedError != null ||
            deadlineError != null
        ) {
            return
        }

        val goal = SavingsGoal(
            name = name,
            targetAmountCents = requireNotNull(targetCents),
            startingSavedCents = requireNotNull(startingSavedCents),
            startDate = today,
            deadline = current.deadline,
            status = SavingsGoalStatus.ACTIVE,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val result = savingsGoalRepository.addGoal(goal)

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isSaving = false)
                    }
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message
                                ?: "Unable to save the goal. Please try again.",
                        )
                    }
                },
            )
        }
    }

    /**
     * Converts an LKR amount into integer cents.
     * Floating-point arithmetic is not used.
     */
    private fun parseAmountToCents(value: String): Long? {
        val cleaned = value.trim().replace(",", "")

        if (cleaned.isBlank()) return null

        if (!cleaned.matches(Regex("""\d+(\.\d{1,2})?"""))) {
            return null
        }

        return try {
            BigDecimal(cleaned)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }
}
