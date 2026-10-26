package com.mintfy.app.data.model

import com.google.firebase.Timestamp
import java.time.LocalDate

/**
 * Represents a savings goal owned by one Mintfy user.
 * Monetary values use Long integer cents, following the existing Mintfy income and expense models.
 * Firestore dates will be converted to/from Timestamp by the repository.
 */
data class SavingsGoal(
    val id: String = "",
    val name: String,
    val targetAmountCents: Long,
    val startingSavedCents: Long,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val status: SavingsGoalStatus = SavingsGoalStatus.ACTIVE,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Lifecycle status stored for a savings goal.
 */
enum class SavingsGoalStatus {
    ACTIVE,
    ACHIEVED,
    ARCHIVED
}
