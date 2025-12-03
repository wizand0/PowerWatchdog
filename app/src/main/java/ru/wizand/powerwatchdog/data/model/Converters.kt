package ru.wizand.powerwatchdog.data.model

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPowerState(state: PowerState): String = state.name

    @TypeConverter
    fun toPowerState(value: String): PowerState = PowerState.valueOf(value)
}