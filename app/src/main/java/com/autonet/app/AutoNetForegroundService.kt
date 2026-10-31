package com.autonet.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AutoNetForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "autonet_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AutoNet Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("AutoNet Active")
         .setContentText("Running scheduled task...")
         .setSmallIcon(android.R.drawable.ic_menu_compass)
         .build()

        startForeground(1, notification)
        
        // Here is where the alarm would trigger the AI to run the scheduled task.
        // For now, we stop the service gracefully after starting.
        stopSelf()
        return START_NOT_STICKY
    }
}
