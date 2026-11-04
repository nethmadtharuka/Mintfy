package com.mintfy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query(
        """
        SELECT * FROM savings_goal
        WHERE ownerUid = :ownerUid
        LIMIT 1
        """
    )
    fun observeActiveGoal(
        ownerUid: String
    ): Flow<SavingsGoalEntity?>

    @Query(
        """
        SELECT * FROM savings_goal
        WHERE ownerUid = :ownerUid
        LIMIT 1
        """
    )
    suspend fun getActiveGoal(
        ownerUid: String
    ): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        goal: SavingsGoalEntity
    )

    @Query(
        """
        DELETE FROM savings_goal
        WHERE ownerUid = :ownerUid
        """
    )
    suspend fun deleteActiveGoal(
        ownerUid: String
    )
}