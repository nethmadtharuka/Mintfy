package com.mintfy.app.ui.screens.goal

import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalProgress
import java.time.LocalDate
import java.time.YearMonth

object SavingsGoalCalculator {

    fun calculate(
        goal: SavingsGoal,
        receivedIncomeSinceStartCents: Long,
        expensesSinceStartCents: Long,
        actualMonthlySavingsCents: Long,
        today: LocalDate
    ): SavingsGoalProgress {

        // Calculate cumulative savings since goal creation.
        val netSavings = safeSubtract(
            receivedIncomeSinceStartCents,
            expensesSinceStartCents
        )

        val currentSaved = safeAdd(
            goal.startingSavedCents,
            netSavings
        ).coerceAtLeast(0L)

        // Calculate remaining amount.
        val remaining = (
                goal.targetAmountCents - currentSaved
                ).coerceAtLeast(0L)

        // Calculate remaining calendar months.
        val monthsRemaining = remainingCalendarMonths(
            today,
            goal.deadline
        )

        // Calculate required monthly saving.
        val requiredMonthly = when {
            remaining == 0L -> 0L

            monthsRemaining == 0L -> remaining

            else -> {
                // Integer arithmetic: round upwards to the next cent.
                remaining / monthsRemaining +
                        if (remaining % monthsRemaining != 0L) 1L else 0L
            }
        }

        // Calculate percentage progress.
        val progressPercent =
            if (goal.targetAmountCents <= 0L) {
                0
            } else {
                (
                        currentSaved.coerceAtMost(goal.targetAmountCents)
                            .toDouble() /
                                goal.targetAmountCents.toDouble()
                                * 100
                        ).toInt().coerceIn(0, 100)
            }

        // Compare actual monthly savings with required monthly savings.
        val isOnTrack =
            actualMonthlySavingsCents >= requiredMonthly

        return SavingsGoalProgress(
            goal = goal,
            currentSavedCents = currentSaved,
            remainingCents = remaining,
            requiredMonthlySavingCents = requiredMonthly,
            actualMonthlySavingsCents = actualMonthlySavingsCents,
            monthsRemaining = monthsRemaining,
            progressPercent = progressPercent,
            isOnTrack = isOnTrack
        )
    }

    fun remainingCalendarMonths(
        today: LocalDate,
        deadline: LocalDate
    ): Long {

        if (!today.isBefore(deadline)) {
            return 0L
        }

        val currentMonth = YearMonth.from(today)
        val deadlineMonth = YearMonth.from(deadline)

        val months =
            (deadlineMonth.year - currentMonth.year) * 12L +
                    (deadlineMonth.monthValue -
                            currentMonth.monthValue).toLong()

        return months.coerceAtLeast(1L)
    }

    private fun safeAdd(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            if (b >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    private fun safeSubtract(a: Long, b: Long): Long {
        return try {
            Math.subtractExact(a, b)
        } catch (_: ArithmeticException) {
            if (b < 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }
}