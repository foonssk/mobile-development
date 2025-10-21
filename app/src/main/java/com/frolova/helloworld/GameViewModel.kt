package com.frolova.helloworld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val scoreDao = database.scoreDao()

    var settings = Settings(
        gameSpeed = 1,
        maxInsects = 10,
        bonusInterval = 5,
        roundDuration = 60
    )
    var playerDifficulty = 1
    var currentPlayer: PlayerEntity? = null

    fun savePlayer(player: PlayerEntity) {
        viewModelScope.launch {
            scoreDao.insertPlayer(player)
            currentPlayer = player
        }
    }

    fun saveScore(score: Int, misses: Int) {
        currentPlayer?.let { player ->
            viewModelScope.launch {
                val bestScore = scoreDao.getBestScore(player.id) ?: 0
                if (score > bestScore) {
                    scoreDao.insertScore(
                        ScoreEntity(
                            playerId = player.id,
                            score = score,
                            misses = misses,
                            difficulty = playerDifficulty
                        )
                    )
                }
            }
        }
    }

    fun getAllScores() = scoreDao.getAllScoresWithPlayerInfo()

    fun getAllPlayers() = scoreDao.getAllPlayers()
}