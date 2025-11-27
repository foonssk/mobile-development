package com.frolova.helloworld

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val scoreDao = database.scoreDao()
    private val goldRateService = CbrApiService(application)

    // Используем SharedPreferences для сохранения настроек
    private val sharedPreferences = application.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

    // Загружаем настройки при инициализации
    var settings = Settings(
        gameSpeed = sharedPreferences.getInt("game_speed", 1),
        maxInsects = sharedPreferences.getInt("max_insects", 10),
        bonusInterval = sharedPreferences.getInt("bonus_interval", 5),
        roundDuration = sharedPreferences.getInt("round_duration", 60)
    )

    var playerDifficulty = sharedPreferences.getInt("player_difficulty", 1)
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

    // Сохраняем настройки при изменении
    fun saveSettings() {
        with(sharedPreferences.edit()) {
            putInt("game_speed", settings.gameSpeed)
            putInt("max_insects", settings.maxInsects)
            putInt("bonus_interval", settings.bonusInterval)
            putInt("round_duration", settings.roundDuration)
            putInt("player_difficulty", playerDifficulty)
            apply()
        }
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
            // Сохраняем сложность игрока
            playerDifficulty = player.difficulty
            saveSettings()
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