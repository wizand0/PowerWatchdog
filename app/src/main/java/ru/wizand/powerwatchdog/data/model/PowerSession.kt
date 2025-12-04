package ru.wizand.powerwatchdog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "power_sessions")
data class PowerSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long? = null,
    val durationSec: Long? = null
)