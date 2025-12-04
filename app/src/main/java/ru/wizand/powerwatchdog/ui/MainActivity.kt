package ru.wizand.powerwatchdog.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.databinding.ActivityMainBinding
import ru.wizand.powerwatchdog.utils.Constants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if service was killed and reset flags
        checkAndResetServiceFlags()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
    }

    private fun checkAndResetServiceFlags() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)

        // If prefs say service was running but we're in cold start (service not actually running),
        // it means the process was killed. Reset the flags.
        if (wasRunning) {
            // We assume on cold start the service is not running
            // (A more robust check would be to query ActivityManager, but this is simpler)
            prefs.edit().apply {
                putBoolean(Constants.PREF_SERVICE_RUNNING, false)
                remove(Constants.PREF_SERVICE_START_TS)
                apply()
            }
        }
    }
}