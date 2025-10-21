package com.frolova.helloworld

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Insert
    suspend fun insertScore(score: ScoreEntity)

    @Query("SELECT MAX(score) FROM scores WHERE playerId = :playerId")
    suspend fun getBestScore(playerId: Long): Int?

    @Query("SELECT * FROM players ORDER BY fullName")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    @Transaction
    @Query("SELECT * FROM scores ORDER BY score DESC, timestamp DESC LIMIT 3")
    fun getAllScoresWithPlayerInfo(): Flow<List<ScoreWithPlayer>>
}


data class ScoreWithPlayer(
    @Embedded val score: ScoreEntity,
    @Relation(
        parentColumn = "playerId",
        entityColumn = "id"
    )
    val player: PlayerEntity
)