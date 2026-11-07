package com.vignan.tracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

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
        AppContextProvider.context = context.applicationContext
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> WidgetRefreshJob.enqueue(context, fromTap = false)
            WidgetUpdater.ACTION_REFRESH -> WidgetRefreshJob.enqueue(context, fromTap = true)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        AppContextProvider.context = context.applicationContext
        WidgetUpdater.updateAll(context)
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
