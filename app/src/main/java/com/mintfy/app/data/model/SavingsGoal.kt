package com.mintfy.app.data.model

import java.time.LocalDate

enum class SavingsGoalStatus {
    ACTIVE,
    ACHIEVED,
    ARCHIVED
}

data class SavingsGoal(
    val id: String = "active",
    val name: String,
    val targetAmountCents: Long,
    val startingSavedCents: Long,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val status: SavingsGoalStatus = SavingsGoalStatus.ACTIVE
)

data class SavingsGoalProgress(
    val goal: SavingsGoal,
    val currentSavedCents: Long,
    val remainingCents: Long,
    val requiredMonthlySavingCents: Long,
    val actualMonthlySavingsCents: Long,
    val monthsRemaining: Long,
    val progressPercent: Int,
    val isOnTrack: Boolean
)