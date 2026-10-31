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
import android.view.KeyEvent
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object CommandRouter {
    fun execute(context: Context, text: String): String {
        val cmd = text.lowercase(Locale.getDefault()).trim()
        
        val isLightWord = cmd.contains("light") || cmd.contains("torch") || cmd.contains("flash") || cmd.contains("bulb")
        val isOffWord = cmd.contains("off") || cmd.contains("stop") || cmd.contains("disable") || cmd.contains("close")

        if (isLightWord) {
            if (isOffWord) { toggleFlashlight(context, false); return "Flashlight off." } 
            else { toggleFlashlight(context, true); return "Flashlight on." }
        }

        if (cmd.contains("volume") || cmd.contains("sound")) {
            if (cmd.contains("up") || cmd.contains("increase") || cmd.contains("louder")) { adjustVolume(context, true); return "Volume up." }
            if (cmd.contains("down") || cmd.contains("decrease") || cmd.contains("quieter")) { adjustVolume(context, false); return "Volume down." }
        }

        if (cmd.contains("play") || cmd.contains("music") || cmd.contains("song")) {
            if (cmd.contains("stop") || cmd.contains("pause")) { dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE); return "Music paused." } 
            else { dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY); return "Playing music." }
        }
        
        if (cmd.contains("silent") || cmd.contains("mute") || cmd.contains("do not disturb") || cmd.contains("dnd")) {
            if (isOffWord || cmd.contains("normal") || cmd.contains("unmute")) { toggleDnd(context, false); return "Do Not Disturb is off." } 
            else { toggleDnd(context, true); return "Do Not Disturb is on." }
        }
        
        if (cmd.contains("time")) { val cal = Calendar.getInstance(); return "The time is ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}" }
        if (cmd.contains("date")) { val cal = Calendar.getInstance(); return "Today is ${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}" }
        if (cmd.contains("battery")) { val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager; return "Battery is at ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" }
        
        if (cmd.contains("read") && (cmd.contains("message") || cmd.contains("sms"))) return readLastSms(context)
        if (cmd.contains("send") && (cmd.contains("message") || cmd.contains("sms"))) {
            val parts = cmd.replace("send", "").replace("message", "").replace("sms", "").replace("to", "").trim().split(" ", limit = 2)
            if (parts.size == 2) { sendSms(context, parts[0], parts[1]); return "Sending message to ${parts[0]}." }
            return "Please say 'send message to [Name] saying [Message]'."
        }
        
        if (cmd.contains("call") || cmd.contains("dial")) { val name = cmd.replace("call", "").replace("dial", "").trim(); return callContact(context, name) }
        
        if (cmd.contains("alarm") || cmd.contains("wake me up")) {
            val time = extractTime(cmd)
            if (time != null) { setAlarm(context, time[0], time[1]); return "Alarm set for ${time[0]}:${time[1]}" }
            return "Please specify a time, e.g., 'set alarm for 7:30 am'."
        }
        
        if (cmd.contains("open") || cmd.contains("launch") || cmd.contains("start")) {
            val appName = cmd.replace("open", "").replace("launch", "").replace("start", "").trim()
            if (appName.isNotBlank()) return openApp(context, appName)
        }
        if (cmd.length > 2 && !cmd.contains(" ")) { val appCheck = openApp(context, cmd); if (!appCheck.contains("Could not find")) return appCheck }
        
        if (cmd.contains("calculate") || cmd.contains("plus") || cmd.contains("minus") || cmd.contains("times")) {
            val expression = cmd.replace("calculate", "").replace("what is", "").trim(); return calculateMath(expression)
        }
        
        if (cmd.contains("search") || cmd.contains("browse") || cmd.contains("google")) {
            val query = cmd.replace("search for", "").replace("search", "").replace("browse", "").replace("google", "").trim()
            if (query.isNotBlank()) { browseWeb(context, query); return "Searching for $query." }
        }
        
        if (cmd.contains("who made you")) return "I was engineered by Ajon Inno."
        if (cmd.contains("joke")) { val jokes = listOf("Why did the phone go to the doctor? Because it lost its contacts!", "What do you call a fake noodle? An impasta!", "A boda boda rider told me he never gets tired. I guess he has two-tireless!"); return jokes[Random.nextInt(jokes.size)] }
        
        return "Command not recognized."
    }

    private fun openApp(context: Context, appName: String): String {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
            var targetPackage: String? = null
            var exactMatch = false
            for (app in apps) {
                val label = app.loadLabel(pm).toString().lowercase()
                if (label == appName) { targetPackage = app.activityInfo.packageName; exactMatch = true; break }
                if (!exactMatch && label.contains(appName)) { targetPackage = app.activityInfo.packageName }
            }
            if (targetPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launchIntent); return "Opening $appName." }
            }
        } catch (e: Exception) { }
        return "Could not find app: $appName."
    }

    private fun sendSms(context: Context, name: String, message: String): String {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            var number = name
            if (cursor != null && cursor.moveToFirst()) { number = cursor.getString(0); cursor.close() }
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else { @Suppress("DEPRECATION") SmsManager.getDefault() }
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            return "Message sent."
        } catch (e: Exception) { return "Failed to send message." }
    }

    private fun callContact(context: Context, name: String): String {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(0); cursor.close()
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent); return "Calling $name."
            }
        } catch (e: Exception) { }
        return "Could not find contact: $name."
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try { val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)); audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)) } catch (e: Exception) { }
    }

    private fun adjustVolume(context: Context, up: Boolean) {
        try { val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER; audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) } catch (e: Exception) { }
    }

    private fun toggleDnd(context: Context, enable: Boolean) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val filter = if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(filter)
            } else { val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }; context.startActivity(intent) }
        } catch (e: Exception) { }
    }

    private fun toggleFlashlight(context: Context, turnOn: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id -> cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return false
            cameraManager.setTorchMode(cameraId, turnOn); true
        } catch (e: Exception) { false }
    }

    private fun browseWeb(context: Context, query: String) {
        try { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }; context.startActivity(intent) } catch (e: Exception) { }
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int) {
        try { val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR, hour); putExtra(AlarmClock.EXTRA_MINUTES, minute); putExtra(AlarmClock.EXTRA_SKIP_UI, true); flags = Intent.FLAG_ACTIVITY_NEW_TASK }; context.startActivity(intent) } catch (e: Exception) { }
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

    private fun calculateMath(expression: String): String {
        return try {
            val mathExpr = expression.replace("plus", "+").replace("add", "+").replace("minus", "-").replace("subtract", "-").replace("times", "*").replace("multiply", "*").replace("divided by", "/").replace(" ", "")
            val match = Regex("([-+]?\\d*\\.?\\d+)([+\\-*/])([-+]?\\d*\\.?\\d+)").find(mathExpr)
            if (match != null) {
                val num1 = match.groupValues[1].toDouble(); val op = match.groupValues[2]; val num2 = match.groupValues[3].toDouble()
                val result = when(op) { "+" -> num1 + num2; "-" -> num1 - num2; "*" -> num1 * num2; "/" -> if (num2 != 0.0) num1 / num2 else return "Cannot divide by zero."; else -> return "Invalid operation." }
                if (result % 1.0 == 0.0) "The answer is ${result.toInt()}" else "The answer is $result"
            } else { "Could not understand the math expression." }
        } catch (e: Exception) { "Math error." }
    }

    private fun readLastSms(context: Context): String {
        try { val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("body"), null, null, "date DESC LIMIT 1"); if (cursor != null && cursor.moveToFirst()) { val body = cursor.getString(0); cursor.close(); return "Latest message: $body" } } catch (e: Exception) { }
        return "Could not read messages."
    }
}
