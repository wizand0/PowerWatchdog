package ru.wizand.powerwatchdog.service

import android.Manifest
import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.pm.ServiceInfo

class PowerMonitorService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: PowerRepository

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> handlePowerState(context, PowerState.CONNECTED)
                Intent.ACTION_POWER_DISCONNECTED -> handlePowerState(context, PowerState.DISCONNECTED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getInstance(this)
        repository = PowerRepository(db.powerEventDao())

        createNotificationChannel()

        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = buildNotification("Мониторинг электросети активен")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {}
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun handlePowerState(context: Context, state: PowerState) {

        val ts = System.currentTimeMillis()

        serviceScope.launch {
            repository.insert(PowerEvent(type = state, timestamp = ts))
        }

        if (state == PowerState.DISCONNECTED) {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val playSound = prefs.getBoolean(Constants.PREF_SOUND, true)
            val doVibrate = prefs.getBoolean(Constants.PREF_VIBRATE, true)

            if (playSound) {
                try {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    RingtoneManager.getRingtone(applicationContext, uri).play()
                } catch (_: Exception) {}
            }

            if (doVibrate) {
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                500,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                } catch (_: Exception) {}
            }
        }

        val text = if (state == PowerState.CONNECTED)
            "ПИТАНИЕ: НОРМА"
        else
            "ПИТАНИЕ: ОТКЛЮЧЕНО!"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, Class.forName("ru.wizand.powerwatchdog.ui.MainActivity"))
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Power Watchdog")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_power)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    inner class LocalBinder : Binder() {
        fun getService(): PowerMonitorService = this@PowerMonitorService
    }

    fun stopServiceSelf() {
        stopForeground(true)
        stopSelf()
    }
}
