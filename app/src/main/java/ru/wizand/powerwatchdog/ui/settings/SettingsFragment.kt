package ru.wizand.powerwatchdog.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.databinding.FragmentSettingsBinding
import ru.wizand.powerwatchdog.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _vb: FragmentSettingsBinding? = null
    private val vb get() = _vb!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentSettingsBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Load prefs
        val prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        vb.switchSound.isChecked = prefs.getBoolean(Constants.PREF_SOUND, true)
        vb.switchVibrate.isChecked = prefs.getBoolean(Constants.PREF_VIBRATE, true)

        vb.switchSound.setOnCheckedChangeListener { _, isChecked ->
            vm.setSoundEnabled(isChecked)
        }
        vb.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            vm.setVibrateEnabled(isChecked)
        }

        vb.btnClearLog.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Очистить журнал")
                .setMessage("Вы уверены, что хотите удалить все записи журнала?")
                .setPositiveButton("Да") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        vm.clearLog()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        vb.cardAboutTitle.text = "О программе"
        vb.cardAboutText.text = "Power Watchdog v1.0. Инструмент главного инженера комплекса."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}
