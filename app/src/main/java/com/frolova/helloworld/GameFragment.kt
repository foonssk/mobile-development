package com.frolova.helloworld

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.*
import java.util.Random
import androidx.lifecycle.ViewModelProvider

class GameFragment : Fragment(), SensorEventListener {

    private val viewModel: GameViewModel by activityViewModels(
        factoryProducer = { ViewModelProvider.AndroidViewModelFactory(requireActivity().application) }
    )
    private var score = 0
    private var misses = 0
    private var gameActive = false
    private val insects = mutableListOf<View>()
    private val bonuses = mutableListOf<View>()
    private val random = Random()
    private var scope: CoroutineScope? = null

    // Акселерометр
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var tiltControlActive = false
    private var tiltControlEndTime = 0L

    data class InsectType(val drawableRes: Int, val points: Int)
    data class BonusType(val drawableRes: Int, val duration: Long)

    private lateinit var gameArea: FrameLayout
    private lateinit var scoreText: TextView
    private lateinit var startButton: TextView
    private lateinit var tiltIndicator: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_game, container, false)

        gameArea = root.findViewById(R.id.gameArea)
        scoreText = root.findViewById(R.id.scoreText)
        startButton = root.findViewById(R.id.startButton)
        tiltIndicator = root.findViewById(R.id.tiltIndicator) // Добавьте этот TextView в layout

        // Инициализация акселерометра
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Промах = только клик по фону, когда есть насекомые
        gameArea.setOnClickListener {
            if (gameActive && insects.isNotEmpty()) {
                misses++
                score -= 1
                scoreText.text = "Очки: $score | Промахи: $misses"
                checkGameOver()
            }
        }

        startButton.setOnClickListener {
            startNewGame()
        }

        return root
    }

    private fun startNewGame() {
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        gameActive = true
        score = 0
        misses = 0
        tiltControlActive = false
        scoreText.text = "Очки: $score | Промахи: $misses"
        tiltIndicator.visibility = View.GONE
        startButton.visibility = View.GONE
        gameArea.removeAllViews()
        insects.clear()
        bonuses.clear()

        val difficulty = viewModel.playerDifficulty.coerceIn(1, 10).toFloat() / 10f

        val effectiveSpeed = (viewModel.settings.gameSpeed * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveMaxInsects = (viewModel.settings.maxInsects * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveRoundDuration = (viewModel.settings.roundDuration * (1 - difficulty * 0.5)).toInt().coerceAtLeast(10)

        scope?.launch {
            delay(effectiveRoundDuration * 1000L)
            if (gameActive) endGame()
        }

        // Запуск спауна насекомых
        scope?.launch {
            while (gameActive) {
                if (insects.size < effectiveMaxInsects) {
                    spawnInsect(effectiveSpeed)
                }
                delay((1000 / effectiveSpeed.toLong()).coerceAtLeast(100))
            }
        }

        // Запуск спауна бонусов каждые 15 секунд
        scope?.launch {
            while (gameActive) {
                delay(15000) // Каждые 15 секунд
                if (gameActive && bonuses.isEmpty()) {
                    spawnBonus()
                }
            }
        }

        // Запуск проверки времени действия бонуса
        scope?.launch {
            while (gameActive) {
                delay(1000)
                if (tiltControlActive && System.currentTimeMillis() > tiltControlEndTime) {
                    deactivateTiltControl()
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun spawnInsect(gameSpeed: Int) {
        val insectTypes = listOf(
            InsectType(R.drawable.ic_bug, 5),
            InsectType(R.drawable.ic_cockroach, 10)
        )
        val chosenType = insectTypes.random()

        val insect = ImageView(requireContext()).apply {
            setImageResource(chosenType.drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val size = dpToPx(60)
        val params = ViewGroup.LayoutParams(size, size)
        insect.layoutParams = params

        gameArea.post {
            if (!gameActive) return@post

            val maxWidth = gameArea.width - size
            val maxHeight = gameArea.height - size
            if (maxWidth <= 0 || maxHeight <= 0) return@post

            val startX = random.nextInt(maxWidth.coerceAtLeast(1)).toFloat()
            val startY = random.nextInt(maxHeight.coerceAtLeast(1)).toFloat()

            insect.x = startX
            insect.y = startY

            gameArea.addView(insect)
            insects.add(insect)
            insect.setTag(R.id.insect_type, chosenType)

            scope?.launch {
                var x = startX
                var y = startY
                val baseSpeed = 3 + gameSpeed * 2
                var dx = (random.nextFloat() - 0.5f) * baseSpeed * 2
                var dy = (random.nextFloat() - 0.5f) * baseSpeed * 2

                while (insects.contains(insect) && gameActive) {
                    // Если активно управление наклоном, используем акселерометр
                    if (tiltControlActive) {
                        // Движение будет обработано в onSensorChanged
                        delay(50)
                        continue
                    }

                    x += dx
                    y += dy

                    if (x <= 0f) {
                        x = 0f
                        dx = -dx
                    } else if (x >= maxWidth) {
                        x = maxWidth.toFloat()
                        dx = -dx
                    }

                    if (y <= 0f) {
                        y = 0f
                        dy = -dy
                    } else if (y >= maxHeight) {
                        y = maxHeight.toFloat()
                        dy = -dy
                    }

                    withContext(Dispatchers.Main) {
                        insect.x = x
                        insect.y = y
                    }

                    delay(50)
                }

                if (insects.contains(insect)) {
                    removeInsect(insect)
                }
            }

            scope?.launch {
                delay(5000)
                if (insects.contains(insect)) {
                    removeInsect(insect)
                }
            }

            insect.setOnClickListener {
                if (insects.contains(insect)) {
                    gameArea.removeView(insect)
                    insects.remove(insect)
                    val type = insect.getTag(R.id.insect_type) as InsectType
                    score += type.points
                    scoreText.text = "Очки: $score | Промахи: $misses"
                }
            }
        }
    }

    private fun spawnBonus() {
        val bonusType = BonusType(R.drawable.ic_bonus_tilt, 10000L) // 10 секунд

        val bonus = ImageView(requireContext()).apply {
            setImageResource(bonusType.drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val size = dpToPx(50)
        val params = ViewGroup.LayoutParams(size, size)
        bonus.layoutParams = params

        gameArea.post {
            if (!gameActive) return@post

            val maxWidth = gameArea.width - size
            val maxHeight = gameArea.height - size
            if (maxWidth <= 0 || maxHeight <= 0) return@post

            val startX = random.nextInt(maxWidth.coerceAtLeast(1)).toFloat()
            val startY = random.nextInt(maxHeight.coerceAtLeast(1)).toFloat()

            bonus.x = startX
            bonus.y = startY

            gameArea.addView(bonus)
            bonuses.add(bonus)
            bonus.setTag(R.id.bonus_type, bonusType)

            // Автоматическое исчезновение бонуса через 5 секунд
            scope?.launch {
                delay(5000)
                if (bonuses.contains(bonus)) {
                    removeBonus(bonus)
                }
            }

            bonus.setOnClickListener {
                if (bonuses.contains(bonus)) {
                    gameArea.removeView(bonus)
                    bonuses.remove(bonus)
                    activateTiltControl(bonusType.duration)
                    score += 20 // Бонусные очки
                    scoreText.text = "Очки: $score | Промахи: $misses"
                }
            }
        }
    }

    private fun activateTiltControl(duration: Long) {
        tiltControlActive = true
        tiltControlEndTime = System.currentTimeMillis() + duration

        // Регистрируем слушатель акселерометра
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        // Показываем индикатор
        tiltIndicator.visibility = View.VISIBLE
        tiltIndicator.text = "Управление наклоном активно!"

        scope?.launch {
            // Таймер обратного отсчета
            var timeLeft = duration / 1000
            while (timeLeft > 0 && tiltControlActive) {
                tiltIndicator.text = "Управление наклоном: ${timeLeft}с"
                delay(1000)
                timeLeft--
            }
        }
    }

    private fun deactivateTiltControl() {
        tiltControlActive = false
        sensorManager.unregisterListener(this)
        tiltIndicator.visibility = View.GONE
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!tiltControlActive || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]

        // Применяем наклон ко всем насекомым
        insects.forEach { insect ->
            val speed = 15f // Скорость движения при наклоне

            var newX = insect.x - x * speed
            var newY = insect.y + y * speed // Инвертируем Y для естественного движения

            // Ограничиваем границы экрана
            val maxWidth = gameArea.width - insect.width
            val maxHeight = gameArea.height - insect.height

            newX = newX.coerceIn(0f, maxWidth.toFloat())
            newY = newY.coerceIn(0f, maxHeight.toFloat())

            insect.x = newX
            insect.y = newY
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не используется
    }

    private fun removeInsect(insect: View) {
        if (gameArea.isAttachedToWindow && insect.parent == gameArea) {
            gameArea.removeView(insect)
        }
        insects.remove(insect)
    }

    private fun removeBonus(bonus: View) {
        if (gameArea.isAttachedToWindow && bonus.parent == gameArea) {
            gameArea.removeView(bonus)
        }
        bonuses.remove(bonus)
    }

    private fun checkGameOver() {
        if (misses >= 10) {
            endGame()
        }
    }

    private fun endGame() {
        gameActive = false
        deactivateTiltControl()

        insects.forEach {
            if (it.parent == gameArea) {
                gameArea.removeView(it)
            }
        }
        insects.clear()

        bonuses.forEach {
            if (it.parent == gameArea) {
                gameArea.removeView(it)
            }
        }
        bonuses.clear()

        scope?.cancel()
        scope = null

        // Сохраняем результат в БД
        viewModel.saveScore(score, misses)

        val resultText = TextView(requireContext()).apply {
            text = "Игра окончена!\nОчки: $score\nПромахи: $misses"
            textSize = 20f
            setPadding(40, 40, 40, 40)
            setBackgroundColor(requireContext().getColor(R.color.my_primary))
            setTextColor(requireContext().getColor(android.R.color.white))
        }
        gameArea.addView(resultText)

        startButton.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        if (tiltControlActive) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (tiltControlActive) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope?.cancel()
        if (tiltControlActive) {
            sensorManager.unregisterListener(this)
        }
    }
}