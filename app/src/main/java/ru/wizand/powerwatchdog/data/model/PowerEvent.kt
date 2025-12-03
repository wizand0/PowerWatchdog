package ru.wizand.powerwatchdog.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "power_events")
data class PowerEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: PowerState,
    val timestamp: Long
)

enum class PowerState {
    CONNECTED,
    DISCONNECTED
}