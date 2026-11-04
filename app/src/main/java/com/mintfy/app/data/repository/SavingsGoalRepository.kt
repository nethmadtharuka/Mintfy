
package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mintfy.app.data.local.SavingsGoalDao
import com.mintfy.app.data.local.SavingsGoalEntity
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsGoalRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dao: SavingsGoalDao
) {

    companion object {
        private const val TAG = "SavingsGoalRepository"
    }

    private fun goalDocument(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document("active")

    fun observeActiveGoal(): Flow<SavingsGoal?> = channelFlow {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            close(
                IllegalStateException(
                    "You're signed out. Please sign in again."
                )
            )
            return@channelFlow
        }

        val syncJob = SupervisorJob()

        val syncScope = CoroutineScope(
            syncJob + Dispatchers.IO
        )

        val registration = goalDocument(uid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e(TAG, "Firestore listener failed", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                syncScope.launch {

                    if (snapshot.exists()) {

                        val goal = snapshot.toSavingsGoal()

                        if (goal != null) {
                            dao.upsert(
                                SavingsGoalEntity.from(goal, uid)
                            )
                        }

                    } else if (!snapshot.metadata.isFromCache) {

                        dao.deleteActiveGoal(uid)
                    }
                }
            }

        val roomJob = launch {
            dao.observeActiveGoal(uid).collect { entity ->
                send(entity?.toModel())
            }
        }

        awaitClose {
            registration.remove()
            roomJob.cancel()
            syncJob.cancel()
        }
    }

    suspend fun createGoal(
        goal: SavingsGoal
    ): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        validateGoal(goal)

        val cachedGoal = dao.getActiveGoal(uid)

        require(cachedGoal == null) {
            "An active savings goal already exists."
        }

        dao.upsert(
            SavingsGoalEntity.from(goal, uid)
        )

        val data = mapOf(
            "name" to goal.name.trim(),
            "targetAmountCents" to goal.targetAmountCents,
            "startingSavedCents" to goal.startingSavedCents,
            "startDate" to goal.startDate.toTimestamp(),
            "deadline" to goal.deadline.toTimestamp(),
            "status" to goal.status.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        goalDocument(uid)
            .set(data)
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "Savings goal synchronization failed",
                    error
                )
            }
    }

    suspend fun updateGoal(
        goal: SavingsGoal
    ): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        validateGoal(goal)

        val existingGoal = dao.getActiveGoal(uid)

        require(existingGoal != null) {
            "Savings goal does not exist."
        }

        dao.upsert(
            SavingsGoalEntity.from(goal, uid)
        )

        val data = mapOf(
            "name" to goal.name.trim(),
            "targetAmountCents" to goal.targetAmountCents,
            "startingSavedCents" to goal.startingSavedCents,
            "startDate" to goal.startDate.toTimestamp(),
            "deadline" to goal.deadline.toTimestamp(),
            "status" to goal.status.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        goalDocument(uid)
            .update(data)
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "Savings goal update synchronization failed",
                    error
                )
            }
    }

    suspend fun deleteGoal(): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        val existingGoal = dao.getActiveGoal(uid)

        require(existingGoal != null) {
            "Savings goal does not exist."
        }

        dao.deleteActiveGoal(uid)

        goalDocument(uid)
            .delete()
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "Savings goal deletion synchronization failed",
                    error
                )
            }
    }

    suspend fun retrySync(): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        goalDocument(uid)
            .get(Source.SERVER)
            .await()
    }

    private fun validateGoal(goal: SavingsGoal) {

        require(goal.name.isNotBlank()) {
            "Goal name cannot be empty."
        }

        require(goal.name.trim().length <= 60) {
            "Goal name cannot exceed 60 characters."
        }

        require(goal.targetAmountCents > 0L) {
            "Target amount must be greater than zero."
        }

        require(goal.startingSavedCents >= 0L) {
            "Starting saved amount cannot be negative."
        }

        require(goal.deadline.isAfter(goal.startDate)) {
            "Deadline must be after start date."
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot
            .toSavingsGoal(): SavingsGoal? {

        val startDate = getTimestamp("startDate")
            ?.toDate()
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: return null

        val deadline = getTimestamp("deadline")
            ?.toDate()
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: return null

        return SavingsGoal(
            id = "active",
            name = getString("name") ?: return null,
            targetAmountCents = getLong("targetAmountCents")
                ?: return null,
            startingSavedCents = getLong("startingSavedCents")
                ?: return null,
            startDate = startDate,
            deadline = deadline,
            status = getString("status")
                ?.let {
                    runCatching {
                        SavingsGoalStatus.valueOf(it)
                    }.getOrNull()
                }
                ?: SavingsGoalStatus.ACTIVE
        )
    }

    private fun LocalDate.toTimestamp(): Timestamp {

        val instant = atStartOfDay(
            ZoneId.systemDefault()
        ).toInstant()

        return Timestamp(Date.from(instant))
    }
}
