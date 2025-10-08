package com.frolova.helloworld

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import java.util.Calendar

class RegistrationFragment : Fragment() {

    private val viewModel: GameViewModel by activityViewModels()
    private var selectedDate: Long = Calendar.getInstance().timeInMillis

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_registration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etFullName = view.findViewById<EditText>(R.id.etFullName)
        val rgGender = view.findViewById<RadioGroup>(R.id.rgGender)
        val spinnerCourse = view.findViewById<Spinner>(R.id.spinnerCourse)
        val tvGameDifficulty = view.findViewById<TextView>(R.id.tvGameDifficulty)
        val sbGameDifficulty = view.findViewById<SeekBar>(R.id.sbGameDifficulty)
        val cvCalendar = view.findViewById<CalendarView>(R.id.cvCalendar)
        val tvZodiacSign = view.findViewById<TextView>(R.id.tvZodiacSign)
        val ivZodiacSign = view.findViewById<ImageView>(R.id.ivZodiacSign)
        val btnRegister = view.findViewById<Button>(R.id.btnRegister)
        val tvResult = view.findViewById<TextView>(R.id.tvResult)

        selectedDate = cvCalendar.date
        updateZodiacDisplay(selectedDate, ivZodiacSign, tvZodiacSign)

        spinnerCourse.adapter = setSpinnerCourse()

        sbGameDifficulty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvGameDifficulty.text = "Уровень сложности: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cvCalendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDate = cal.timeInMillis
            updateZodiacDisplay(selectedDate, ivZodiacSign, tvZodiacSign)
        }

        btnRegister.setOnClickListener {
            val fullName = etFullName.text.toString()
            val gender = when (rgGender.checkedRadioButtonId) {
                R.id.rbMale -> "Мужчина"
                R.id.rbFemale -> "Женщина"
                else -> "Не выбран"
            }
            val course = spinnerCourse.selectedItem.toString()
            val difficulty = sbGameDifficulty.progress.coerceIn(1, 10)
            val zodiacSign = getZodiacSign(selectedDate)

            viewModel.playerDifficulty = difficulty

            val settings = Settings(1, 10, 5, 60)

            val player = createPlayer(fullName, gender, course, difficulty, selectedDate, zodiacSign.first)
            val info = formatPlayerInfo(player, settings)
            tvResult.text = info
            tvResult.visibility = View.VISIBLE
        }
    }

    // Все твои методы остаются здесь — они теперь видны!
    private fun getZodiacSign(dateMillis: Long): Pair<String, String> {
        val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        return when (month * 100 + day) {
            in 120..218 -> "Водолей" to "Aquarius"
            in 219..320 -> "Рыбы" to "Pisces"
            in 321..419 -> "Овен" to "Aries"
            in 420..520 -> "Телец" to "Taurus"
            in 521..620 -> "Близнецы" to "Gemini"
            in 621..722 -> "Рак" to "Cancer"
            in 723..822 -> "Лев" to "Leo"
            in 823..922 -> "Дева" to "Virgo"
            in 923..1022 -> "Весы" to "Libra"
            in 1023..1121 -> "Скорпион" to "Scorpio"
            in 1122..1221 -> "Стрелец" to "Sagittarius"
            in 1222..1231, in 101..119 -> "Козерог" to "Capricorn"
            else -> "Неизвестно" to "Unknown"
        }
    }

    private fun getZodiacDrawable(zodiac: String): Int {
        return when (zodiac.lowercase()) {
            "aries" -> R.drawable.ic_zodiac_aries
            "taurus" -> R.drawable.ic_zodiac_taurus
            "gemini" -> R.drawable.ic_zodiac_gemini
            "cancer" -> R.drawable.ic_zodiac_cancer
            "leo" -> R.drawable.ic_zodiac_leo
            "virgo" -> R.drawable.ic_zodiac_virgo
            "libra" -> R.drawable.ic_zodiac_libra
            "scorpio" -> R.drawable.ic_zodiac_scorpio
            "sagittarius" -> R.drawable.ic_zodiac_sagittarius
            "capricorn" -> R.drawable.ic_zodiac_capricorn
            "aquarius" -> R.drawable.ic_zodiac_aquarius
            "pisces" -> R.drawable.ic_zodiac_pisces
            else -> R.drawable.ic_launcher_foreground
        }
    }

    private fun updateZodiacDisplay(dateMillis: Long, imageView: ImageView, textView: TextView) {
        val zodiac = getZodiacSign(dateMillis)
        textView.text = zodiac.first
        imageView.setImageResource(getZodiacDrawable(zodiac.second))
    }

    private fun createPlayer(
        fullName: String,
        gender: String,
        course: String,
        difficulty: Int,
        birthDate: Long,
        zodiacSign: String
    ): Player {
        return Player(fullName, gender, course, difficulty, birthDate, zodiacSign)
    }

    private fun setSpinnerCourse(): ArrayAdapter<String> {
        val courses = arrayOf(
            "Бакалавриат. 1 курс", "Бакалавриат. 2 курс",
            "Бакалавриат. 3 курс", "Бакалавриат. 4 курс",
            "Магистратура. 1 курс", "Магистратура. 2 курс"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun formatPlayerInfo(player: Player, settings: Settings): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = player.birthDate }
        val birthDateStr = "${calendar.get(Calendar.DAY_OF_MONTH)}.${calendar.get(Calendar.MONTH) + 1}.${calendar.get(Calendar.YEAR)}"
        return """
            Регистрация успешна:
            
            ФИО: ${player.fullName}
            Пол: ${player.gender}
            Курс: ${player.course}
            Уровень сложности: ${player.difficulty}/10
            → Влияет на скорость, количество жуков и время раунда!
            Дата рождения: $birthDateStr
            Знак зодиака: ${player.zodiacSign}
            
            --- Настройки игры ---
            Скорость: ${settings.gameSpeed}
            Макс. тараканов: ${settings.maxInsects}
            Интервал бонусов: ${settings.bonusInterval} сек
            Длительность раунда: ${settings.roundDuration} сек
        """.trimIndent()
    }
}