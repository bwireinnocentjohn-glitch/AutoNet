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
        
        for ((action, phrases) in commandMap) {
            if (phrases.any { cleanText.contains(it) }) {
                return performAction(context, action, cleanText)
            }
        }

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

    private fun toggleFlashlight(context: Context, turnOn: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cameraManager.setTorchMode(cameraId, turnOn)
            true
        } catch (e: Exception) { false }
    }

    private fun adjustVolume(context: Context, up: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) { }
    }

    private fun openWifiSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun requestBluetoothEnable(context: Context) {
        try {
            val intent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun callContact(context: Context, name: String): String {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(0)
                cursor.close()
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
                return "Calling $name."
            }
        } catch (e: Exception) { }
        return "Could not find contact: $name."
    }

    private fun openApp(context: Context, appName: String): String {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(appName.lowercase())) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return "Opening $appName."
                    }
                }
            }
        } catch (e: Exception) { }
        return "Could not find app: $appName."
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun extractTime(text: String): IntArray? {
        val regex = Regex("(\\d{1,2})[: ](\\d{2})?\\s*(am|pm)?")
        val match = regex.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase()
        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0
        return intArrayOf(hour, minute)
    }

    private fun readLastSms(context: Context): String {
        try {
            val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC")
            if (cursor != null && cursor.moveToFirst()) {
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                cursor.close()
                return "Latest message: $body"
            }
        } catch (e: Exception) { }
        return "Could not read messages."
    }
}
