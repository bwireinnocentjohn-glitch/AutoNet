package com.autonet.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.WindowManager

object SystemController {

    fun toggleFlashlight(context: Context, turnOn: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cameraManager.setTorchMode(cameraId, turnOn)
            true
        } catch (e: Exception) { false }
    }

    // 📞 CALL BY NAME
    fun callContactByName(context: Context, name: String): String {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(0)
                cursor.close()
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return "Calling $name."
            }
        } catch (e: Exception) { }
        return "Could not find contact: $name."
    }

    // 🔆 BRIGHTNESS
    fun setBrightness(context: Context, level: Float) {
        try {
            val activity = context as? android.app.Activity ?: return
            val window = activity.window
            val layout = window.attributes
            layout.screenBrightness = level.coerceIn(0.01f, 1.0f)
            window.attributes = layout
        } catch (e: Exception) { }
    }

    // 🔊 VOLUME
    fun adjustVolume(context: Context, up: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) { }
    }

    // 🚀 OPEN APP
    fun openApp(context: Context, appName: String): Boolean {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(appName.lowercase())) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    }
                }
            }
        } catch (e: Exception) { }
        return false
    }

    // 🌐 BROWSE URL
    fun browseUrl(context: Context, url: String) {
        try {
            val formattedUrl = if (!url.startsWith("http")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    fun openWifiSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
    }

    fun readLastSms(context: Context): String {
        try {
            val cursor = context.contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC")
            if (cursor != null && cursor.moveToFirst()) {
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                cursor.close()
                return "Latest message says: $body"
            }
        } catch (e: Exception) { }
        return "Could not read messages."
    }
}
