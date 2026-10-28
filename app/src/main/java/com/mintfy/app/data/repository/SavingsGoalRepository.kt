
package com.mintfy.app.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mintfy.app.data.model.SavingsGoal
import com.mintfy.app.data.model.SavingsGoalStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsGoalRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /**
     * Observes the signed-in user's savings goals.
     * Firestore snapshot listeners use the local cache and update the
     * flow when local writes or remote changes become visible.
     */

    fun observeGoals(): Flow<List<SavingsGoal>> = callbackFlow {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }

        val registration = goals(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toRepositoryException())
                return@addSnapshotListener
            }

            val result = snapshot?.documents
                .orEmpty()
                .mapNotNull { it.toSavingsGoal() }
                .sortedBy { it.deadline }

            trySend(result)
        }

        awaitClose { registration.remove() }
    }

    /**
     * Creates a new goal.--->
     * The document ID is generated locally, allowing Firestore to queue
     * the write when offline. A later server rejection is logged.
     */
    fun addGoal(goal: SavingsGoal): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(signedOut())

        val validationError = validateGoal(goal)
        if (validationError != null) {
            return Result.failure(RepositoryException(validationError))
        }

        return try {
            val document = goals(uid).document()

            document.set(goal.toNewDocument())
                .addOnFailureListener {
                    Log.w(TAG, "Savings goal ${document.id} was rejected", it)
                }

            Result.success(document.id)
        } catch (e: Exception) {
            Result.failure(e.toRepositoryException())
        }
    }

    /**
     * Updates an existing goal without changing createdAt.
     */
    fun updateGoal(goal: SavingsGoal): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(signedOut())

        if (goal.id.isBlank()) {
            return Result.failure(
                RepositoryException("The savings goal ID is missing.")
            )
        }

        val validationError = validateGoal(goal)
        if (validationError != null) {
            return Result.failure(RepositoryException(validationError))
        }

        return try {
            goals(uid).document(goal.id)
                .update(
                    goal.toUpdateDocument()
                )
                .addOnFailureListener {
                    Log.w(TAG, "Savings goal ${goal.id} update was rejected", it)
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toRepositoryException())
        }
    }

    /**
     * Deletes a goal belonging to the signed-in user.
     */
    fun deleteGoal(goalId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(signedOut())

        if (goalId.isBlank()) {
            return Result.failure(
                RepositoryException("The savings goal ID is missing.")
            )
        }

        return try {
            goals(uid).document(goalId)
                .delete()
                .addOnFailureListener {
                    Log.w(TAG, "Savings goal $goalId deletion was rejected", it)
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toRepositoryException())
        }
    }

    private fun goals(uid: String) =
        firestore.collection(USERS)
            .document(uid)
            .collection(GOALS)

    /**
     * Fields must match firestore.rules exactly.
     */
    private fun SavingsGoal.toNewDocument(): Map<String, Any> = mapOf(
        NAME to name.trim(),
        TARGET_AMOUNT_CENTS to targetAmountCents,
        STARTING_SAVED_CENTS to startingSavedCents,
        START_DATE to startDate.toTimestamp(),
        DEADLINE to deadline.toTimestamp(),
        STATUS to status.name,
        CREATED_AT to FieldValue.serverTimestamp(),
        UPDATED_AT to FieldValue.serverTimestamp(),
    )

    /**
     * createdAt must remain unchanged when a goal is updated.
     */
    private fun SavingsGoal.toUpdateDocument(): Map<String, Any> = mapOf(
        NAME to name.trim(),
        TARGET_AMOUNT_CENTS to targetAmountCents,
        STARTING_SAVED_CENTS to startingSavedCents,
        START_DATE to startDate.toTimestamp(),
        DEADLINE to deadline.toTimestamp(),
        STATUS to status.name,
        UPDATED_AT to FieldValue.serverTimestamp(),
    )

    /**
     * Returns null if the document can be converted to a goal.
     * Malformed documents are ignored rather than crashing the list.
     */
    private fun DocumentSnapshot.toSavingsGoal(): SavingsGoal? {
        val goalName = getString(NAME) ?: return null
        val target = getLong(TARGET_AMOUNT_CENTS) ?: return null
        val startingSaved = getLong(STARTING_SAVED_CENTS) ?: return null
        val startTimestamp = getTimestamp(START_DATE) ?: return null
        val deadlineTimestamp = getTimestamp(DEADLINE) ?: return null

        val goalStatus = getString(STATUS)
            ?.let { runCatching { SavingsGoalStatus.valueOf(it) }.getOrNull() }
            ?: return null

        return runCatching {
            SavingsGoal(
                id = id,
                name = goalName,
                targetAmountCents = target,
                startingSavedCents = startingSaved,
                startDate = startTimestamp.toLocalDate(),
                deadline = deadlineTimestamp.toLocalDate(),
                status = goalStatus,
                createdAt = getTimestamp(CREATED_AT),
                updatedAt = getTimestamp(UPDATED_AT),
            )
        }.getOrNull()
    }

    /**
     * Client-side validation complements the Firestore security rules.
     */
    private fun validateGoal(goal: SavingsGoal): String? {
        if (goal.name.trim().isEmpty()) {
            return "Enter a name for your savings goal."
        }

        if (goal.name.trim().length > 60) {
            return "The goal name cannot exceed 60 characters."
        }

        if (goal.targetAmountCents <= 0L) {
            return "The target amount must be greater than zero."
        }

        if (goal.startingSavedCents < 0L) {
            return "The starting saved amount cannot be negative."
        }

        if (!goal.deadline.isAfter(goal.startDate)) {
            return "The deadline must be after the start date."
        }

        return null
    }

    private fun LocalDate.toTimestamp(): Timestamp {
        val zone = ZoneId.systemDefault()
        return Timestamp(
            java.util.Date.from(
                atTime(java.time.LocalTime.NOON)
                    .atZone(zone)
                    .toInstant()
            )
        )
    }

    private fun Timestamp.toLocalDate(): LocalDate =
        toDate().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    private companion object {
        const val TAG = "SavingsGoalRepository"

        const val USERS = "users"
        const val GOALS = "goals"

        const val NAME = "name"
        const val TARGET_AMOUNT_CENTS = "targetAmountCents"
        const val STARTING_SAVED_CENTS = "startingSavedCents"
        const val START_DATE = "startDate"
        const val DEADLINE = "deadline"
        const val STATUS = "status"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }
}
