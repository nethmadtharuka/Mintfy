package com.mintfy.app.ui.screens.goal

import com.mintfy.app.data.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsGoalCalculatorTest {

    private val goal = SavingsGoal(
        name = "MacBook Fund",
        targetAmountCents = 49_000_000L,
        startingSavedCents = 1_120_000L,
        startDate = LocalDate.of(2026, 11, 4),
        deadline = LocalDate.of(2027, 11, 4)
    )

    @Test
    fun calculateRequiredMonthlySaving() {

        val result = SavingsGoalCalculator.calculate(
            goal = goal,
            receivedIncomeSinceStartCents = 0L,
            expensesSinceStartCents = 0L,
            actualMonthlySavingsCents = 3_990_000L,
            today = LocalDate.of(2026, 11, 4)
        )

        assertEquals(47880000L, result.remainingCents)
        assertEquals(12L, result.monthsRemaining)
        assertEquals(3990000L, result.requiredMonthlySavingCents)
        assertTrue(result.isOnTrack)
    }

    @Test
    fun expensesReduceCurrentSavedAmount() {

        val result = SavingsGoalCalculator.calculate(
            goal = goal,
            receivedIncomeSinceStartCents = 500_000L,
            expensesSinceStartCents = 100_000L,
            actualMonthlySavingsCents = 400_000L,
            today = LocalDate.of(2026, 12, 4)
        )

        assertEquals(1_520_000L, result.currentSavedCents)
    }

    @Test
    fun savedAmountCannotBecomeNegative() {

        val result = SavingsGoalCalculator.calculate(
            goal = goal,
            receivedIncomeSinceStartCents = 0L,
            expensesSinceStartCents = 5_000_000L,
            actualMonthlySavingsCents = -5_000_000L,
            today = LocalDate.of(2026, 12, 4)
        )

        assertEquals(0L, result.currentSavedCents)
    }

    @Test
    fun behindWhenActualSavingIsLessThanRequired() {

        val result = SavingsGoalCalculator.calculate(
            goal = goal,
            receivedIncomeSinceStartCents = 0L,
            expensesSinceStartCents = 0L,
            actualMonthlySavingsCents = 2_000_000L,
            today = LocalDate.of(2026, 11, 4)
        )

        assertFalse(result.isOnTrack)
    }

    @Test
    fun completedGoalHasZeroRemainingAmount() {

        val completedGoal = goal.copy(
            startingSavedCents = 49_000_000L
        )

        val result = SavingsGoalCalculator.calculate(
            goal = completedGoal,
            receivedIncomeSinceStartCents = 0L,
            expensesSinceStartCents = 0L,
            actualMonthlySavingsCents = 0L,
            today = LocalDate.of(2026, 11, 4)
        )

        assertEquals(0L, result.remainingCents)
        assertEquals(0L, result.requiredMonthlySavingCents)
        assertEquals(100, result.progressPercent)
        assertTrue(result.isOnTrack)
    }
}