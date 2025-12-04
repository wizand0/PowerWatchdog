package ru.wizand.powerwatchdog.service

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.BatteryManager
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
import ru.wizand.powerwatchdog.data.model.PowerSession
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.pm.ServiceInfo
import android.util.Log
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PowerMonitorService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: PowerRepository
    private var currentSessionId: Long? = null  // To track the current open session

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
        repository = PowerRepository(db.powerEventDao(), db.powerSessionDao())

        // Close any open sessions from previous runs
        serviceScope.launch {
            val allSessions = repository.getAllSessionsDesc().first()
            val openSessions = allSessions.filter { it.endTs == null }
            openSessions.forEach { session ->
                val endTs = System.currentTimeMillis()
                val duration = (endTs - session.startTs) / 1000
                repository.closeSession(session.id, endTs, duration)
            }
        }

        createNotificationChannel()

        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check permissions before starting foreground
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Show warning notification and log
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = buildNotification("Внимание: без разрешений система может принудительно закрыть приложение")
                nm.notify(Constants.NOTIFICATION_ID + 1, notification)  // Use a different ID for warning
                prefs.edit().putBoolean("perm_warning_shown", true).apply()
                // Service continues; do not stop
            } else {
                prefs.edit().putBoolean("perm_warning_shown", false).apply()
            }
        }

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

        // Save service start timestamp and running flag
        prefs.edit().apply {
            putLong(Constants.PREF_SERVICE_START_TS, System.currentTimeMillis())
            putBoolean(Constants.PREF_SERVICE_RUNNING, true)
            apply()
        }

        // Check initial power state and start session if connected
        val isConnected = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.let { it != 0 } ?: false
        if (isConnected) {
            val now = System.currentTimeMillis()
            serviceScope.launch {
                currentSessionId = repository.insertSession(PowerSession(startTs = now))
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("current_session_id", currentSessionId!!)
                    .putLong("current_session_id_start", now)
                    .apply()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Close any open session
        currentSessionId?.let { id ->
            val endTs = System.currentTimeMillis()
            val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
            val duration = (endTs - startTs) / 1000
            serviceScope.launch {
                repository.closeSession(id, endTs, duration)
            }
        }

        // Clear service running flags
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(Constants.PREF_SERVICE_RUNNING, false)
            remove(Constants.PREF_SERVICE_START_TS)
            remove("current_session_id")
            remove("current_session_id_start")
            apply()
        }

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

        if (state == PowerState.CONNECTED) {
            // Start a new session
            serviceScope.launch {
                currentSessionId = repository.insertSession(PowerSession(startTs = ts))
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("current_session_id", currentSessionId!!)
                    .putLong("current_session_id_start", ts)
                    .apply()
            }
        } else if (state == PowerState.DISCONNECTED) {
            // Close the current session
            currentSessionId?.let { id ->
                val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
                val duration = (ts - startTs) / 1000
                serviceScope.launch {
                    repository.closeSession(id, ts, duration)
                }
                currentSessionId = null
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove("current_session_id")
                    .remove("current_session_id_start")
                    .apply()
            }

            // Notification, sound, vibration logic for DISCONNECTED remains here
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

            // New: Telegram notification on DISCONNECTED
            serviceScope.launch(Dispatchers.IO) {
                val telegramEnabled = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)
                val botToken = prefs.getString(Constants.PREF_TELEGRAM_TOKEN, null)
                val chatId = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, null)
                if (telegramEnabled && !botToken.isNullOrEmpty() && !chatId.isNullOrEmpty()) {
                    try {
                        // Format message: Use string resource with placeholders
                        val formattedTime = java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(ts)) // Assumes TimeUtils.format is similar; adjust if needed
                        val message = getString(R.string.telegram_power_disconnected, formattedTime, android.os.Build.MODEL)
                        sendTelegramMessage(botToken, chatId, message)
                    } catch (e: Exception) {
                        Log.e("PowerMonitorService", "Error sending Telegram message", e)
                    }
                }
            }
        }

        val text = if (state == PowerState.CONNECTED)
            "ПИТАНИЕ: НОРМА"
        else
            "ПИТАНИЕ: ОТКЛЮЧЕНО!"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIFICATION_ID, buildNotification(text))
    }

    // New: Helper method to send Telegram message (async in IO)
    private fun sendTelegramMessage(token: String, chatId: String, message: String) {
        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                // Removed: setRequestProperty("Content-Length", "0")  // HttpURLConnection calculates it automatically
            }

            val postData = "chat_id=$chatId&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
            conn.outputStream.use { os ->
                DataOutputStream(os).use { dos ->
                    dos.writeBytes(postData)
                    dos.flush()
                }
            }
            val response = conn.inputStream.bufferedReader().readText()  // Consume response
            Log.d("PowerMonitorService", "Telegram response: $response")  // Optional: for debugging
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("PowerMonitorService", "Error sending Telegram message", e)
        }
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
        // Close any open session
        currentSessionId?.let { id ->
            val endTs = System.currentTimeMillis()
            val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
            val duration = (endTs - startTs) / 1000
            serviceScope.launch {
                repository.closeSession(id, endTs, duration)
            }
        }

        // Clear service running flags
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(Constants.PREF_SERVICE_RUNNING, false)
            remove(Constants.PREF_SERVICE_START_TS)
            remove("current_session_id")
            remove("current_session_id_start")
            apply()
        }

        stopForeground(true)
        stopSelf()
    }
}