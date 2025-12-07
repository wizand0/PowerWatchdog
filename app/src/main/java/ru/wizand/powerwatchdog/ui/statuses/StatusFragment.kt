package ru.wizand.powerwatchdog.ui.statuses

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.wizand.powerwatchdog.data.model.StatusItem
import ru.wizand.powerwatchdog.data.model.StatusState
import ru.wizand.powerwatchdog.databinding.FragmentStatusBinding
import ru.wizand.powerwatchdog.utils.*


class StatusFragment : Fragment() {

    private var _vb: FragmentStatusBinding? = null
    private val vb get() = _vb!!
    private lateinit var adapter: StatusAdapter
    private val checks = mutableListOf<StatusItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentStatusBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = StatusAdapter()
        vb.rvStatus.layoutManager = LinearLayoutManager(requireContext())
        vb.rvStatus.adapter = adapter

        prepareChecks()

        vb.btnCheck.setOnClickListener {
            runChecks()
        }
    }


    private fun prepareChecks() {
        val ctx = requireContext()
        val pkg = ctx.packageName
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager

        checks.clear()

        checks += StatusItem(
            title = "Исключение из оптимизации батареи",
            fixAction = { IntentHelper.openBatteryOptimizationSettings(requireContext()) }
        )

        checks += StatusItem(
            title = "Разрешение автозапуска",
            fixAction = { IntentHelper.openAutoStartSettings(requireContext()) }
        )

        checks += StatusItem(
            title = "Точное выполнение будильников (Exact Alarm)",
            fixAction = { IntentHelper.openExactAlarmSettings(requireContext()) }
        )

        checks += StatusItem(
            title = "Разрешения уведомлений",
            fixAction = { IntentHelper.openNotificationSettings(requireContext()) }
        )

        checks += StatusItem(
            title = "Фоновая активность приложения",
            fixAction = { IntentHelper.openBackgroundActivitySettings(requireContext()) }
        )

        adapter.setData(checks)
    }

    private fun runChecks() {

        vb.tvCheckingTitle.visibility = View.VISIBLE
        vb.progressChecking.visibility = View.VISIBLE

        vb.progressChecking.visibility = View.VISIBLE
        lifecycleScope.launch {
            checks.forEachIndexed { index, item ->

                adapter.updateStatus(index, StatusState.CHECKING)

                delay(1200) // красивая анимация проверки

                val result = performCheck(item)
                adapter.updateStatus(index, result)
            }



        }

        vb.tvCheckingTitle.visibility = View.GONE
        vb.progressChecking.visibility = View.GONE
    }

    private fun performCheck(item: StatusItem): StatusState {
        val ctx = requireContext()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = ctx.packageName

        return when (item.title) {

            "Исключение из оптимизации батареи" -> {
                if (pm.isIgnoringBatteryOptimizations(pkg)) StatusState.OK
                else StatusState.WARNING
            }

            "Разрешение автозапуска" -> {
                if (AutoStartDetector.hasAutoStartPermission(ctx)) StatusState.OK
                else StatusState.WARNING
            }

            "Точное выполнение будильников (Exact Alarm)" -> {
                if (ExactAlarmDetector.checkExactAlarm(ctx)) StatusState.OK
                else StatusState.WARNING
            }

            "Разрешения уведомлений" -> {
                if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) StatusState.OK
                else StatusState.WARNING
            }

            "Фоновая активность приложения" -> {
                if (!PowerManagerHelper.isRestricted(ctx)) StatusState.OK
                else StatusState.WARNING
            }

            else -> StatusState.UNKNOWN
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}
