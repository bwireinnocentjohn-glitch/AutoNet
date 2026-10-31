package com.autonet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoNetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val task = intent?.getStringExtra("task_text") ?: return
        if (context != null) {
            CommandRouter.execute(context, task)
        }
    }
}
