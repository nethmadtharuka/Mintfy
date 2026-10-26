package com.mintfy.app.data.domain.savingsgoal

import java.time.LocalDate
import java.time.Period

/**
 * The result of calculating a savings goal's current progress.
 * currentSavedCents is supplied by the caller. It can initially be the starting saved amount; later, the financial-data integration
 * will calculate the current amount using eligible savings records.
 */
data class SavingsGoalProgress(
    val currentSavedCents: Long,
    val remainingAmountCents: Long,
    val progressPercentage: Double,
    val monthsRemaining: Int,
    val requiredMonthlySavingsCents: Long?,
    val status: GoalProgressStatus
)

/**
 * Calculated progress status. This is separate from the persisted
 * SavingsGoalStatus lifecycle status.
 */
enum class GoalProgressStatus {
    ON_TRACK,
    BEHIND,
    ACHIEVED
}

/**
 * Pure calculation functions for the Savings Goal feature.
 * These functions do not access Firebase or modify stored data.
 */
object SavingsGoalCalculations {

    /**
     * Calculates the number of calendar months remaining
     * A partial month counts as one month so the required monthly
     * savings amount is not underestimated.
     * examples:
     * -> Same date as the deadline: 0 months
     * -> Deadline in 10 days: 1 month
     * -> Deadline exactly 12 months away: 12 months
     * -> Deadline in the past: 0 months
     */

    fun calculateMonthsRemaining(
        today: LocalDate,
        deadline: LocalDate
    ): Int {
        if (!deadline.isAfter(today)) {
            return 0
        }

        val period = Period.between(today, deadline)

        val completeMonths = period.years * 12 + period.months
        val hasPartialMonth = period.days > 0

        return completeMonths + if (hasPartialMonth) 1 else 0
    }

    /**
     * Calculates how much more money is needed to reach the target.
     */
    fun calculateRemainingAmount(
        targetAmountCents: Long,
        currentSavedCents: Long
    ): Long {
        require(targetAmountCents > 0) {
            "Target amount must be greater than zero."
        }
        require(currentSavedCents >= 0) {
            "Current saved amount cannot be negative."
        }

        return (targetAmountCents - currentSavedCents).coerceAtLeast(0L)
    }

    /**
     * Calculates progress as a percentage, capped at 100%.
     */
    fun calculateProgressPercentage(
        targetAmountCents: Long,
        currentSavedCents: Long
    ): Double {
        require(targetAmountCents > 0) {
            "Target amount must be greater than zero."
        }
        require(currentSavedCents >= 0) {
            "Current saved amount cannot be negative."
        }

        return (
                currentSavedCents.toDouble() /
                        targetAmountCents.toDouble() * 100.0
                ).coerceIn(0.0, 100.0)
    }

    /**
     * Calculates the minimum whole-cent monthly saving needed
     * to cover the remaining amount before the deadline.
     * returns:
     * - 0 if the target has already been reached
     * - null if the deadline has arrived/passed and money is
     *   still needed
     * - Otherwise, the remaining amount divided across the
     *   remaining months, rounded UP to a whole cent
     */

    fun calculateRequiredMonthlySavings(
        remainingAmountCents: Long,
        monthsRemaining: Int
    ): Long? {
        require(remainingAmountCents >= 0) {
            "Remaining amount cannot be negative."
        }
        require(monthsRemaining >= 0) {
            "Months remaining cannot be negative."
        }

        if (remainingAmountCents == 0L) {
            return 0L
        }

        if (monthsRemaining == 0) {
            return null
        }

        val wholeMonths = monthsRemaining.toLong()
        val quotient = remainingAmountCents / wholeMonths
        val remainder = remainingAmountCents % wholeMonths

        // Round up so the required pace never falls below the target.
        return quotient + if (remainder > 0L) 1L else 0L
    }

    /**
     * Combines the calculations into one progress result.
     * actualMonthlySavingsCents must represent:
     * received income for the relevant month - recorded expenses
     * for that month. Expected and invoiced income must be excluded.
     */
    fun calculateProgress(
        targetAmountCents: Long,
        currentSavedCents: Long,
        deadline: LocalDate,
        today: LocalDate,
        actualMonthlySavingsCents: Long
    ): SavingsGoalProgress {
        require(targetAmountCents > 0) {
            "Target amount must be greater than zero."
        }
        require(currentSavedCents >= 0) {
            "Current saved amount cannot be negative."
        }

        val remainingAmount = calculateRemainingAmount(
            targetAmountCents = targetAmountCents,
            currentSavedCents = currentSavedCents
        )

        val percentage = calculateProgressPercentage(
            targetAmountCents = targetAmountCents,
            currentSavedCents = currentSavedCents
        )

        val monthsRemaining = calculateMonthsRemaining(
            today = today,
            deadline = deadline
        )

        val requiredMonthlySavings = calculateRequiredMonthlySavings(
            remainingAmountCents = remainingAmount,
            monthsRemaining = monthsRemaining
        )

        val progressStatus = when {
            remainingAmount == 0L -> GoalProgressStatus.ACHIEVED

            requiredMonthlySavings == null ->
                GoalProgressStatus.BEHIND

            actualMonthlySavingsCents >= requiredMonthlySavings ->
                GoalProgressStatus.ON_TRACK

            else -> GoalProgressStatus.BEHIND
        }

        return SavingsGoalProgress(
            currentSavedCents = currentSavedCents,
            remainingAmountCents = remainingAmount,
            progressPercentage = percentage,
            monthsRemaining = monthsRemaining,
            requiredMonthlySavingsCents = requiredMonthlySavings,
            status = progressStatus
        )
    }
}
