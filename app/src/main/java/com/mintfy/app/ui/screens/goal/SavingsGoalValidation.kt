package com.mintfy.app.ui.screens.goal

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class SavingsGoalFormErrors(
    val name: String? = null,
    val targetAmount: String? = null,
    val startingSaved: String? = null,
    val deadline: String? = null
)

data class ValidatedSavingsGoalInput(
    val name: String,
    val targetAmountCents: Long,
    val startingSavedCents: Long,
    val startDate: LocalDate,
    val deadline: LocalDate
)

object SavingsGoalValidation {

    fun validate(
        name: String,
        targetAmount: String,
        startingSaved: String,
        startDate: LocalDate,
        deadline: LocalDate
    ): Pair<ValidatedSavingsGoalInput?, SavingsGoalFormErrors> {

        val cleanName = name.trim()

        val target = parseCents(targetAmount)
        val saved = parseCents(startingSaved)

        val errors = SavingsGoalFormErrors(

            name = when {
                cleanName.isBlank() ->
                    "Enter a goal name."

                cleanName.length > 60 ->
                    "Goal name must be 60 characters or fewer."

                else -> null
            },

            targetAmount = when {
                target == null ->
                    "Enter a valid target amount."

                target <= 0L ->
                    "Target amount must be greater than zero."

                else -> null
            },

            startingSaved = when {
                saved == null ->
                    "Enter a valid saved amount."

                saved < 0L ->
                    "Saved amount cannot be negative."

                else -> null
            },

            deadline = when {
                !deadline.isAfter(startDate) ->
                    "Deadline must be after the start date."

                else -> null
            }
        )

        if (
            errors.name != null ||
            errors.targetAmount != null ||
            errors.startingSaved != null ||
            errors.deadline != null
        ) {
            return Pair(null, errors)
        }

        return Pair(
            ValidatedSavingsGoalInput(
                name = cleanName,
                targetAmountCents = target!!,
                startingSavedCents = saved!!,
                startDate = startDate,
                deadline = deadline
            ),
            errors
        )
    }

    fun parseCents(raw: String): Long? {
        return runCatching {
            BigDecimal(raw.trim())
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }
}