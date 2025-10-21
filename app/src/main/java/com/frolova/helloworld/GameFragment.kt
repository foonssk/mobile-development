package com.frolova.helloworld

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

class GameFragment : Fragment() {

    private val viewModel: GameViewModel by activityViewModels(
        factoryProducer = { ViewModelProvider.AndroidViewModelFactory(requireActivity().application) }
    )
    private var score = 0
    private var misses = 0
    private var gameActive = false
    private val insects = mutableListOf<View>()
    private val random = Random()
    private var scope: CoroutineScope? = null

    data class InsectType(val drawableRes: Int, val points: Int)

    private lateinit var gameArea: FrameLayout
    private lateinit var scoreText: TextView
    private lateinit var startButton: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_game, container, false)

        gameArea = root.findViewById(R.id.gameArea)
        scoreText = root.findViewById(R.id.scoreText)
        startButton = root.findViewById(R.id.startButton)

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
        scoreText.text = "Очки: $score | Промахи: $misses"
        startButton.visibility = View.GONE
        gameArea.removeAllViews()
        insects.clear()

        val difficulty = viewModel.playerDifficulty.coerceIn(1, 10).toFloat() / 10f

        val effectiveSpeed = (viewModel.settings.gameSpeed * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveMaxInsects = (viewModel.settings.maxInsects * (1 + difficulty)).toInt().coerceAtLeast(1)
        val effectiveRoundDuration = (viewModel.settings.roundDuration * (1 - difficulty * 0.5)).toInt().coerceAtLeast(10)

        scope?.launch {
            delay(effectiveRoundDuration * 1000L)
            if (gameActive) endGame()
        }

        scope?.launch {
            while (gameActive) {
                if (insects.size < effectiveMaxInsects) {
                    spawnInsect(effectiveSpeed)
                }
                delay((1000 / effectiveSpeed.toLong()).coerceAtLeast(100))
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

                // Удаляем насекомое, если оно ещё в списке
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


    private fun removeInsect(insect: View) {
        if (gameArea.isAttachedToWindow && insect.parent == gameArea) {
            gameArea.removeView(insect)
        }
        insects.remove(insect)
        // Никакого штрафа — просто исчезает
    }

    private fun checkGameOver() {
        if (misses >= 10) {
            endGame()
        }
    }

    private fun endGame() {
        gameActive = false
        insects.forEach {
            if (it.parent == gameArea) {
                gameArea.removeView(it)
            }
        }
        insects.clear()
        scope?.cancel()
        scope = null

        //  Сохраняем результат в БД
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

    override fun onDestroy() {
        super.onDestroy()
        scope?.cancel()
    }
}