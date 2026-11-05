package com.mintfy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Reads and refreshes the local copy of the user's data (FR-G2).
 * Each "replace" swaps exactly the rows a Firestore listener covers (a date range, or a whole
 * list) in one transaction, so documents deleted on another device disappear here too and a
 * screen never reads a half-written copy.
 */
@Dao
abstract class CacheDao {

    // ---------- Expenses: one date range at a time ----------

    @Query(
        """
        SELECT * FROM cached_expense
        WHERE uid = :uid AND dateMillis >= :fromMillis AND dateMillis < :toMillis
        ORDER BY dateMillis DESC
        """
    )
    abstract suspend fun expenses(uid: String, fromMillis: Long, toMillis: Long): List<CachedExpense>

    @Query("DELETE FROM cached_expense WHERE uid = :uid AND dateMillis >= :fromMillis AND dateMillis < :toMillis")
    protected abstract suspend fun deleteExpenses(uid: String, fromMillis: Long, toMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertExpenses(rows: List<CachedExpense>)

    @Transaction
    open suspend fun replaceExpenses(uid: String, fromMillis: Long, toMillis: Long, rows: List<CachedExpense>) {
        deleteExpenses(uid, fromMillis, toMillis)
        insertExpenses(rows)
    }

    // ---------- Income entries: a date range, or every freelance entry ----------

    @Query(
        """
        SELECT * FROM cached_income_entry
        WHERE uid = :uid AND dateEpochDay >= :fromDay AND dateEpochDay <= :toDay
        ORDER BY dateEpochDay DESC
        """
    )
    abstract suspend fun incomeEntries(uid: String, fromDay: Long, toDay: Long): List<CachedIncomeEntry>

    @Query("DELETE FROM cached_income_entry WHERE uid = :uid AND dateEpochDay >= :fromDay AND dateEpochDay <= :toDay")
    protected abstract suspend fun deleteIncomeEntries(uid: String, fromDay: Long, toDay: Long)

    @Query("SELECT * FROM cached_income_entry WHERE uid = :uid AND sourceType = :sourceType ORDER BY dateEpochDay DESC")
    abstract suspend fun incomeEntriesOfType(uid: String, sourceType: String): List<CachedIncomeEntry>

    @Query("DELETE FROM cached_income_entry WHERE uid = :uid AND sourceType = :sourceType")
    protected abstract suspend fun deleteIncomeEntriesOfType(uid: String, sourceType: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertIncomeEntries(rows: List<CachedIncomeEntry>)

    @Transaction
    open suspend fun replaceIncomeEntries(uid: String, fromDay: Long, toDay: Long, rows: List<CachedIncomeEntry>) {
        deleteIncomeEntries(uid, fromDay, toDay)
        insertIncomeEntries(rows)
    }

    @Transaction
    open suspend fun replaceIncomeEntriesOfType(uid: String, sourceType: String, rows: List<CachedIncomeEntry>) {
        deleteIncomeEntriesOfType(uid, sourceType)
        insertIncomeEntries(rows)
    }

    // ---------- Reference lists: replaced whole ----------

    @Query("SELECT * FROM cached_category WHERE uid = :uid ORDER BY position")
    abstract suspend fun categories(uid: String): List<CachedCategory>

    @Query("DELETE FROM cached_category WHERE uid = :uid")
    protected abstract suspend fun deleteCategories(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCategories(rows: List<CachedCategory>)

    @Transaction
    open suspend fun replaceCategories(uid: String, rows: List<CachedCategory>) {
        deleteCategories(uid)
        insertCategories(rows)
    }

    @Query("SELECT * FROM cached_payment_method WHERE uid = :uid ORDER BY position")
    abstract suspend fun paymentMethods(uid: String): List<CachedPaymentMethod>

    @Query("DELETE FROM cached_payment_method WHERE uid = :uid")
    protected abstract suspend fun deletePaymentMethods(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPaymentMethods(rows: List<CachedPaymentMethod>)

    @Transaction
    open suspend fun replacePaymentMethods(uid: String, rows: List<CachedPaymentMethod>) {
        deletePaymentMethods(uid)
        insertPaymentMethods(rows)
    }

    @Query("SELECT * FROM cached_income_source WHERE uid = :uid ORDER BY position")
    abstract suspend fun incomeSources(uid: String): List<CachedIncomeSource>

    @Query("DELETE FROM cached_income_source WHERE uid = :uid")
    protected abstract suspend fun deleteIncomeSources(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertIncomeSources(rows: List<CachedIncomeSource>)

    @Transaction
    open suspend fun replaceIncomeSources(uid: String, rows: List<CachedIncomeSource>) {
        deleteIncomeSources(uid)
        insertIncomeSources(rows)
    }

    @Query("SELECT * FROM cached_recurring WHERE uid = :uid ORDER BY position")
    abstract suspend fun recurring(uid: String): List<CachedRecurring>

    @Query("DELETE FROM cached_recurring WHERE uid = :uid")
    protected abstract suspend fun deleteRecurring(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertRecurring(rows: List<CachedRecurring>)

    @Transaction
    open suspend fun replaceRecurring(uid: String, rows: List<CachedRecurring>) {
        deleteRecurring(uid)
        insertRecurring(rows)
    }

    // ---------- Sign-out ----------

    @Query("DELETE FROM cached_expense")
    protected abstract suspend fun clearExpenses()

    @Query("DELETE FROM cached_income_entry")
    protected abstract suspend fun clearIncomeEntries()

    @Query("DELETE FROM cached_category")
    protected abstract suspend fun clearCategories()

    @Query("DELETE FROM cached_payment_method")
    protected abstract suspend fun clearPaymentMethods()

    @Query("DELETE FROM cached_income_source")
    protected abstract suspend fun clearIncomeSources()

    @Query("DELETE FROM cached_recurring")
    protected abstract suspend fun clearRecurring()

    /** Removes every cached document, so nothing of the account stays on the phone after sign-out. */
    @Transaction
    open suspend fun clearAll() {
        clearExpenses()
        clearIncomeEntries()
        clearCategories()
        clearPaymentMethods()
        clearIncomeSources()
        clearRecurring()
    }
}
