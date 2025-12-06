package ru.wizand.powerwatchdog.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.work.*
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import ru.wizand.powerwatchdog.worker.TelegramSendWorker
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PowerRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val db = AppDatabase.getInstance(application)
        repo = PowerRepository(db.powerEventDao(), db.powerSessionDao())
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean(Constants.PREF_SOUND, true)
    fun isVibrateEnabled(): Boolean = prefs.getBoolean(Constants.PREF_VIBRATE, true)

    fun setSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean(Constants.PREF_SOUND, enabled).apply()
    fun setVibrateEnabled(enabled: Boolean) = prefs.edit().putBoolean(Constants.PREF_VIBRATE, enabled).apply()

    fun getBotToken(): String = prefs.getString(Constants.PREF_TELEGRAM_TOKEN, "") ?: ""
    fun getRawChatIdString(): String = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, "") ?: ""
    fun isTelegramEnabled(): Boolean = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)

    fun saveBotToken(token: String) = prefs.edit().putString(Constants.PREF_TELEGRAM_TOKEN, token.trim()).apply()
    fun saveChatId(rawIds: String) = prefs.edit().putString(Constants.PREF_TELEGRAM_CHAT_ID, rawIds.trim()).apply()
    fun setTelegramEnabled(enabled: Boolean) = prefs.edit().putBoolean(Constants.PREF_TELEGRAM_ENABLED, enabled).apply()

    fun getChatIdList(): List<String> {
        val raw = getRawChatIdString()
        return raw.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Запускает Worker для отправки теста.
     * @return UUID первой задачи для отслеживания UI или null, если параметры неверны.
     */
    fun sendTestTelegramMessage(context: Context): UUID? {
        val token = getBotToken()
        val chatIds = getChatIdList()

        if (token.isEmpty() || chatIds.isEmpty()) return null

        val message = "Test message: ${android.os.Build.MODEL}"

        // Требование: Наличие интернета
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workManager = WorkManager.getInstance(context)
        var firstWorkId: UUID? = null

        // Ставим задачи для всех chat_id
        for ((index, chatId) in chatIds.withIndex()) {
            val data = workDataOf(
                TelegramSendWorker.KEY_BOT_TOKEN to token,
                TelegramSendWorker.KEY_CHAT_ID to chatId,
                TelegramSendWorker.KEY_MESSAGE to message
            )

            val request = OneTimeWorkRequest.Builder(TelegramSendWorker::class.java)
                .setConstraints(constraints)
                .setInputData(data)
                .addTag("telegram_test") // Тэг для группировки, если нужно
                .build()

            workManager.enqueue(request)

            if (index == 0) {
                firstWorkId = request.id
            }
        }

        return firstWorkId
    }

    suspend fun clearLog() {
        repo.clearAll()
    }
}