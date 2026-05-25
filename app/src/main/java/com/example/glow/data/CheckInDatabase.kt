package com.example.glow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CheckInEntity::class], version = 2, exportSchema = false) // CHANGED version to 2
abstract class CheckInDatabase : RoomDatabase() {

    abstract fun checkInDao(): CheckInDao

    companion object {
        @Volatile
        private var INSTANCE: CheckInDatabase? = null

        fun getDatabase(context: Context): CheckInDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CheckInDatabase::class.java,
                    "glow_check_in_database"
                )
                    .fallbackToDestructiveMigration() // NEW: Auto-wipes and rebuilds if version changes!
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}