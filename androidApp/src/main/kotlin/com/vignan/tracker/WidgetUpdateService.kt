package com.vignan.tracker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Service to update widget data when attendance data changes.
 * This can be triggered from the main app when new data is fetched.
 */
class WidgetUpdateService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Update all widgets
        updateAllWidgets()
        
        // Stop the service after completing the work
        stopSelf(startId)
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun updateAllWidgets() {
        // Update both widget sizes
        AttendanceAppWidget.updateAllWidgets(this)
        AttendanceAppWidgetSmall.updateAllWidgets(this)
    }
    
    companion object {
        /**
         * Start the widget update service
         */
        fun start(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java)
            context.startService(intent)
        }
        
        /**
         * Trigger widget update from anywhere in the app
         */
        fun triggerWidgetUpdate(context: Context) {
            // Update widgets immediately
            AttendanceAppWidget.updateAllWidgets(context)
            AttendanceAppWidgetSmall.updateAllWidgets(context)
            
            // Also start service for any background updates if needed
            start(context)
        }
    }
}