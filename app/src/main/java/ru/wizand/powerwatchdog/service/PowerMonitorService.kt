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
import kotlinx.coroutines.isActive
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PowerMonitorService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: PowerRepository
    private var currentSessionId: Long? = null

    private val heartbeatJob = Job()
    private val heartbeatScope = CoroutineScope(Dispatchers.IO + heartbeatJob)

    data class SendResult(val success: Boolean, val message: String)

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
        startHeartbeat()

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
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = buildNotification(getString(R.string.text_need_permissions))
                nm.notify(Constants.NOTIFICATION_ID + 1, notification)
                prefs.edit().putBoolean("perm_warning_shown", true).apply()
            } else {
                prefs.edit().putBoolean("perm_warning_shown", false).apply()
            }
        }

        val notification = buildNotification(getString(R.string.monitor_is_active))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        scheduleWatchdog()

        prefs.edit().apply {
            putLong(Constants.PREF_SERVICE_START_TS, System.currentTimeMillis())
            putBoolean(Constants.PREF_SERVICE_RUNNING, true)
            apply()
        }

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

    private fun scheduleWatchdog() {
        val alarmIntent = Intent(this, WatchdogReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val interval = 5 * 60 * 1000L
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            pi
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatJob.cancel()

        currentSessionId?.let { id ->
            val endTs = System.currentTimeMillis()
            val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
            val duration = (endTs - startTs) / 1000
            serviceScope.launch {
                repository.closeSession(id, endTs, duration)
            }
        }

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(Constants.PREF_SERVICE_RUNNING, false)
            remove(Constants.PREF_SERVICE_START_TS)
            remove("current_session_id")
            remove("current_session_id_start")
            apply()
        }

        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun handlePowerState(context: Context, state: PowerState) {
        val ts = System.currentTimeMillis()

        serviceScope.launch {
            repository.insert(PowerEvent(type = state, timestamp = ts))
        }

        if (state == PowerState.CONNECTED) {
            serviceScope.launch {
                currentSessionId = repository.insertSession(PowerSession(startTs = ts))
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("current_session_id", currentSessionId!!)
                    .putLong("current_session_id_start", ts)
                    .apply()
            }
        } else if (state == PowerState.DISCONNECTED) {
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

            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(Constants.PREF_SOUND, true)) {
                try {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    RingtoneManager.getRingtone(applicationContext, uri).play()
                } catch (_: Exception) {}
            }
            if (prefs.getBoolean(Constants.PREF_VIBRATE, true)) {
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                } catch (_: Exception) {}
            }
        }

        // Notification logic
        val text = if (state == PowerState.CONNECTED) "ПИТАНИЕ: НОРМА" else "ПИТАНИЕ: ОТКЛЮЧЕНО!"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIFICATION_ID, buildNotification(text))

        // --- MULTI-CHAT TELEGRAM NOTIFICATION ---
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        serviceScope.launch(Dispatchers.IO) {
            val telegramEnabled = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)
            val botToken = prefs.getString(Constants.PREF_TELEGRAM_TOKEN, null)
            val rawChatIds = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, "") ?: ""

            // Parse Chat IDs
            val chatIds = rawChatIds.split(",", ";", " ", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (telegramEnabled && !botToken.isNullOrEmpty() && chatIds.isNotEmpty()) {
                try {
                    val formattedTime = java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(ts))
                    val message = if (state == PowerState.CONNECTED) {
                        getString(R.string.telegram_power_connected, formattedTime, android.os.Build.MODEL)
                    } else {
                        getString(R.string.telegram_power_disconnected, formattedTime, android.os.Build.MODEL)
                    }

                    // Send to all IDs
                    for (chatId in chatIds) {
                        sendTelegramMessage(botToken, chatId, message)
                    }
                } catch (e: Exception) {
                    Log.e("PowerMonitorService", "Error sending Telegram message", e)
                }
            }
        }
    }

    private fun sendTelegramMessage(token: String, chatId: String, message: String): SendResult {
        return try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }

            val postData = "chat_id=$chatId&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
            conn.outputStream.use { os ->
                DataOutputStream(os).use { dos ->
                    dos.writeBytes(postData)
                    dos.flush()
                }
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                // Read response to clear stream
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                SendResult(true, "OK")
            } else {
                val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e("PowerMonitorService", "Error sending to $chatId: $responseCode - $errorResponse")
                conn.disconnect()
                SendResult(false, "HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e("PowerMonitorService", "Net error sending to $chatId", e)
            SendResult(false, e.localizedMessage ?: "Error")
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
        // Close sessions and clean up
        currentSessionId?.let { id ->
            val endTs = System.currentTimeMillis()
            val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
            val duration = (endTs - startTs) / 1000
            serviceScope.launch {
                repository.closeSession(id, endTs, duration)
            }
        }

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

    private fun startHeartbeat() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        heartbeatScope.launch {
            while (isActive) {
                prefs.edit().putLong("pref_last_heartbeat_ts", System.currentTimeMillis()).commit()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
}