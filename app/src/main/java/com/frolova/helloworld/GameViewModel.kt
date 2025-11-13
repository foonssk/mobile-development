package com.frolova.helloworld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val scoreDao = database.scoreDao()
    private val goldRateService = CbrApiService(application)

    var settings = Settings(
        gameSpeed = 1,
        maxInsects = 10,
        bonusInterval = 5,
        roundDuration = 60
    )
    var playerDifficulty = 1
    var currentPlayer: PlayerEntity? = null

    // Новое состояние для курса золота
    private val _goldRate = MutableStateFlow<GoldRate?>(null)
    val goldRate: StateFlow<GoldRate?> = _goldRate

    // Новое состояние для виджета
    private val _widgetVisible = MutableStateFlow(true)
    val widgetVisible: StateFlow<Boolean> = _widgetVisible

    init {
        loadGoldRate()
    }

    fun loadGoldRate() {
        viewModelScope.launch {
            val result = goldRateService.fetchGoldRate()
            if (result.isSuccess) {
                _goldRate.value = result.getOrNull()
                GoldRateWidget.updateAllWidgets(getApplication())
            } else {
                _goldRate.value = GoldRate(7500.0, "Золото")
            }
        }
    }

    fun toggleWidget() {
        _widgetVisible.value = !_widgetVisible.value
    }

    fun getCurrentGoldRate(): GoldRate {
        return goldRateService.getCachedGoldRate()
    }

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