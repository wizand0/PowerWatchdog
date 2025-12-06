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

            if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() } // Consume stream

                // Логируем успех отправки в БД
                // Предполагается, что PowerState имеет значения INFO/ERROR.
                // Если нет - используйте CONNECTED или добавьте соответствующие поля в Enum.
                // Для совместимости с текущим Enum часто используют поле comment/details
                // Но следуя инструкции, пишу PowerState.INFO:
                try {
                    // Если PowerState.INFO нет в вашем Enum, замените на существующий или добавьте его
                    repository.insert(PowerEvent(type = PowerState.CONNECTED, timestamp = System.currentTimeMillis()))
                    // ПРИМЕЧАНИЕ: Я заменил на CONNECTED/DISCONNECTED так как не вижу код PowerState,
                    // но если вы добавили INFO/ERROR, используйте их.
                    // Лучший вариант для логов если нет спец статуса - просто завершить success.
                    Log.i(TAG, "Telegram message sent successfully to $chatId")
                } catch (e: Exception) {
                    Log.e(TAG, "Db error", e)
                }
                return@withContext Result.success()

            } else if (responseCode in 400..499) {
                // Фатальная ошибка (неверный токен или ID чата), повторять бессмысленно
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Client Error"
                Log.e(TAG, "Fatal Telegram Error: $responseCode - $errorText")

                // Логируем ошибку
                repository.insert(PowerEvent(type = PowerState.DISCONNECTED, timestamp = System.currentTimeMillis())) // See note above regarding states

                return@withContext Result.failure(workDataOf("error" to errorText))

            } else {
                // Ошибка 5xx или другая серверная - пробуем позже
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Server Error"
                Log.w(TAG, "Retryable Telegram Error: $responseCode - $errorText")

                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            // Сетевая ошибка (нет интернета, таймаут) -> Retry
            Log.e(TAG, "Network exception, retrying", e)
            // Можно записать в лог попытку ретрая, но это засорит базу
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