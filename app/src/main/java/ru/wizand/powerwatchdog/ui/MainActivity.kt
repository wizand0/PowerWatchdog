package ru.wizand.powerwatchdog.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.databinding.ActivityMainBinding
import ru.wizand.powerwatchdog.utils.Constants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQ_NOTIF = 1001  // Request code for notifications permission

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if service was killed and reset flags
        checkAndResetServiceFlags()

        // Check first run and show permissions dialog if needed
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val firstRunDone = prefs.getBoolean("pref_first_run_done", false)
        if (!firstRunDone) {
            showPermissionsDialog(prefs)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Handle WindowInsets to adjust padding for system bars
        val originalTopPadding = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sysBars.top + originalTopPadding, v.paddingRight, v.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun showPermissionsDialog(prefs: SharedPreferences) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissions_title))
            .setMessage(getString(R.string.permissions_message))
            .setPositiveButton(getString(R.string.permissions_grant)) { _, _ ->
                // Request POST_NOTIFICATIONS permission
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                // Mark first run as done
                prefs.edit().putBoolean("pref_first_run_done", true).apply()
            }
            .setCancelable(false)  // Prevent dismissal without action
            .create()
        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            prefs.edit()
                .putBoolean("pref_notifications_granted", granted)
                .apply()
        }
    }

    private fun checkAndResetServiceFlags() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)
        val auto = prefs.getBoolean("pref_autorestart", false)

        // Если сервис считался работающим, но мы в cold start — не сбрасываем,
        // потому что RestartServiceReceiver должен попытаться восстановить его при BOOT или POWER_CONNECTED.
        // Однако можно сбросить временные поля, если auto == false.
        if (!auto && wasRunning) {
            prefs.edit().apply {
                putBoolean(Constants.PREF_SERVICE_RUNNING, false)
                remove(Constants.PREF_SERVICE_START_TS)
                apply()
            }
        }
        // иначе — ничего не делаем: даём ресиверам/системе попытаться перезапустить сервис
    }
}