package ru.wizand.powerwatchdog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ru.wizand.powerwatchdog.utils.Constants

class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val shouldRestart = prefs.getBoolean("pref_autorestart", false)
            Log.d("RestartServiceReceiver", "Received ${intent.action}, shouldRestart=$shouldRestart")
            if (!shouldRestart) return

            val svcIntent = Intent(context, PowerMonitorService::class.java)
            // On Android O+ use startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (e: Exception) {
            Log.e("RestartServiceReceiver", "Error in onReceive", e)
        }
    }
}
