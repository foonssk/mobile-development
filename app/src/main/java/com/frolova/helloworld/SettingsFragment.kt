package com.frolova.helloworld

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsFragment : Fragment() {

    private val viewModel: GameViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Скорость игры
        val sbSpeed = view.findViewById<SeekBar>(R.id.sbSpeed)
        val tvSpeed = view.findViewById<TextView>(R.id.tvSpeed)
        sbSpeed.max = 10
        sbSpeed.progress = viewModel.settings.gameSpeed.coerceIn(1, 10)
        tvSpeed.text = "Скорость игры: ${viewModel.settings.gameSpeed}"
        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newProgress = progress.coerceAtLeast(1)
                viewModel.settings = viewModel.settings.copy(gameSpeed = newProgress)
                tvSpeed.text = "Скорость игры: $newProgress"
                viewModel.saveSettings() // Сохраняем настройки
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Максимальное количество тараканов
        val sbMaxCockroaches = view.findViewById<SeekBar>(R.id.sbMaxCockroaches)
        val tvMaxCockroaches = view.findViewById<TextView>(R.id.tvMaxCockroaches)
        sbMaxCockroaches.max = 50
        sbMaxCockroaches.progress = viewModel.settings.maxInsects.coerceIn(1, 50)
        tvMaxCockroaches.text = "Максимальное количество тараканов: ${viewModel.settings.maxInsects}"
        sbMaxCockroaches.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newProgress = progress.coerceAtLeast(1)
                viewModel.settings = viewModel.settings.copy(maxInsects = newProgress)
                tvMaxCockroaches.text = "Максимальное количество тараканов: $newProgress"
                viewModel.saveSettings() // Сохраняем настройки
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Интервал появления бонусов
        val sbBonusInterval = view.findViewById<SeekBar>(R.id.sbBonusInterval)
        val tvBonusInterval = view.findViewById<TextView>(R.id.tvBonusInterval)
        sbBonusInterval.max = 30
        sbBonusInterval.progress = viewModel.settings.bonusInterval.coerceIn(1, 30)
        tvBonusInterval.text = "Интервал появления бонусов: ${viewModel.settings.bonusInterval} сек"
        sbBonusInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newProgress = progress.coerceAtLeast(1)
                viewModel.settings = viewModel.settings.copy(bonusInterval = newProgress)
                tvBonusInterval.text = "Интервал появления бонусов: $newProgress сек"
                viewModel.saveSettings() // Сохраняем настройки
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Длительность раунда
        val sbRoundDuration = view.findViewById<SeekBar>(R.id.sbRoundDuration)
        val tvRoundDuration = view.findViewById<TextView>(R.id.tvRoundDuration)
        sbRoundDuration.max = 300
        sbRoundDuration.progress = viewModel.settings.roundDuration.coerceIn(10, 300)
        tvRoundDuration.text = "Длительность раунда: ${viewModel.settings.roundDuration} сек"
        sbRoundDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newProgress = progress.coerceAtLeast(10)
                viewModel.settings = viewModel.settings.copy(roundDuration = newProgress)
                tvRoundDuration.text = "Длительность раунда: $newProgress сек"
                viewModel.saveSettings() // Сохраняем настройки
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return view
    }
}