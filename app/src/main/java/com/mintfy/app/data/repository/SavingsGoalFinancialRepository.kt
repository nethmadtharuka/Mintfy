
package com.mintfy.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class FinancialTransaction(
    val date: LocalDate,
    val amountCents: Long,
)

data class SavingsGoalFinancialData(
    val receivedIncome: List<FinancialTransaction> = emptyList(),
    val expenses: List<FinancialTransaction> = emptyList(),
)

@Singleton
class SavingsGoalFinancialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    /**
     * Observes received income and recorded expenses for the signed-in user.
     *
     * Income with EXPECTED or INVOICED status is excluded.
     * Firestore snapshot listeners also observe local changes and sync updates.
     */
    fun observeFinancialData(): Flow<SavingsGoalFinancialData> = callbackFlow {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            close(signedOut())
            return@callbackFlow
        }

        var receivedIncome: List<FinancialTransaction>? = null
        var expenses: List<FinancialTransaction>? = null

        fun emitWhenReady() {
            val income = receivedIncome ?: return
            val expenseList = expenses ?: return

            trySend(
                SavingsGoalFinancialData(
                    receivedIncome = income,
                    expenses = expenseList,
                )
            )
        }

        val incomeRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("incomeEntries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                receivedIncome = snapshot.documents.mapNotNull { document ->
                    document.toReceivedIncomeTransaction()
                }

                emitWhenReady()
            }

        val expenseRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryException())
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                expenses = snapshot.documents.mapNotNull { document ->
                    document.toExpenseTransaction()
                }

                emitWhenReady()
            }

        awaitClose {
            incomeRegistration.remove()
            expenseRegistration.remove()
        }
    }

    private fun DocumentSnapshot.toReceivedIncomeTransaction():
            FinancialTransaction? {

        if (getString("status") != "RECEIVED") {
            return null
        }

        val timestamp = getTimestamp("date") ?: return null
        val amountCents = getLong("lkrAmountCents") ?: return null

        if (amountCents < 0L) return null

        return FinancialTransaction(
            date = timestamp.toLocalDate(),
            amountCents = amountCents,
        )
    }

    private fun DocumentSnapshot.toExpenseTransaction():
            FinancialTransaction? {

        val timestamp = getTimestamp("date") ?: return null
        val amountCents = getLong("amountCents") ?: return null

        if (amountCents < 0L) return null

        return FinancialTransaction(
            date = timestamp.toLocalDate(),
            amountCents = amountCents,
        )
    }

    private fun Timestamp.toLocalDate(): LocalDate =
        Instant.ofEpochSecond(seconds, nanoseconds.toLong())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
}
