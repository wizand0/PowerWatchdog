package ru.wizand.powerwatchdog.ui.settings

import android.app.Application
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
import android.content.Context

data class TestResult(val success: Boolean, val message: String)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PowerRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

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

    // Returns raw string for EditText
    fun getRawChatIdString(): String? = prefs.getString(Constants.PREF_TELEGRAM_CHAT_ID, null)

    // Returns parsed list of IDs
    fun getChatIdList(): List<String> {
        val raw = getRawChatIdString() ?: return emptyList()
        // Split by comma, semicolon, space, or newline
        return raw.split(",", ";", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun isTelegramEnabled(): Boolean = prefs.getBoolean(Constants.PREF_TELEGRAM_ENABLED, false)

    fun saveBotToken(token: String) {
        prefs.edit().putString(Constants.PREF_TELEGRAM_TOKEN, token).apply()
    }

    fun saveChatId(rawIds: String) {
        prefs.edit().putString(Constants.PREF_TELEGRAM_CHAT_ID, rawIds).apply()
    }

    fun setTelegramEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PREF_TELEGRAM_ENABLED, enabled).apply()
    }

    // Send test Telegram message to ALL configured Chat IDs
    suspend fun sendTestTelegramMessage(context: Context): TestResult {
        return withContext(Dispatchers.IO) {
            val token = getBotToken()
            val chatIds = getChatIdList()

            if (token.isNullOrEmpty() || chatIds.isEmpty()) {
                return@withContext TestResult(false, context.getString(R.string.bot_token_empty))
            }

            val message = context.getString(R.string.test_message)
            var successCount = 0
            var errors = StringBuilder()

            for (chatId in chatIds) {
                try {
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
                        conn.disconnect()
                        successCount++
                    } else {
                        val errorResponse = conn.errorStream?.bufferedReader()?.readText() ?: "No details"
                        Log.e("SettingsViewModel", "Error sending to $chatId: $responseCode - $errorResponse")

                        // Capture first error description
                        if (errors.isEmpty()) {
                            val userErrorMsg = when (responseCode) {
                                400 -> context.getString(R.string.telegram_invalid_chat_id)
                                401 -> context.getString(R.string.telegram_invalid_token)
                                403 -> context.getString(R.string.telegram_bot_blocked)
                                else -> "HTTP $responseCode"
                            }
                            errors.append(userErrorMsg)
                        }
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Net Error sending to $chatId", e)
                    if (errors.isEmpty()) errors.append(e.localizedMessage)
                }
            }

            if (successCount == chatIds.size) {
                return@withContext TestResult(true, context.getString(R.string.test_success))
            } else if (successCount > 0) {
                // Partial success
                return@withContext TestResult(true, "Sent to $successCount/${chatIds.size}. Error: $errors")
            } else {
                return@withContext TestResult(false, "Failed. Error: $errors")
            }
        }
    }

    suspend fun clearLog() {
        withContext(Dispatchers.IO) {
            repo.clearAll()
        }
    }
}