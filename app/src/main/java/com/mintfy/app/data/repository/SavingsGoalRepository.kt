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
import kotlinx.coroutines.Job
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

    // ------------------------------------------------
    // FIRESTORE REFERENCE
    // ------------------------------------------------

    private fun goalDocument(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document("active")


    // ------------------------------------------------
    // OBSERVE SAVINGS GOAL
    // ------------------------------------------------

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

        // Listen to Firestore changes.

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
                                SavingsGoalEntity.from(
                                    goal,
                                    uid
                                )
                            )
                        }

                    } else if (!snapshot.metadata.isFromCache) {

                        // Delete cached goal only after a confirmed
                        // server snapshot says the document is absent.

                        dao.deleteActiveGoal(uid)
                    }
                }
            }

        // Observe Room changes.

        val roomJob = launch {

            dao.observeActiveGoal(uid).collect { entity ->

                send(
                    entity?.toModel()
                )
            }
        }

        awaitClose {

            registration.remove()

            roomJob.cancel()

            syncJob.cancel()
        }
    }


    // ------------------------------------------------
    // CREATE SAVINGS GOAL
    // ------------------------------------------------

    suspend fun createGoal(
        goal: SavingsGoal
    ): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        require(goal.name.isNotBlank()) {
            "Goal name cannot be empty."
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

        // Prevent creating another goal when one is already cached.

        val cachedGoal = dao.getActiveGoal(uid)

        require(cachedGoal == null) {
            "An active savings goal already exists."
        }

        // Save locally first.

        dao.upsert(
            SavingsGoalEntity.from(goal, uid)
        )

        // Save to Firestore.

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


    // ------------------------------------------------
    // RETRY SYNCHRONIZATION
    // ------------------------------------------------

    suspend fun retrySync(): Result<Unit> = runCatching {

        val uid = auth.currentUser?.uid
            ?: error("You're signed out. Please sign in again.")

        goalDocument(uid)
            .get(Source.SERVER)
            .await()

    }


    // ------------------------------------------------
    // FIRESTORE DOCUMENT TO MODEL
    // ------------------------------------------------

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

            name = getString("name")
                ?: return null,

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


    // ------------------------------------------------
    // LOCALDATE TO FIREBASE TIMESTAMP
    // ------------------------------------------------

    private fun LocalDate.toTimestamp(): Timestamp {

        val instant = atStartOfDay(
            ZoneId.systemDefault()
        ).toInstant()

        return Timestamp(
            Date.from(instant)
        )
    }
}