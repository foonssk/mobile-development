package com.frolova.helloworld

data class GoldRate(
    val value: Double = 0.0,
    val name: String = "Золото",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getPointsValue(): Int {
        return (value / 10000).toInt()
    }

    fun getFormattedValue(): String {
        return String.format("%,.2f", value)
    }

    fun getShortFormattedValue(): String {
        val thousands = value / 1000
        return if (thousands >= 1000) {
            val millions = thousands / 1000
            String.format("%,.1fM", millions)  // "7.5M"
        } else {
            String.format("%,.1fK", thousands) // "7500.0K"
        }
    }

    // Специально для виджета - очень короткий формат
    fun getWidgetFormattedValue(): String {
        val millions = value / 1000000
        return String.format("%,.1f", millions)  // "7.5"
    }
}