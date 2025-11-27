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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class GameFragment : Fragment(), SensorEventListener {

    private val viewModel: GameViewModel by activityViewModels(
        factoryProducer = { ViewModelProvider.AndroidViewModelFactory(requireActivity().application) }
    )

    private var score = 0
    private var misses = 0
    private var gameActive = false
    private val insects = mutableListOf<InsectData>()
    private val bonuses = mutableListOf<BonusData>()
    private val random = Random()
    private var gameScope: CoroutineScope? = null
    private var gameJob: Job? = null

    // Акселерометр
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var tiltControlActive = false
    private var tiltControlEndTime = 0L

    // Данные для сохранения состояния объектов
    data class InsectData(
        var type: InsectType, // Изменили на var
        var x: Float,         // Изменили на var
        var y: Float,         // Изменили на var
        var dx: Float,        // Изменили на var
        var dy: Float,        // Изменили на var
        var spawnTime: Long,  // Изменили на var
        var isGolden: Boolean = false // Изменили на var
    ) : java.io.Serializable

    data class BonusData(
        var type: BonusType,  // Изменили на var
        var x: Float,         // Изменили на var
        var y: Float,         // Изменили на var
        var spawnTime: Long   // Изменили на var
    ) : java.io.Serializable

    data class InsectType(val drawableRes: Int, val points: Int) : java.io.Serializable
    data class BonusType(val drawableRes: Int, val duration: Long) : java.io.Serializable

    private lateinit var gameArea: FrameLayout
    private lateinit var scoreText: TextView
    private lateinit var startButton: TextView
    private lateinit var tiltIndicator: TextView
    private lateinit var goldRateWidget: LinearLayout
    private lateinit var goldRateText: TextView
    private lateinit var closeWidgetButton: ImageButton

    // Ключи для сохранения состояния
    companion object {
        private const val KEY_SCORE = "score"
        private const val KEY_MISSES = "misses"
        private const val KEY_GAME_ACTIVE = "game_active"
        private const val KEY_TILT_ACTIVE = "tilt_active"
        private const val KEY_TILT_END_TIME = "tilt_end_time"
        private const val KEY_INSECTS = "insects"
        private const val KEY_BONUSES = "bonuses"
        private const val KEY_GAME_START_TIME = "game_start_time"
    }

    private var gameStartTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_game, container, false)

        gameArea = root.findViewById(R.id.gameArea)
        scoreText = root.findViewById(R.id.scoreText)
        startButton = root.findViewById(R.id.startButton)
        tiltIndicator = root.findViewById(R.id.tiltIndicator)

        // Инициализация виджета золота
        goldRateWidget = root.findViewById(R.id.goldRateWidget)
        goldRateText = root.findViewById(R.id.goldRateText)
        closeWidgetButton = root.findViewById(R.id.closeWidgetButton)

        // Инициализация акселерометра
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Обработчик закрытия виджета
        closeWidgetButton.setOnClickListener {
            viewModel.toggleWidget()
        }

        // Наблюдение за видимостью виджета
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.widgetVisible.collect { visible ->
                goldRateWidget.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        // Наблюдение за курсом золота
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.goldRate.collect { goldRate ->
                goldRate?.let {
                    goldRateText.text = "Золото\n${it.getShortFormattedValue()} руб/кг"
                }
            }
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Восстановление состояния при повороте
        savedInstanceState?.let { bundle ->
            score = bundle.getInt(KEY_SCORE, 0)
            misses = bundle.getInt(KEY_MISSES, 0)
            gameActive = bundle.getBoolean(KEY_GAME_ACTIVE, false)
            tiltControlActive = bundle.getBoolean(KEY_TILT_ACTIVE, false)
            tiltControlEndTime = bundle.getLong(KEY_TILT_END_TIME, 0L)
            gameStartTime = bundle.getLong(KEY_GAME_START_TIME, 0L)

            // Восстанавливаем данные объектов
            val savedInsects = bundle.getSerializable(KEY_INSECTS) as? ArrayList<InsectData>
            val savedBonuses = bundle.getSerializable(KEY_BONUSES) as? ArrayList<BonusData>

            if (savedInsects != null) insects.addAll(savedInsects)
            if (savedBonuses != null) bonuses.addAll(savedBonuses)

            scoreText.text = "Очки: $score | Промахи: $misses"

            if (gameActive) {
                // Скрываем кнопку старта
                startButton.visibility = View.GONE
                // Восстанавливаем управление наклоном если было активно
                if (tiltControlActive && System.currentTimeMillis() <= tiltControlEndTime) {
                    activateTiltControl(tiltControlEndTime - System.currentTimeMillis())
                } else {
                    tiltControlActive = false
                    tiltIndicator.visibility = View.GONE
                }

                // Запускаем игру с восстановленным состоянием
                continueGame()
            } else {
                startButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем все данные состояния
        outState.putInt(KEY_SCORE, score)
        outState.putInt(KEY_MISSES, misses)
        outState.putBoolean(KEY_GAME_ACTIVE, gameActive)
        outState.putBoolean(KEY_TILT_ACTIVE, tiltControlActive)
        outState.putLong(KEY_TILT_END_TIME, tiltControlEndTime)
        outState.putLong(KEY_GAME_START_TIME, gameStartTime)

        // Сохраняем данные объектов
        outState.putSerializable(KEY_INSECTS, ArrayList(insects))
        outState.putSerializable(KEY_BONUSES, ArrayList(bonuses))

        // Останавливаем корутины при сохранении состояния
        gameJob?.cancel()
        gameJob = null
        gameScope = null

        // Очищаем визуальные представления
        gameArea.removeAllViews()

        if (tiltControlActive) {
            sensorManager.unregisterListener(this)
        }
    }

    private fun startNewGame() {
        gameJob?.cancel()
        gameJob = Job()
        gameScope = CoroutineScope(Dispatchers.Main + gameJob!!)

        gameActive = true
        score = 0
        misses = 0
        insects.clear()
        bonuses.clear()
        tiltControlActive = false
        gameStartTime = System.currentTimeMillis()

        scoreText.text = "Очки: $score | Промахи: $misses"
        tiltIndicator.visibility = View.GONE
        startButton.visibility = View.GONE
        gameArea.removeAllViews()

        startGameLoop()
    }

    private fun continueGame() {
        gameJob?.cancel()
        gameJob = Job()
        gameScope = CoroutineScope(Dispatchers.Main + gameJob!!)

        // Воссоздаем визуальные объекты из сохраненных данных
        recreateVisualObjects()

        // Продолжаем игровой цикл
        startGameLoop()
    }

    private fun recreateVisualObjects() {
        // Воссоздаем насекомых
        insects.forEach { insectData ->
            val insectView = createInsectView(insectData)
            gameArea.addView(insectView)
        }

        // Воссоздаем бонусы
        bonuses.forEach { bonusData ->
            val bonusView = createBonusView(bonusData)
            gameArea.addView(bonusView)
        }
    }

    private fun createInsectView(insectData: InsectData): ImageView {
        return ImageView(requireContext()).apply {
            setImageResource(insectData.type.drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val size = dpToPx(if (insectData.isGolden) 70 else 60)
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = insectData.x
            y = insectData.y
            setTag(R.id.insect_type, insectData.type)
            setTag(R.id.insect_data, insectData)

            setOnClickListener {
                if (insects.contains(insectData)) {
                    gameArea.removeView(this)
                    insects.remove(insectData)
                    score += insectData.type.points
                    scoreText.text = "Очки: $score | Промахи: $misses"
                }
            }
        }
    }

    private fun createBonusView(bonusData: BonusData): ImageView {
        return ImageView(requireContext()).apply {
            setImageResource(bonusData.type.drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val size = dpToPx(50)
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = bonusData.x
            y = bonusData.y
            setTag(R.id.bonus_type, bonusData.type)
            setTag(R.id.bonus_data, bonusData)

            setOnClickListener {
                if (bonuses.contains(bonusData)) {
                    gameArea.removeView(this)
                    bonuses.remove(bonusData)
                    activateTiltControl(bonusData.type.duration)
                    score += 20
                    scoreText.text = "Очки: $score | Промахи: $misses"
                }
            }
        }
    }

    private fun startGameLoop() {
        val difficulty = viewModel.playerDifficulty.coerceIn(1, 10).toFloat() / 10f
        val effectiveSpeed = (viewModel.settings.gameSpeed * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveMaxInsects = (viewModel.settings.maxInsects * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveRoundDuration = (viewModel.settings.roundDuration * (1 - difficulty * 0.5)).toInt().coerceAtLeast(10)

        // Таймер окончания игры
        gameScope?.launch {
            val remainingTime = if (gameStartTime > 0) {
                effectiveRoundDuration * 1000L - (System.currentTimeMillis() - gameStartTime)
            } else {
                effectiveRoundDuration * 1000L
            }

            if (remainingTime > 0) {
                delay(remainingTime)
                if (gameActive) endGame()
            } else {
                endGame()
            }
        }

        // Спаун насекомых
        gameScope?.launch {
            while (isActive && gameActive) {
                if (insects.size < effectiveMaxInsects) {
                    spawnInsect(effectiveSpeed)
                }
                delay((1000 / effectiveSpeed.toLong()).coerceAtLeast(100))
            }
        }

        // Спаун бонусов
        gameScope?.launch {
            while (isActive && gameActive) {
                delay(15000)
                if (gameActive && bonuses.isEmpty()) {
                    spawnBonus()
                }
            }
        }

        // Спаун золотых тараканов
        gameScope?.launch {
            while (isActive && gameActive) {
                delay(20000)
                if (gameActive) {
                    spawnGoldenCockroach()
                }
            }
        }

        // Движение объектов
        gameScope?.launch {
            while (isActive && gameActive) {
                if (!tiltControlActive) {
                    updateInsectPositions()
                }
                delay(50)
            }
        }

        // Проверка времени бонусов
        gameScope?.launch {
            while (isActive && gameActive) {
                delay(1000)
                checkInsectLifetimes()
                if (tiltControlActive && System.currentTimeMillis() > tiltControlEndTime) {
                    deactivateTiltControl()
                }
            }
        }
    }

    private fun updateInsectPositions() {
        gameArea.post {
            insects.forEach { insectData ->
                val insectView = findViewByData(insectData) ?: return@forEach

                var newX = insectData.x + insectData.dx
                var newY = insectData.y + insectData.dy

                val maxWidth = gameArea.width - insectView.width
                val maxHeight = gameArea.height - insectView.height

                if (newX <= 0f) {
                    newX = 0f
                    insectData.dx = -insectData.dx
                } else if (newX >= maxWidth) {
                    newX = maxWidth.toFloat()
                    insectData.dx = -insectData.dx
                }

                if (newY <= 0f) {
                    newY = 0f
                    insectData.dy = -insectData.dy
                } else if (newY >= maxHeight) {
                    newY = maxHeight.toFloat()
                    insectData.dy = -insectData.dy
                }

                insectData.x = newX
                insectData.y = newY
                insectView.x = newX
                insectView.y = newY
            }
        }
    }

    private fun checkInsectLifetimes() {
        val currentTime = System.currentTimeMillis()
        val insectsToRemove = insects.filter {
            currentTime - it.spawnTime > 5000
        }

        insectsToRemove.forEach { insectData ->
            removeInsect(insectData)
        }

        val bonusesToRemove = bonuses.filter {
            currentTime - it.spawnTime > 5000
        }

        bonusesToRemove.forEach { bonusData ->
            removeBonus(bonusData)
        }
    }

    private fun findViewByData(insectData: InsectData): ImageView? {
        for (i in 0 until gameArea.childCount) {
            val view = gameArea.getChildAt(i)
            if (view is ImageView && view.getTag(R.id.insect_data) == insectData) {
                return view
            }
        }
        return null
    }

    private fun spawnInsect(gameSpeed: Int) {
        gameArea.post {
            if (!gameActive) return@post

            val insectTypes = listOf(
                InsectType(R.drawable.ic_bug, 5),
                InsectType(R.drawable.ic_cockroach, 10)
            )
            val chosenType = insectTypes.random()

            val maxWidth = gameArea.width - dpToPx(60)
            val maxHeight = gameArea.height - dpToPx(60)
            if (maxWidth <= 0 || maxHeight <= 0) return@post

            val startX = random.nextInt(maxWidth.coerceAtLeast(1)).toFloat()
            val startY = random.nextInt(maxHeight.coerceAtLeast(1)).toFloat()

            val baseSpeed = 3 + gameSpeed * 2
            val dx = (random.nextFloat() - 0.5f) * baseSpeed * 2
            val dy = (random.nextFloat() - 0.5f) * baseSpeed * 2

            val insectData = InsectData(
                type = chosenType,
                x = startX,
                y = startY,
                dx = dx,
                dy = dy,
                spawnTime = System.currentTimeMillis()
            )

            insects.add(insectData)
            val insectView = createInsectView(insectData)
            gameArea.addView(insectView)
        }
    }

    private fun spawnGoldenCockroach() {
        gameArea.post {
            if (!gameActive) return@post

            val goldRate = viewModel.getCurrentGoldRate()
            val points = goldRate.getPointsValue()
            val goldenType = InsectType(R.drawable.ic_golden_cockroach, points)

            val maxWidth = gameArea.width - dpToPx(70)
            val maxHeight = gameArea.height - dpToPx(70)
            if (maxWidth <= 0 || maxHeight <= 0) return@post

            val startX = random.nextInt(maxWidth.coerceAtLeast(1)).toFloat()
            val startY = random.nextInt(maxHeight.coerceAtLeast(1)).toFloat()

            val baseSpeed = 2
            val dx = (random.nextFloat() - 0.5f) * baseSpeed * 2
            val dy = (random.nextFloat() - 0.5f) * baseSpeed * 2

            val insectData = InsectData(
                type = goldenType,
                x = startX,
                y = startY,
                dx = dx,
                dy = dy,
                spawnTime = System.currentTimeMillis(),
                isGolden = true
            )

            insects.add(insectData)
            val insectView = createInsectView(insectData)
            gameArea.addView(insectView)

            // Автоматическое удаление через 7 секунд
            gameScope?.launch {
                delay(7000)
                if (insects.contains(insectData)) {
                    removeInsect(insectData)
                }
            }
        }
    }

    private fun spawnBonus() {
        gameArea.post {
            if (!gameActive) return@post

            val bonusType = BonusType(R.drawable.ic_bonus_tilt, 10000L)

            val maxWidth = gameArea.width - dpToPx(50)
            val maxHeight = gameArea.height - dpToPx(50)
            if (maxWidth <= 0 || maxHeight <= 0) return@post

            val startX = random.nextInt(maxWidth.coerceAtLeast(1)).toFloat()
            val startY = random.nextInt(maxHeight.coerceAtLeast(1)).toFloat()

            val bonusData = BonusData(
                type = bonusType,
                x = startX,
                y = startY,
                spawnTime = System.currentTimeMillis()
            )

            bonuses.add(bonusData)
            val bonusView = createBonusView(bonusData)
            gameArea.addView(bonusView)
        }
    }

    private fun activateTiltControl(duration: Long) {
        tiltControlActive = true
        tiltControlEndTime = System.currentTimeMillis() + duration
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        tiltIndicator.visibility = View.VISIBLE
        tiltIndicator.text = "Управление наклоном активно!"

        gameScope?.launch {
            var timeLeft = duration / 1000
            while (timeLeft > 0 && isActive && tiltControlActive) {
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

        insects.forEach { insectData ->
            val speed = 15f

            var newX = insectData.x - x * speed
            var newY = insectData.y + y * speed

            val insectView = findViewByData(insectData) ?: return@forEach
            val maxWidth = gameArea.width - insectView.width
            val maxHeight = gameArea.height - insectView.height

            newX = newX.coerceIn(0f, maxWidth.toFloat())
            newY = newY.coerceIn(0f, maxHeight.toFloat())

            insectData.x = newX
            insectData.y = newY
            insectView.x = newX
            insectView.y = newY
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun removeInsect(insectData: InsectData) {
        val insectView = findViewByData(insectData)
        insectView?.let {
            if (gameArea.isAttachedToWindow && it.parent == gameArea) {
                gameArea.removeView(it)
            }
        }
        insects.remove(insectData)
    }

    private fun removeBonus(bonusData: BonusData) {
        // Находим view по данным бонуса
        for (i in 0 until gameArea.childCount) {
            val view = gameArea.getChildAt(i)
            if (view is ImageView && view.getTag(R.id.bonus_data) == bonusData) {
                if (gameArea.isAttachedToWindow && view.parent == gameArea) {
                    gameArea.removeView(view)
                }
                break
            }
        }
        bonuses.remove(bonusData)
    }

    private fun checkGameOver() {
        if (misses >= 10) {
            endGame()
        }
    }

    private fun endGame() {
        gameActive = false
        deactivateTiltControl()

        insects.clear()
        bonuses.clear()
        gameArea.removeAllViews()

        gameJob?.cancel()
        gameJob = null
        gameScope = null

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
        viewModel.loadGoldRate()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameJob?.cancel()
        if (tiltControlActive) {
            sensorManager.unregisterListener(this)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}