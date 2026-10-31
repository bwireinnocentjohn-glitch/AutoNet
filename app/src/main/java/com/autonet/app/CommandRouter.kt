package com.autonet.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.os.Build
import android.provider.ContactsContract
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

object CommandRouter {
    private val commandMap = mapOf(
        "flashlight_on" to listOf("turn on light", "light on", "torch on"),
        "flashlight_off" to listOf("turn off light", "light off", "torch off"),
        "volume_up" to listOf("volume up", "increase volume"),
        "volume_down" to listOf("volume down", "decrease volume"),
        "wifi" to listOf("wifi", "turn on wifi", "turn off wifi"),
        "bluetooth" to listOf("bluetooth", "turn on bluetooth", "turn off bluetooth"),
        "time" to listOf("time", "what is the time", "tell me the time"),
        "date" to listOf("date", "what is the date"),
        "battery" to listOf("battery", "battery level"),
        "read_sms" to listOf("read message", "read my messages"),
        "stop" to listOf("stop", "cancel", "shut up")
    )

    fun execute(context: Context, text: String): String {
        val cleanText = text.lowercase(Locale.getDefault()).trim()
        
        // 1. Exact Match
        for ((action, phrases) in commandMap) {
            if (phrases.any { cleanText.contains(it) }) {
                return performAction(context, action, cleanText)
            }
        }

        // 2. Built-in Fuzzy Match
        var bestMatch: String? = null
        var highestScore = 0.0
        for ((action, phrases) in commandMap) {
            for (phrase in phrases) {
                val score = calculateSimilarity(cleanText, phrase)
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = action
                }
            }
        }
        if (highestScore > 0.6 && bestMatch != null) {
            return performAction(context, bestMatch, cleanText)
        }

        // 3. Dynamic Commands
        if (cleanText.contains("call")) {
            val name = cleanText.replace("call", "").trim()
            return callContact(context, name)
        }
        if (cleanText.contains("open")) {
            val appName = cleanText.replace("open", "").trim()
            return openApp(context, appName)
        }
        if (cleanText.contains("alarm")) {
            val time = extractTime(cleanText)
            if (time != null) {
                setAlarm(context, time[0], time[1])
                return "Alarm set for ${time[0]}:${time[1]}"
            }
            return "Please specify a time."
        }

        return "Command not recognized: $text"
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0
        return (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
    }

    private fun editDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun performAction(context: Context, action: String, rawText: String): String {
        return when (action) {
            "flashlight_on" -> { toggleFlashlight(context, true); "Flashlight on." }
            "flashlight_off" -> { toggleFlashlight(context, false); "Flashlight off." }
            "volume_up" -> { adjustVolume(context, true); "Volume up." }
            "volume_down" -> { adjustVolume(context, false); "Volume down." }
            "wifi" -> { openWifiSettings(context); "Opening Wi-Fi." }
            "bluetooth" -> { requestBluetoothEnable(context); "Opening Bluetooth." }
            "time" -> {
                val cal = Calendar.getInstance()
                "The time is ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}"
            }
            "date" -> {
                val cal = Calendar.getInstance()
                "Today is ${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
            }
            "battery" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                "Battery is at ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            "read_sms" -> readLastSms(context)
            "stop" -> "Stopped."
            else -> "Action executed."
        }
    }

    // ... (Keep the helper functions: toggleFlashlight, adjustVolume, openWifiSettings, requestBluetoothEnable, callContact, openApp, setAlarm, extractTime, readLastSms) ...
    // These are unchanged from the previous working version.
}
