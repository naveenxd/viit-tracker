package com.vignan.tracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Android App Widget Provider for the 4:2 split attendance widget.
 *
 * Left three-quarters carry the student's identity, verdict, overall percentage
 * and progress; the right quarter stacks today's per-subject attendance chips.
 * All rendering goes through [WidgetUpdater]; a tap arrives here as
 * [WidgetUpdater.ACTION_REFRESH] and refetches attendance instead of opening the app.
 */
class AttendanceDetailedAppWidget : AppWidgetProvider() {

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
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            AppContextProvider.context = context.applicationContext
            super.onReceive(context, intent)
            val pendingResult = goAsync()
            WidgetUpdater.refreshOnSchedule(context) { pendingResult.finish() }
            return
        }

        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            AppContextProvider.context = context.applicationContext
            val pendingResult = goAsync()
            WidgetUpdater.refreshFromTap(context) { pendingResult.finish() }
        }
    }

    companion object {
        /** Update all instances of this widget from the latest known data. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AttendanceDetailedAppWidget::class.java)
            )
            if (ids.isNotEmpty()) WidgetUpdater.updateAll(context)
        }
    }
}
