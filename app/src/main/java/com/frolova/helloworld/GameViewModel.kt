// GameViewModel.kt
package com.frolova.helloworld

import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel() {
    var settings = Settings(
        gameSpeed = 1,
        maxInsects = 10,
        bonusInterval = 5,
        roundDuration = 60
    )
    var playerDifficulty = 1 // по умолчанию
}