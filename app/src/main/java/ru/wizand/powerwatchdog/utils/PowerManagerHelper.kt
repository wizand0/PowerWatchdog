package ru.wizand.powerwatchdog.utils

import android.app.AppOpsManager
import android.content.Context
import android.os.Build

object PowerManagerHelper {

    fun isRestricted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        return try {
            val op = "android:run_any_in_background" // универсальный вариант

            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    op,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    op,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }

            mode == AppOpsManager.MODE_ERRORED ||
                    mode == AppOpsManager.MODE_IGNORED ||
                    mode == AppOpsManager.MODE_DEFAULT
        } catch (e: Exception) {
            false
        }
    }
}
