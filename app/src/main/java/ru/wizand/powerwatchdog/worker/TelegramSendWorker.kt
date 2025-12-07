package ru.wizand.powerwatchdog.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TelegramSendWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val botToken = inputData.getString(KEY_BOT_TOKEN)
        val chatId = inputData.getString(KEY_CHAT_ID)
        val message = inputData.getString(KEY_MESSAGE)

        if (botToken.isNullOrEmpty() || chatId.isNullOrEmpty() || message.isNullOrEmpty()) {
            return@withContext Result.failure(workDataOf("error" to "Missing params"))
        }

        val db = AppDatabase.getInstance(applicationContext)
        val repository = PowerRepository(db.powerEventDao(), db.powerSessionDao())

        try {
            val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val postData = "chat_id=$chatId&text=${java.net.URLEncoder.encode(message, "UTF-8")}"

                conn.outputStream.use { os ->
                    DataOutputStream(os).use { dos ->
                        dos.writeBytes(postData)
                        dos.flush()
                    }
                }

                val responseCode = conn.responseCode

                when (responseCode) {
                    200 -> {
                        conn.inputStream.bufferedReader().use { it.readText() }
                        repository.insert(PowerEvent(type = PowerState.CONNECTED, timestamp = System.currentTimeMillis()))
                        Log.i(TAG, "Telegram message sent successfully to $chatId")
                        return@withContext Result.success()

                    }
                    in 400..499 -> {
                        val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Client Error"
                        Log.e(TAG, "Fatal Telegram Error: $responseCode - $errorText")
                        repository.insert(PowerEvent(type = PowerState.DISCONNECTED, timestamp = System.currentTimeMillis()))
                        return@withContext Result.failure(workDataOf("error" to errorText))

                    }
                    else -> {
                        val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Server Error"
                        Log.w(TAG, "Retryable Telegram Error: $responseCode - $errorText")
                        return@withContext Result.retry()
                    }
                }

            } finally {
                // ← Здесь гарантия закрытия
                try {
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Network exception, retrying", e)
            return@withContext Result.retry()
        }

    }

    companion object {
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_MESSAGE = "message"
        private const val TAG = "TelegramWorker"
    }
}