package ru.wizand.powerwatchdog.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    fun format(ts: Long): String = sdf.format(Date(ts))
}