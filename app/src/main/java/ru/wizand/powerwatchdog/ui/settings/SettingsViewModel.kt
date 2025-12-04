package ru.wizand.powerwatchdog.ui.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import ru.wizand.powerwatchdog.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import ru.wizand.powerwatchdog.R

data class TestResult(val success: Boolean, val message: String)

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

    // Telegram-related methods
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

    // Send test Telegram message with result
    suspend fun sendTestTelegramMessage(context: Context): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                val token = getBotToken()
                val chatId = getChatId()
                if (token.isNullOrEmpty() || chatId.isNullOrEmpty()) {
                    return@withContext TestResult(false, context.getString(R.string.bot_token_empty))
                }

                val message = context.getString(R.string.test_message)
                val url = URL("https://api.telegram.org/bot$token/sendMessage")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 10000  // 10 seconds
                    readTimeout = 10000     // 10 seconds
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
                    val response = conn.inputStream.bufferedReader().readText()
                    Log.d("SettingsViewModel", "Telegram test response: $response")
                    conn.disconnect()
                    return@withContext TestResult(true, context.getString(R.string.test_success))
                } else {
                    val errorResponse = conn.errorStream?.bufferedReader()?.readText() ?: "Нет деталей ошибки"
                    Log.e("SettingsViewModel", "Error: HTTP $responseCode - $errorResponse")
                    val userErrorMsg = when (responseCode) {
                        400 -> context.getString(R.string.telegram_invalid_chat_id)
                        401 -> context.getString(R.string.telegram_invalid_token)
                        403 -> context.getString(R.string.telegram_bot_blocked)
                        else -> "${context.getString(R.string.telegram_api_error)}$errorResponse"
                    }
                    conn.disconnect()
                    return@withContext TestResult(false, userErrorMsg)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error sending test Telegram message", e)
                return@withContext TestResult(false, "${context.getString(R.string.telegram_network_error)}${e.localizedMessage ?: "Неизвестная ошибка"}")
            }
        }
    }

    suspend fun clearLog() {
        withContext(Dispatchers.IO) {
            repo.clearAll()
        }
    }
}