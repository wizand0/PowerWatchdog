package ru.wizand.powerwatchdog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import ru.wizand.powerwatchdog.utils.Constants

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val shouldRestart = prefs.getBoolean("pref_autorestart", false)

        Log.d("WatchdogReceiver", "run watchdog, shouldRestart=$shouldRestart")

        if (!shouldRestart) return

        val svcIntent = Intent(context, PowerMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }
    }
}
