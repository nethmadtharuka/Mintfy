package com.mintfy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavingsGoalEntity::class,
        CachedExpense::class,
        CachedIncomeEntry::class,
        CachedCategory::class,
        CachedPaymentMethod::class,
        CachedIncomeSource::class,
        CachedRecurring::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class MintfyDatabase : RoomDatabase() {

    abstract fun savingsGoalDao(): SavingsGoalDao

    abstract fun cacheDao(): CacheDao
}

/**
 * Version 1 had only the savings goal. Version 2 adds the offline cache tables (FR-G2).
 * The goal table is untouched, so an update never loses the goal saved on the phone.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_expense` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`paymentMethodId` TEXT NOT NULL, `note` TEXT, `recurringId` TEXT, PRIMARY KEY(`uid`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_income_entry` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`sourceId` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, `currency` TEXT NOT NULL, `originalAmountCents` INTEGER NOT NULL, " +
                "`exchangeRate` TEXT NOT NULL, `lkrAmountCents` INTEGER NOT NULL, `baseAmountCents` INTEGER, " +
                "`bonusAmountCents` INTEGER, `client` TEXT, `project` TEXT, `milestone` TEXT, " +
                "`dueDateEpochDay` INTEGER, `note` TEXT, PRIMARY KEY(`uid`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_category` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `spendingType` TEXT NOT NULL, `icon` TEXT, " +
                "`builtIn` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`uid`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_payment_method` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`archived` INTEGER NOT NULL, PRIMARY KEY(`uid`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_income_source` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`defaultCurrency` TEXT NOT NULL, `expectedAmountCents` INTEGER, `archived` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uid`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_recurring` (`uid` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`paymentMethodId` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `frequency` TEXT NOT NULL, " +
                "`nextDueEpochDay` INTEGER NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`uid`, `id`))"
        )
    }
}
