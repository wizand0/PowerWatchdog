package ru.wizand.powerwatchdog.service

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerSession
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import ru.wizand.powerwatchdog.worker.TelegramSendWorker
import java.util.concurrent.TimeUnit

class PowerMonitorService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: PowerRepository
    private var currentSessionId: Long? = null

    private val heartbeatJob = Job()
    private val heartbeatScope = CoroutineScope(Dispatchers.IO + heartbeatJob)

    private var wakeLock: PowerManager.WakeLock? = null

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

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PowerWatchdog:AlertLock")

        startHeartbeat()

        val db = AppDatabase.getInstance(this)
        repository = PowerRepository(db.powerEventDao(), db.powerSessionDao())

        // Close any open sessions
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
                val prefsInternal = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                prefsInternal.edit()
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
        val triggerTime = System.currentTimeMillis() + interval

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Для Android 12+ (API 31+) проверяем разрешение
                if (am.canScheduleExactAlarms()) {
                    // Разрешение есть — ставим точный будильник
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pi
                    )
                } else {
                    // Разрешения нет — ставим неточный будильник (он сохранит жизнь сервису, но не требует прав)
                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pi
                    )
                }
            } else {
                // Для старых версий Android (<12) ставим как обычно
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pi
                )
            }
        } catch (e: SecurityException) {
            // На случай, если разрешение было отозвано в момент выполнения
            Log.e("PowerMonitorService", "SecurityException when setting alarm", e)
            // Пытаемся поставить обычный неточный будильник как фолбэк
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pi
                )
            } catch (ex: Exception) {
                Log.e("PowerMonitorService", "Failed to set backup alarm", ex)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatJob.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

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
        // Захват WakeLock важен, чтобы успеть записать в БД и поставить задачу в очередь
        wakeLock?.acquire(60 * 1000L)

        serviceScope.launch {
            try {
                val ts = System.currentTimeMillis()

                // 1. Логика БД
                repository.insert(PowerEvent(type = state, timestamp = ts))

                if (state == PowerState.CONNECTED) {
                    currentSessionId = repository.insertSession(PowerSession(startTs = ts))
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putLong("current_session_id", currentSessionId!!)
                        .putLong("current_session_id_start", ts)
                        .apply()
                } else if (state == PowerState.DISCONNECTED) {
                    currentSessionId?.let { id ->
                        val startTs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).getLong("current_session_id_start", 0)
                        val duration = (ts - startTs) / 1000
                        repository.closeSession(id, ts, duration)
                        currentSessionId = null
                        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit()
                            .remove("current_session_id")
                            .remove("current_session_id_start")
                            .apply()
                    }
                }

                // 2. Локальные уведомления (Звук/Вибрация)
                if (state == PowerState.DISCONNECTED) {
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

                val text = if (state == PowerState.CONNECTED) "ПИТАНИЕ: НОРМА" else "ПИТАНИЕ: ОТКЛЮЧЕНО!"
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(Constants.NOTIFICATION_ID, buildNotification(text))

                // 3. TELEGRAM VIA WORKMANAGER
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val telegramEnabled = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)
                val botToken = prefs.getString(Constants.PREF_TELEGRAM_TOKEN, null)
                val rawChatIds = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, "") ?: ""

                val chatIds = rawChatIds.split(",", ";", " ", "\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (telegramEnabled && !botToken.isNullOrEmpty() && chatIds.isNotEmpty()) {
                    val formattedTime = java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(ts))
                    val message = if (state == PowerState.CONNECTED) {
                        getString(R.string.telegram_power_connected, formattedTime, android.os.Build.MODEL)
                    } else {
                        getString(R.string.telegram_power_disconnected, formattedTime, android.os.Build.MODEL)
                    }

                    // Создаем constraint: Требуется подключение к интернету
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val workManager = WorkManager.getInstance(applicationContext)

                    for (chatId in chatIds) {
                        val data = workDataOf(
                            TelegramSendWorker.KEY_BOT_TOKEN to botToken,
                            TelegramSendWorker.KEY_CHAT_ID to chatId,
                            TelegramSendWorker.KEY_MESSAGE to message
                        )

                        val request = OneTimeWorkRequest.Builder(TelegramSendWorker::class.java)
                            .setConstraints(constraints)
                            .setInputData(data)
                            // Если неудача, первый повтор через 10 сек, потом 20, 30...
                            .setBackoffCriteria(
                                BackoffPolicy.LINEAR,
                                10L,
                                TimeUnit.SECONDS
                            )
                            .addTag("telegram_send")
                            .build()

                        workManager.enqueue(request)
                    }
                }

            } catch (e: Exception) {
                Log.e("PowerMonitorService", "Error in handlePowerState", e)
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            }
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

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
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