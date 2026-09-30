package com.vignan.tracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Android App Widget Provider for the 4:2 attendance tracker widget.
 *
 * All rendering goes through [WidgetUpdater] (real data, cache-first). A tap on the
 * widget arrives here as [WidgetUpdater.ACTION_REFRESH] and triggers a network
 * refetch + toast instead of opening the app.
 */
class AttendanceAppWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Widget callbacks can run in a fresh process — make the context available
        // to the shared credential store before touching any data.
        AppContextProvider.context = context.applicationContext
        WidgetUpdater.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            AppContextProvider.context = context.applicationContext
            // goAsync keeps the process alive for the duration of the network fetch.
            val pendingResult = goAsync()
            WidgetUpdater.refreshFromTap(context) { pendingResult.finish() }
        }
    }

    companion object {
        /**
         * Update all instances of this widget size from the latest known data.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, AttendanceAppWidget::class.java)
            )
            if (ids.isNotEmpty()) WidgetUpdater.updateAll(context)
        }
    }
}
