package com.autonet.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.provider.ContactsContract
import java.util.Calendar
import java.util.Locale

object CommandRouter {
    fun execute(context: Context, text: String): String {
        val cmd = text.lowercase(Locale.getDefault()).trim()
        
        return when {
            cmd.contains("light on") || cmd.contains("turn on light") -> { toggleFlashlight(context, true); "Flashlight on." }
            cmd.contains("light off") || cmd.contains("turn off light") -> { toggleFlashlight(context, false); "Flashlight off." }
            cmd.contains("volume up") -> { adjustVolume(context, true); "Volume up." }
            cmd.contains("volume down") -> { adjustVolume(context, false); "Volume down." }
            cmd.contains("time") -> {
                val cal = Calendar.getInstance()
                "The time is ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}"
            }
            cmd.contains("battery") -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                "Battery is at ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            cmd.contains("read message") || cmd.contains("read sms") -> readLastSms(context)
            cmd.startsWith("call") -> {
                val name = cmd.replace("call", "").trim()
                callContact(context, name)
            }
            cmd.startsWith("open") -> {
                val appName = cmd.replace("open", "").trim()
                if (appName.contains("camera")) { openCamera(context); "Opening camera." } 
                else { openApp(context, appName) }
            }
            cmd.contains("calculate") || cmd.contains("plus") || cmd.contains("minus") || cmd.contains("times") -> {
                val expression = cmd.replace("calculate", "").replace("what is", "").trim()
                calculateMath(expression)
            }
            cmd.contains("search for") || cmd.contains("browse") -> {
                val query = cmd.replace("search for", "").replace("browse", "").trim()
                browseWeb(context, query)
                "Searching for \$query."
            }
            cmd.contains("take a photo") || cmd.contains("open camera") -> {
                openCamera(context)
                "Opening camera."
            }
            else -> "Command not recognized."
        }
    }

    private fun calculateMath(expression: String): String {
        return try {
            val mathExpr = expression
                .replace("plus", "+").replace("add", "+")
                .replace("minus", "-").replace("subtract", "-")
                .replace("times", "*").replace("multiply", "*")
                .replace("divided by", "/").replace(" ", "")
            
            val match = Regex("([-+]?\\\\d*\\\\.?\\\\d+)([+\\\\-*/])([-+]?\\\\d*\\\\.?\\\\d+)").find(mathExpr)
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
                if (result % 1.0 == 0.0) "The answer is \${result.toInt()}" else "The answer is \$result"
            } else { "Could not understand the math expression." }
        } catch (e: Exception) { "Math error." }
    }

    private fun browseWeb(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=\$query")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    private fun openCamera(context: Context) {
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
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

    private fun callContact(context: Context, name: String): String {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(0)
                cursor.close()
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:\$number")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
                return "Calling \$name."
            }
        } catch (e: Exception) { }
        return "Could not find contact: \$name."
    }

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
                    return "Opening \$appName."
                }
            }
        } catch (e: Exception) { }
        return "Could not find app: \$appName."
    }

    private fun readLastSms(context: Context): String {
        try {
            val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("body"), null, null, "date DESC LIMIT 1")
            if (cursor != null && cursor.moveToFirst()) {
                val body = cursor.getString(0)
                cursor.close()
                return "Latest message: \$body"
            }
        } catch (e: Exception) { }
        return "Could not read messages."
    }
}
