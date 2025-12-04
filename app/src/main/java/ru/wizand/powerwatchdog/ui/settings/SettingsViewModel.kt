package ru.wizand.powerwatchdog.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PowerRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    init {
        val db = AppDatabase.getInstance(application)
        repo = PowerRepository(db.powerEventDao(), db.powerSessionDao())
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean(Constants.PREF_SOUND, true)
    fun isVibrateEnabled(): Boolean = prefs.getBoolean(Constants.PREF_VIBRATE, true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PREF_SOUND, enabled).apply()
    }

    fun setVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PREF_VIBRATE, enabled).apply()
    }

    // New: Telegram-related methods
    fun getBotToken(): String? = prefs.getString(Constants.PREF_TELEGRAM_TOKEN, null)
    fun getChatId(): String? = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, null)
    fun isTelegramEnabled(): Boolean = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)

    fun saveBotToken(token: String) {
        prefs.edit().putString(Constants.PREF_TELEGRAM_TOKEN, token).apply()
    }

    fun saveChatId(id: String) {
        prefs.edit().putString(Constants.PREF_TELEGRAM_CHAT_ID, id).apply()
    }

    fun setTelegramEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PREF_TELEGRAM_ENABLED, enabled).apply()
    }

    suspend fun clearLog() {
        withContext(Dispatchers.IO) {
            repo.clearAll()
        }
    }
}