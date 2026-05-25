package com.example.glow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val state: String,
    val responseTime: Double,
    var note: String = "",
    var activitiesPerformed: String = "" // NEW: Stores activities like "Breathing|Stretching"
)