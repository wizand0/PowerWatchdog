package ru.wizand.powerwatchdog.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "power_events")
data class PowerEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: PowerState,
    val timestamp: Long,
    val message: String? = null // Новое поле для текста ошибки или деталей
)

enum class PowerState {
    CONNECTED,
    DISCONNECTED,
    ERROR, // Ошибка выполнения операции (например, сбой отправки в Telegram)
    INFO   // Информационное сообщение (например, старт сервиса)
}