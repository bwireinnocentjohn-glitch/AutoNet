package com.autonet.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class AutoNetAccessibilityService : AccessibilityService() {
    private var lastTapTime: Long = 0
    private var tapCount = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_UP) {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastTapTime < 300) {
                tapCount++
                if (tapCount == 3) {
                    // Triple tap detected!
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    tapCount = 0
                }
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime
        }
    }

    override fun onInterrupt() {}
}
