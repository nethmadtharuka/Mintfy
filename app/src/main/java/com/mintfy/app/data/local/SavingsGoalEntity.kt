package com.mintfy.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalStatus
import java.time.LocalDate

@Entity(tableName = "savings_goal")
data class SavingsGoalEntity(

    @PrimaryKey
    val id: String,

    val ownerUid: String,

    val name: String,

    val targetAmountCents: Long,

    val startingSavedCents: Long,

    val startDateEpochDay: Long,

    val deadlineEpochDay: Long,

    val status: String
) {

    fun toModel(): SavingsGoal {

        return SavingsGoal(
            id = "active",
            name = name,
            targetAmountCents = targetAmountCents,
            startingSavedCents = startingSavedCents,
            startDate = LocalDate.ofEpochDay(startDateEpochDay),
            deadline = LocalDate.ofEpochDay(deadlineEpochDay),
            status = runCatching {
                SavingsGoalStatus.valueOf(status)
            }.getOrDefault(SavingsGoalStatus.ACTIVE)
        )
    }

    companion object {

        fun from(
            goal: SavingsGoal,
            ownerUid: String
        ): SavingsGoalEntity {

            return SavingsGoalEntity(
                id = "${ownerUid}_active",
                ownerUid = ownerUid,
                name = goal.name,
                targetAmountCents = goal.targetAmountCents,
                startingSavedCents = goal.startingSavedCents,
                startDateEpochDay = goal.startDate.toEpochDay(),
                deadlineEpochDay = goal.deadline.toEpochDay(),
                status = goal.status.name
            )
        }
    }
}