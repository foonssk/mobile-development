package com.frolova.helloworld

import android.content.Context
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class CbrApiService(private val context: Context) {

    private val _currentGoldRate = MutableLiveData<GoldRate>()
    val currentGoldRate = _currentGoldRate

    private var lastUpdateTime: Long = 0
    private val updateInter = 30 * 60 * 1000 // 30 минут

    suspend fun fetchGoldRate(): Result<GoldRate> {
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > updateInter) {
                    loadFreshGoldRate()
                } else {
                    Result.success(_currentGoldRate.value ?: GoldRate())
                }
            } catch (e: Exception) {
                // При ошибке возвращаем реалистичное значение золота
                val fallbackRate = GoldRate(7500.0, "Золото (кэш)")
                Result.success(fallbackRate)
            }
        }
    }

    private suspend fun loadFreshGoldRate(): Result<GoldRate> {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val url = URL("https://www.cbr.ru/scripts/xml_metall.asp?date_req1=$currentDate&date_req2=$currentDate")

        val connection = url.openConnection()
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val inputStream = connection.getInputStream()
        val goldRate = parseGoldRateFromXML(inputStream)
        inputStream.close()

        _currentGoldRate.postValue(goldRate)
        lastUpdateTime = System.currentTimeMillis()
        return Result.success(goldRate)
    }

    private fun parseGoldRateFromXML(inputStream: java.io.InputStream): GoldRate {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "windows-1251")

        var eventType = parser.eventType
        var inRecord = false
        var currentTag = ""
        var buyPrice = ""
        var sellPrice = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Record" -> {
                            // Проверяем, что это золото (Code="1")
                            val code = parser.getAttributeValue(null, "Code")
                            if (code == "1") {
                                inRecord = true
                            }
                        }
                        "Buy" -> if (inRecord) currentTag = "Buy"
                        "Sell" -> if (inRecord) currentTag = "Sell"
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inRecord) {
                        when (currentTag) {
                            "Buy" -> buyPrice = parser.text    // Цена покупки
                            "Sell" -> sellPrice = parser.text  // Цена продажи
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Record" && inRecord) {
                        inRecord = false  // Закончили парсить запись о золоте
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        // Используем цену покупки или продажи (берем среднее для игры)
        val priceToUse = if (buyPrice.isNotEmpty()) buyPrice else sellPrice

        val numericValue = try {
            // ЦБ РФ дает цену за 1 грамм, умножаем на 1000 для цены за кг
            // и делим на 1000, т.к. цена в формате "1234.56"
            priceToUse.replace(",", ".").toDouble() * 1000
        } catch (e: Exception) {
            7500.0  // Значение по умолчанию
        }

        return GoldRate(numericValue, "Золото")
    }

    fun getCachedGoldRate(): GoldRate {
        return _currentGoldRate.value ?: GoldRate(7500.0, "Золото")
    }
}