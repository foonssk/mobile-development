package com.frolova.helloworld

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scores",
    foreignKeys = [
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playerId")]
)
data class ScoreEntity(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val playerId: Long,
    val score: Int,
    val misses: Int,
    val difficulty: Int,
    val timestamp: Long = System.currentTimeMillis()
)