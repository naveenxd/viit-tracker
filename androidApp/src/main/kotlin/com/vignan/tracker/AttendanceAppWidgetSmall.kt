package com.vignan.tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Android App Widget Provider for small (3:2) attendance tracker widget.
 */
class AttendanceAppWidgetSmall : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        // Called when the first widget is placed
    }
    
    override fun onDisabled(context: Context) {
        // Called when the last widget is removed
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Called when widgets are deleted
    }
    
    companion object {
        /**
         * Update a specific widget instance
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Create RemoteViews for small widget
            val views = RemoteViews(context.packageName, R.layout.widget_attendance_small)
            
            // Set up click intent to open the app
            val intent = Intent(context, MainActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra("from_widget", true)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            
            // Set widget content for small widget
            setWidgetContent(context, views)
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        /**
         * Update all widget instances
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AttendanceAppWidgetSmall::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            if (appWidgetIds.isNotEmpty()) {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
        
        /**
         * Set widget content for small widget
         */
        private fun setWidgetContent(context: Context, views: RemoteViews) {
            // Small widget content
            views.setTextViewText(R.id.widget_student_name, "STUDENT")
            views.setTextViewText(R.id.widget_roll_number, "Roll No")
            views.setTextViewText(R.id.widget_percentage, "85.5%")
            views.setTextViewText(R.id.widget_status, "SAFE")
            views.setTextViewText(R.id.widget_today_status, "✓")
            views.setTextViewText(R.id.widget_skip_indicator, "✓")
            views.setTextColor(R.id.widget_percentage, context.getColor(R.color.safe_emerald))
            views.setTextColor(R.id.widget_status, context.getColor(R.color.safe_emerald))
        }
    }
}