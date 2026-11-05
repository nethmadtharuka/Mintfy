
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

    /**
     * Creates the goal (FR-E1). The rules allow one document, "active", and only as a create, so
     * Firestore itself is asked whether a goal already exists (the server when online, its cache
     * offline); the Room copy alone can be empty after a reinstall while the server still has one.
     * The goal shows at once from Room. If the server rejects it, the Room copy is removed again and
     * the error is shown, so a goal never seems saved and then silently disappears.
     */
    suspend fun createGoal(goal: SavingsGoal): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        runCatching { validateGoal(goal) }.onFailure { return Result.failure(it) }

        val onServer = runCatching { goalDocument(uid).get().await() }.getOrNull() // offline and not cached: unknown
        if (onServer?.exists() == true || dao.getActiveGoal(uid) != null) {
            return Result.failure(RepositoryException("You already have a savings goal. Edit it instead."))
        }

        val data = goal.toFields() + mapOf(
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        dao.upsert(SavingsGoalEntity.from(goal, uid))
        return goalDocument(uid).createAndConfirm(data) { it.toSaveError("savings goal") }
            .onFailure { dao.deleteActiveGoal(uid) }
            .map { }
    }

    /** Changes the goal. createdAt is left alone, as the rules require. Undone locally if rejected. */
    suspend fun updateGoal(goal: SavingsGoal): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        runCatching { validateGoal(goal) }.onFailure { return Result.failure(it) }
        val previous = dao.getActiveGoal(uid)
            ?: return Result.failure(RepositoryException("This savings goal no longer exists."))

        val data = goal.toFields() + ("updatedAt" to FieldValue.serverTimestamp())
        dao.upsert(SavingsGoalEntity.from(goal, uid))
        return goalDocument(uid).confirmWrite({ it.toSaveError("savings goal") }) { update(data) }
            .onFailure { dao.upsert(previous) }
            .map { }
    }

    /** Deletes the goal; the screen asks for confirmation first. Restored locally if rejected. */
    suspend fun deleteGoal(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(signedOut())
        val previous = dao.getActiveGoal(uid)
            ?: return Result.failure(RepositoryException("This savings goal no longer exists."))

        dao.deleteActiveGoal(uid)
        return goalDocument(uid).confirmWrite({ it.toSaveError("savings goal") }) { delete() }
            .onFailure { dao.upsert(previous) }
            .map { }
    }

    /** The fields every goal write sends, named as in firestore.rules (users/{uid}/goals). */
    private fun SavingsGoal.toFields(): Map<String, Any> = mapOf(
        "name" to name.trim(),
        "targetAmountCents" to targetAmountCents,
        "startingSavedCents" to startingSavedCents,
        "startDate" to startDate.toTimestamp(),
        "deadline" to deadline.toTimestamp(),
        "status" to status.name,
    )

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
