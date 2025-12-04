package ru.wizand.powerwatchdog.ui.home

import android.content.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ru.wizand.powerwatchdog.databinding.FragmentHomeBinding
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.service.PowerMonitorService
import ru.wizand.powerwatchdog.utils.Constants
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.wizand.powerwatchdog.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi

class HomeFragment : Fragment() {

    private var _vb: FragmentHomeBinding? = null
    private val vb get() = _vb!!
    private val vm: HomeViewModel by viewModels()
    private var serviceIntent: Intent? = null
    private var timerJob: Job? = null
    private var batteryTempJob: Job? = null

    private val powerStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                vm.setPowerState(PowerState.CONNECTED)
            } else if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                vm.setPowerState(PowerState.DISCONNECTED)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prepare service intent
        serviceIntent = Intent(requireContext(), PowerMonitorService::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentHomeBinding.inflate(inflater, container, false)
        return vb.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Observe power state (reactive)
        vm.powerState.observe(viewLifecycleOwner) { state ->
            if (state == PowerState.CONNECTED) {
                vb.cardIndicator.setCardBackgroundColor(requireContext().getColor(R.color.green_700))
                vb.tvIndicatorText.text = getString(R.string.indicator_power_normal)
            } else {
                vb.cardIndicator.setCardBackgroundColor(requireContext().getColor(R.color.red_700))
                vb.tvIndicatorText.text = getString(R.string.indicator_power_failure)
            }
        }

        // Observe service status
        vm.isServiceActive.observe(viewLifecycleOwner) { active ->
            vb.tvServiceStatus.text = if (active) getString(R.string.service_active) else getString(R.string.service_stopped)
            vb.btnStartStop.text = if (active) getString(R.string.btn_stop_monitoring) else getString(R.string.btn_start_monitoring)
        }

        vb.btnStartStop.setOnClickListener {
            if (vm.isServiceActive.value == true) {
                stopService()
            } else {
                startService()
            }
        }

        // Register local power connected/disconnected receiver to update indicator reactively (in addition to DB events)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        requireActivity().registerReceiver(powerStatusReceiver, filter)

        // Also subscribe to latest DB event just in case (keeps UI consistent)
        lifecycleScope.launch {
            vm.latestPowerState.collectLatest { state ->
                state?.let { vm.setPowerState(it) }
            }
        }

        // Start elapsed time counter
        startElapsedTimeCounter()

        // Start battery temperature updater
        startBatteryTempUpdater()
    }

    override fun onResume() {
        super.onResume()
        updateBatteryTemp()
    }

    private fun startElapsedTimeCounter() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

            while (isActive) {
                val isRunning = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)
                val startTs = prefs.getLong(Constants.PREF_SERVICE_START_TS, 0L)

                if (isRunning && startTs > 0) {
                    val elapsedMs = System.currentTimeMillis() - startTs
                    val elapsedSeconds = elapsedMs / 1000
                    vb.tvElapsed.text = formatElapsedTime(elapsedSeconds)
                    vb.tvElapsed.visibility = View.VISIBLE
                } else {
                    vb.tvElapsed.text = "00:00:00"
                    vb.tvElapsed.visibility = View.GONE
                }

                delay(1000)
            }
        }
    }

    private fun startBatteryTempUpdater() {
        batteryTempJob?.cancel()
        batteryTempJob = lifecycleScope.launch {
            while (isActive) {
                updateBatteryTemp()
                delay(30000) // update every 30 seconds
            }
        }
    }

    private fun updateBatteryTemp() {
        val intent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val c = temp / 10.0
        vb.tvBatteryTemp.text = getString(R.string.temperature_accum).format(c)
    }

    private fun formatElapsedTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startService() {
        val ctx = requireContext().applicationContext
        // start foreground service
        ctx.startForegroundService(serviceIntent)
        vm.setServiceActive(true)
    }

    private fun stopService() {
        val ctx = requireContext().applicationContext
        ctx.stopService(serviceIntent)
        vm.setServiceActive(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
        batteryTempJob?.cancel()
        try {
            requireActivity().unregisterReceiver(powerStatusReceiver)
        } catch (_: Exception) {}
        _vb = null
    }
}