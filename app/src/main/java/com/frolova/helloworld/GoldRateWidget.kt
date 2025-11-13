package com.frolova.helloworld

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.ComponentName

class GoldRateWidget : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_gold_rate)

        // Обновляем данные
        scope.launch {
            try {
                val goldRateService = CbrApiService(context)
                val result = goldRateService.fetchGoldRate()

                if (result.isSuccess) {
                    val goldRate = result.getOrNull()
                    val rateText = goldRate?.getShortFormattedValue() ?: "---"
                    views.setTextViewText(R.id.widget_gold_rate, rateText)
                    views.setTextViewText(R.id.widget_gold_label, "ЗОЛОТО")
                } else {
                    views.setTextViewText(R.id.widget_gold_rate, "Ошибка")
                }
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_gold_rate, "---")
            }

            // Обновляем виджет
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Добавляем клик для обновления
        val intent = Intent(context, GoldRateWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GoldRateWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                val widget = GoldRateWidget()
                widget.onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }
}