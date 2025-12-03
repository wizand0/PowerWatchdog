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

class HomeFragment : Fragment() {

    private var _vb: FragmentHomeBinding? = null
    private val vb get() = _vb!!
    private val vm: HomeViewModel by viewModels()

    private var isServiceBound = false
    private var serviceIntent: Intent? = null

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Observe power state (reactive)
        vm.powerState.observe(viewLifecycleOwner) { state ->
            if (state == PowerState.CONNECTED) {
                vb.cardIndicator.setCardBackgroundColor(requireContext().getColor(R.color.green_700))
                vb.tvIndicatorText.text = "ПИТАНИЕ НОРМА"
            } else {
                vb.cardIndicator.setCardBackgroundColor(requireContext().getColor(R.color.red_700))
                vb.tvIndicatorText.text = "АВАРИЯ! РАБОТА ОТ БАТАРЕИ"
            }
        }

        // Observe service status
        vm.isServiceActive.observe(viewLifecycleOwner) { active ->
            vb.tvServiceStatus.text = if (active) "Служба активна" else "Служба остановлена"
            vb.btnStartStop.text = if (active) "СТОП МОНИТОРИНГА" else "СТАРТ МОНИТОРИНГА"
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
    }

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
        try {
            requireActivity().unregisterReceiver(powerStatusReceiver)
        } catch (_: Exception) {}
        _vb = null
    }
}
