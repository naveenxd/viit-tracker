package com.vignan.tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable

/**
 * Android App Widget Provider for attendance tracker.
 * This class handles widget lifecycle events and updates.
 */
class AttendanceAppWidget : AppWidgetProvider() {
    
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
        // Perform any initialization, like starting a service
    }
    
    override fun onDisabled(context: Context) {
        // Called when the last widget is removed
        // Clean up any resources
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Called when widgets are deleted
        for (appWidgetId in appWidgetIds) {
            // Clean up any stored preferences for this widget
        }
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
            // Determine widget size based on appWidgetId configuration
            val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            val isSmallWidget = widgetInfo?.initialLayout == R.layout.widget_attendance_small
            
            // Create RemoteViews based on widget size
            val views = if (isSmallWidget) {
                RemoteViews(context.packageName, R.layout.widget_attendance_small)
            } else {
                RemoteViews(context.packageName, R.layout.widget_attendance)
            }
            
            // Set up click intent to open the app
            val intent = Intent(context, MainActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra("from_widget", true)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            
            // Set widget content
            setWidgetContent(context, views, isSmallWidget)
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        /**
         * Update all widget instances
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AttendanceAppWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            if (appWidgetIds.isNotEmpty()) {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
        
        /**
         * Set widget content based on widget size
         */
        private fun setWidgetContent(
            context: Context,
            views: RemoteViews,
            isSmallWidget: Boolean
        ) {
            if (isSmallWidget) {
                // 3:2 widget content
                views.setTextViewText(R.id.widget_student_name, "STUDENT")
                views.setTextViewText(R.id.widget_roll_number, "Roll No")
                views.setTextViewText(R.id.widget_percentage, "85.5%")
                views.setTextViewText(R.id.widget_status, "SAFE")
                views.setTextViewText(R.id.widget_skip_count, "3")
                views.setTextViewText(R.id.widget_today_status, "✓")
                views.setTextColor(R.id.widget_percentage, context.getColor(R.color.safe_emerald))
                views.setTextColor(R.id.widget_status, context.getColor(R.color.safe_emerald))
            } else {
                // 4:2 widget content
                views.setTextViewText(R.id.widget_student_name, "STUDENT NAME")
                views.setTextViewText(R.id.widget_roll_number, "21B01A1234")
                views.setTextViewText(R.id.widget_percentage, "85.5%")
                views.setTextViewText(R.id.widget_classes, "45 / 52")
                views.setTextViewText(R.id.widget_skip_status, "CAN SKIP")
                views.setTextViewText(R.id.widget_skip_count, "3 periods")
                views.setTextViewText(R.id.widget_today_status, "✓")
                views.setTextColor(R.id.widget_percentage, context.getColor(R.color.safe_emerald))
                views.setTextColor(R.id.widget_skip_status, context.getColor(R.color.safe_emerald))
            }
        }
    }
}