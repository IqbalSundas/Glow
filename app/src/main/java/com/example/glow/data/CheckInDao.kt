package com.example.glow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CheckInDao {
    @Insert
    suspend fun insertCheckIn(checkIn: CheckInEntity)

    @Query("SELECT * FROM check_ins ORDER BY timestamp DESC")
    suspend fun getAllCheckIns(): List<CheckInEntity>

    @Query("UPDATE check_ins SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Int, note: String)

    @Query("UPDATE check_ins SET activitiesPerformed = :activities WHERE id = :id")
    suspend fun updateActivities(id: Int, activities: String)

    // NEW: Get the most recent check-in
    @Query("SELECT * FROM check_ins ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckIn(): CheckInEntity
}