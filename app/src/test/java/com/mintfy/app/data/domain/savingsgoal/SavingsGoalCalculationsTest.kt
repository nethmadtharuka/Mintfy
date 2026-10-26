package com.mintfy.app.data.domain.savingsgoal

import org.junit.Assert
import org.junit.Test
import java.time.LocalDate

class SavingsGoalCalculationsTest {

    @Test
    fun calculateRemainingAmount_returnsCorrectAmount() {
        val result = SavingsGoalCalculations.calculateRemainingAmount(
            targetAmountCents = 49_000_000L,
            currentSavedCents = 1_120_000L
        )

        Assert.assertEquals(47_880_000L, result)
    }

    @Test
    fun calculateProgressPercentage_returnsCorrectPercentage() {
        val result = SavingsGoalCalculations.calculateProgressPercentage(
            targetAmountCents = 100_000L,
            currentSavedCents = 25_000L
        )

        Assert.assertEquals(25.0, result, 0.001)
    }

    @Test
    fun calculateProgressPercentage_doesNotExceed100Percent() {
        val result = SavingsGoalCalculations.calculateProgressPercentage(
            targetAmountCents = 100_000L,
            currentSavedCents = 120_000L
        )

        Assert.assertEquals(100.0, result, 0.001)
    }

    @Test
    fun calculateMonthsRemaining_countsTwelveMonths() {
        val result = SavingsGoalCalculations.calculateMonthsRemaining(
            today = LocalDate.of(2026, 10, 26),
            deadline = LocalDate.of(2027, 10, 26)
        )

        Assert.assertEquals(12, result)
    }

    @Test
    fun calculateRequiredMonthlySavings_roundsUp() {
        val result =
            SavingsGoalCalculations.calculateRequiredMonthlySavings(
                remainingAmountCents = 100_001L,
                monthsRemaining = 3
            )

        Assert.assertEquals(33_334L, result)
    }

    @Test
    fun calculateRequiredMonthlySavings_returnsNullAfterDeadline() {
        val result =
            SavingsGoalCalculations.calculateRequiredMonthlySavings(
                remainingAmountCents = 50_000L,
                monthsRemaining = 0
            )

        Assert.assertNull(result)
    }

    @Test
    fun calculateProgress_returnsOnTrackWhenPaceIsMet() {
        val result = SavingsGoalCalculations.calculateProgress(
            targetAmountCents = 49_000_000L,
            currentSavedCents = 1_120_000L,
            deadline = LocalDate.of(2027, 10, 26),
            today = LocalDate.of(2026, 10, 26),
            actualMonthlySavingsCents = 3_990_000L
        )

        Assert.assertEquals(47_880_000L, result.remainingAmountCents)
        Assert.assertEquals(3_990_000L, result.requiredMonthlySavingsCents)
        Assert.assertEquals(GoalProgressStatus.ON_TRACK, result.status)
    }

    @Test
    fun calculateProgress_returnsBehindWhenPaceIsNotMet() {
        val result = SavingsGoalCalculations.calculateProgress(
            targetAmountCents = 49_000_000L,
            currentSavedCents = 1_120_000L,
            deadline = LocalDate.of(2027, 10, 26),
            today = LocalDate.of(2026, 10, 26),
            actualMonthlySavingsCents = 2_000_000L
        )

        Assert.assertEquals(GoalProgressStatus.BEHIND, result.status)
    }

    @Test
    fun calculateProgress_returnsAchievedWhenTargetIsReached() {
        val result = SavingsGoalCalculations.calculateProgress(
            targetAmountCents = 100_000L,
            currentSavedCents = 100_000L,
            deadline = LocalDate.of(2027, 10, 26),
            today = LocalDate.of(2026, 10, 26),
            actualMonthlySavingsCents = 0L
        )

        Assert.assertEquals(0L, result.remainingAmountCents)
        Assert.assertEquals(0L, result.requiredMonthlySavingsCents)
        Assert.assertEquals(GoalProgressStatus.ACHIEVED, result.status)
    }
}