package com.mintfy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavingsGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MintfyDatabase : RoomDatabase() {

    abstract fun savingsGoalDao(): SavingsGoalDao
}