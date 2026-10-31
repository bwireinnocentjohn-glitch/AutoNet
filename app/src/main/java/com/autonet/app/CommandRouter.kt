package com.autonet.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.provider.ContactsContract
import android.telephony.SmsManager
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

object CommandRouter {
    fun execute(context: Context, text: String): String {
        val cmd = text.lowercase(Locale.getDefault()).trim()
        
        return when {
            // 🔦 FLASHLIGHT (Synonyms: torch, light, bulb, lamp, flash)
            containsAny(cmd, "flashlight", "torch", "light on", "turn on light", "bulb on", "lamp on", "flash on") -> { 
                toggleFlashlight(context, true)
                "Flashlight on." 
            }
            containsAny(cmd, "light off", "turn off light", "torch off", "bulb off", "lamp off", "flash off") -> { 
                toggleFlashlight(context, false)
                "Flashlight off." 
            }
            
            // 🔊 VOLUME
            containsAny(cmd, "volume up", "louder", "increase volume", "sound up") -> { adjustVolume(context, true); "Volume up." }
            containsAny(cmd, "volume down", "quieter", "decrease volume", "sound down") -> { adjustVolume(context, false); "Volume down." }
            
            // 🔕 DO NOT DISTURB (Synonyms: silent, mute, dnd)
            containsAny(cmd, "do not disturb", "dnd on", "silent mode", "mute", "silence phone") -> { toggleDnd(context, true); "Do Not Disturb is on." }
            containsAny(cmd, "turn off dnd", "disable do not disturb", "normal mode", "unsilence") -> { toggleDnd(context, false); "Do Not Disturb is off." }
            
            // 🔆 BRIGHTNESS
            containsAny(cmd, "brightness up", "brighter", "screen light up") -> { openBrightnessSettings(context); "Opening brightness settings." }
            containsAny(cmd, "brightness down", "dimmer", "screen light down") -> { openBrightnessSettings(context); "Opening brightness settings." }
            
            // ⏰ TIME & DATE
            cmd.contains("time") -> {
                val cal = Calendar.getInstance()
                "The time is ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}"
            }
            cmd.contains("date") -> {
                val cal = Calendar.getInstance()
                "Today is ${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
            }
            
            // 🔋 BATTERY
            cmd.contains("battery") -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                "Battery is at ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            
            // 📩 SMS (Read & Send)
            containsAny(cmd, "read message", "read sms", "check text") -> readLastSms(context)
            containsAny(cmd, "send sms", "send message", "text ") -> {
                val parts = cmd.replace("send sms", "").replace("send message", "").replace("text", "").trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    sendSms(context, parts[0], parts[1])
                    "Sending message to ${parts[0]}."
                } else {
                    "Please say 'send message to [Name] saying [Message]'."
                }
            }
            
            // 📞 CALLS
            cmd.startsWith("call") -> {
                val name = cmd.replace("call", "").trim()
                callContact(context, name)
            }
            
            // ⏰ ALARMS
            containsAny(cmd, "set alarm", "wake me up", "alarm for") -> {
                val time = extractTime(cmd)
                if (time != null) {
                    setAlarm(context, time[0], time[1])
                    "Alarm set for ${time[0]}:${time[1]}"
                } else {
                    "Please specify a time, e.g., 'set alarm for 7:30 am'."
                }
            }
            
            // 🚀 OPEN APPS
            cmd.startsWith("open") -> {
                val appName = cmd.replace("open", "").trim()
                if (appName.contains("camera")) { openCamera(context); "Opening camera." } 
                else { openApp(context, appName) }
            }
            
            // 🧮 MATH
            containsAny(cmd, "calculate", "plus", "minus", "times", "divided by") -> {
                val expression = cmd.replace("calculate", "").replace("what is", "").trim()
                calculateMath(expression)
            }
            
            // 🌐 WEB SEARCH
            containsAny(cmd, "search for", "browse") -> {
                val query = cmd.replace("search for", "").replace("browse", "").trim()
                browseWeb(context, query)
                "Searching for $query."
            }
            
            // 🎯 MARKETING & FUN
            containsAny(cmd, "who made you", "who created you") -> "I was engineered by Ajon Inno."
            containsAny(cmd, "joke") -> {
                val jokes = listOf(
                    "Why did the phone go to the doctor? Because it lost its contacts!",
                    "What do you call a fake noodle? An impasta!",
                    "A boda boda rider told me he never gets tired. I guess he has two-tireless!"
                )
                jokes[Random.nextInt(jokes.size)]
            }
            cmd.contains("share") -> { shareApp(context); "Opening WhatsApp to share." }
            containsAny(cmd, "strobe", "disco light") -> { strobeFlashlight(context); "Strobe activated." }
            
            else -> "Command not recognized. Please try again."
        }
    }

    // ✅ HELPER: Check for multiple synonyms
    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    // 🧮 MATH PARSER
    private fun calculateMath(expression: String): String {
        return try {
            val mathExpr = expression
                .replace("plus", "+").replace("add", "+")
                .replace("minus", "-").replace("subtract", "-")
                .replace("times", "*").replace("multiply", "*")
                .replace("divided by", "/").replace(" ", "")
            
            val match = Regex("([-+]?\\d*\\.?\\d+)([+\\-*/])([-+]?\\d*\\.?\\d+)").find(mathExpr)
            if (match != null) {
                val num1 = match.groupValues[1].toDouble()
                val op = match.groupValues[2]
                val num2 = match.groupValues[3].toDouble()
                val result = when(op) {
                    "+" -> num1 + num2
                    "-" -> num1 - num2
                    "*" -> num1 * num2
                    "/" -> if (num2 != 0.0) num1 / num2 else return "Cannot divide by zero."
                    else -> return "Invalid operation."
                }
                if (result % 1.0 == 0.0) "The answer is ${result.toInt()}" else "The answer is $result"
            } else { "Could not understand the math expression." }
        } catch (e: Exception) { "Math error." }
    }

    // 🔕 DND TOGGLE
    private fun toggleDnd(context: Context, enable: Boolean) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val filter = if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(filter)
            } else {
                // If we don't have permission, open the settings page for the user
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            }
        } catch (e: Exception) { }
    }

    // 🔆 BRIGHTNESS SETTINGS
    private fun openBrightnessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    // 📞 SMART CALLING (Fuzzy Match Names)
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
            } else {
                // Fuzzy match fallback
                val contacts = mutableListOf<Pair<String, String>>()
                val allCursor = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                if (allCursor != null) {
                    while (allCursor.moveToNext()) {
                        val contactName = allCursor.getString(0)
                        val number = allCursor.getString(1)
                        if (contactName != null) contacts.add(Pair(contactName, number))
                    }
                    allCursor.close()
                }
                
                var bestMatch: Pair<String, String>? = null
                var highestScore = 0.0
                for (contact in contacts) {
                    val score = calculateSimilarity(name, contact.first.lowercase())
                    if (score > highestScore) {
                        highestScore = score
                        bestMatch = contact
                    }
                }
                
                if (bestMatch != null && highestScore > 0.6) {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${bestMatch.second}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                    return "Calling ${bestMatch.first}."
                }
            }
        } catch (e: Exception) { }
        return "Could not find contact: $name."
    }

    // 📩 SMART SMS SENDING (Fuzzy Match Names)
    private fun sendSms(context: Context, name: String, message: String): String {
        try {
            // Try fuzzy match contact
            val contacts = mutableListOf<Pair<String, String>>()
            val allCursor = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (allCursor != null) {
                while (allCursor.moveToNext()) {
                    val contactName = allCursor.getString(0)
                    val number = allCursor.getString(1)
                    if (contactName != null) contacts.add(Pair(contactName, number))
                }
                allCursor.close()
            }
            
            var bestMatch: Pair<String, String>? = null
            var highestScore = 0.0
            for (contact in contacts) {
                val score = calculateSimilarity(name, contact.first.lowercase())
                if (score > highestScore) {
                    highestScore = score
                    bestMatch = contact
                }
            }
            
            val number = if (bestMatch != null && highestScore > 0.6) bestMatch.second else name
            
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            return "Message sent to $name."
        } catch (e: Exception) { return "Failed to send message." }
    }

    // 🚀 SMART APP LAUNCHER (Fuzzy Match)
    private fun openApp(context: Context, appName: String): String {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            var targetPackage: String? = null
            var exactMatch = false
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label == appName) { targetPackage = app.packageName; exactMatch = true; break }
                if (!exactMatch && label.contains(appName)) { targetPackage = app.packageName }
            }
            if (targetPackage != null) {
                val intent = pm.getLaunchIntentForPackage(targetPackage)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opening $appName."
                }
            }
        } catch (e: Exception) { }
        return "Could not find app: $appName."
    }

    // ⏰ ALARM HELPER
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

    // 🔦 HARDWARE
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

    // 🌐 WEB & CAMERA
    private fun browseWeb(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun openCamera(context: Context) {
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    // 📩 READ SMS
    private fun readLastSms(context: Context): String {
        try {
            val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("body"), null, null, "date DESC LIMIT 1")
            if (cursor != null && cursor.moveToFirst()) {
                val body = cursor.getString(0)
                cursor.close()
                return "Latest message: $body"
            }
        } catch (e: Exception) { }
        return "Could not read messages."
    }

    // 🎯 SHARE & STROBE
    private fun shareApp(context: Context) {
        try {
            val shareText = "Bro, you need this app! It controls my phone by voice. Download AutoNet here: https://bwireinnocentjohn-glitch.github.io/AutoNet/"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
                setPackage("com.whatsapp")
            }
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Download AutoNet here: https://bwireinnocentjohn-glitch.github.io/AutoNet/")
                type = "text/plain"
            }
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(sendIntent, "Share App").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    private fun strobeFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            Thread {
                try {
                    for (i in 1..10) {
                        cameraManager.setTorchMode(cameraId, true)
                        Thread.sleep(100)
                        cameraManager.setTorchMode(cameraId, false)
                        Thread.sleep(100)
                    }
                } catch (e: Exception) { }
            }.start()
        } catch (e: Exception) { }
    }

    // ✅ FUZZY MATCHING (Levenshtein Distance)
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
}
