package ru.wizand.powerwatchdog.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object IntentHelper {

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ------------ BATTERY OPTIMIZATION --------------
    fun openBatteryOptimizationSettings(context: Context) {
        val pkg = context.packageName

        if (AutoStartDetector.isXiaomi()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", pkg)
                    putExtra("package_label", pkg)
                })) return
        }

        if (AutoStartDetector.isHuawei()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                })) return
        }

        if (AutoStartDetector.isOppoRealme()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                    )
                })) return
        }

        if (AutoStartDetector.isVivo()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.vivo.abe",
                        "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                    )
                })) return
        }

        if (!tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            Toast.makeText(context, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------ AUTOSTART SETTINGS --------------
    fun openAutoStartSettings(context: Context) {

        if (AutoStartDetector.isXiaomi()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                })) return
        }

        if (AutoStartDetector.isHuawei()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                })) return
        }

        if (AutoStartDetector.isOppoRealme()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                })) return
        }

        if (AutoStartDetector.isVivo()) {
            if (tryStart(context, Intent().apply {
                    setClassName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                })) return
        }

        Toast.makeText(context, "Откройте настройки → Автозапуск", Toast.LENGTH_LONG).show()
    }

    // ------------ EXACT ALARM SETTINGS --------------
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!tryStart(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))) {
                Toast.makeText(context, "Не удалось открыть настройки будильников", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Точные будильники не требуются", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------ NOTIFICATIONS --------------
    fun openNotificationSettings(context: Context) {
        tryStart(context, Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        })
    }

    // ------------ BACKGROUND ACTIVITY --------------
    fun openBackgroundActivitySettings(context: Context) {
        tryStart(context, Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        })
    }
}
