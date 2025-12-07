package ru.wizand.powerwatchdog.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

object AutoStartDetector {

    fun hasAutoStartPermission(context: Context): Boolean {

        // 1. Прямые запреты Android
        if (PowerManagerHelper.isRestricted(context)) return false

        // 2. Huawei / Honor
        if (isHuawei()) {
            return !PowerManagerHelper.isRestricted(context)
        }

        // 3. MIUI всегда требует ручного разрешения автозапуска
        if (isXiaomi()) {
            return false // считаем что нужно предупреждать пользователя
        }

        // 4. Oppo / Realme
        if (isOppoRealme()) {
            return !PowerManagerHelper.isRestricted(context)
        }

        // 5. Vivo
        if (isVivo()) {
            return !PowerManagerHelper.isRestricted(context)
        }

        // Если производитель нормальный — считаем что ок
        return true
    }

    fun isXiaomi() = Build.MANUFACTURER.equals("xiaomi", true) ||
            Build.BRAND.equals("redmi", true) ||
            Build.BRAND.equals("poco", true)

    fun isHuawei() = Build.MANUFACTURER.equals("huawei", true) ||
            Build.MANUFACTURER.equals("honor", true)

    fun isOppoRealme() = Build.MANUFACTURER.equals("oppo", true) ||
            Build.MANUFACTURER.equals("realme", true)

    fun isVivo() = Build.MANUFACTURER.equals("vivo", true)
}
