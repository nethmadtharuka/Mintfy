package com.mintfy.app.di

import android.content.Context
import androidx.room.Room
import com.mintfy.app.data.local.MintfyDatabase
import com.mintfy.app.data.local.SavingsGoalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMintfyDatabase(
        @ApplicationContext context: Context
    ): MintfyDatabase {

        return Room.databaseBuilder(
            context,
            MintfyDatabase::class.java,
            "mintfy.db"
        ).build()
    }

    @Provides
    fun provideSavingsGoalDao(
        database: MintfyDatabase
    ): SavingsGoalDao {

        return database.savingsGoalDao()
    }
}