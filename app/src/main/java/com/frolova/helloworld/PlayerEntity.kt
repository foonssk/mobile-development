package com.frolova.helloworld

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val fullName: String,
    val gender: String,
    val course: String,
    val birthDate: Long,
    val zodiacSign: String,
    val difficulty: Int //
)