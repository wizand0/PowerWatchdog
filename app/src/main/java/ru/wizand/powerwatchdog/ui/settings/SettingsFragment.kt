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
import kotlinx.coroutines.withContext
import ru.wizand.powerwatchdog.BuildConfig

class SettingsFragment : Fragment() {

    private var _vb: FragmentSettingsBinding? = null
    private val vb get() = _vb!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentSettingsBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Load existing prefs
        val prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        vb.switchSound.isChecked = prefs.getBoolean(Constants.PREF_SOUND, true)
        vb.switchVibrate.isChecked = prefs.getBoolean(Constants.PREF_VIBRATE, true)

        vb.switchSound.setOnCheckedChangeListener { _, isChecked ->
            vm.setSoundEnabled(isChecked)
        }
        vb.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            vm.setVibrateEnabled(isChecked)
        }

        // Load Telegram settings
        vb.editBotToken.setText(vm.getBotToken())
        vb.editChatId.setText(vm.getChatId())
        vb.switchTelegram.isChecked = vm.isTelegramEnabled()
        validateTelegramSettings() // Initial validation

        vb.editBotToken.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val token = vb.editBotToken.text.toString()
                vm.saveBotToken(token)
                validateTelegramSettings() // Validate after saving
            }
        }
        vb.editChatId.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val chatId = vb.editChatId.text.toString()
                vm.saveChatId(chatId)
                validateTelegramSettings() // Validate after saving
            }
        }
        vb.switchTelegram.setOnCheckedChangeListener { _, isChecked ->
            // Only allow enabling if both fields are non-empty
            if (isChecked && (!isBotTokenValid() || !isChatIdValid())) {
                vb.switchTelegram.isChecked = false
                vm.setTelegramEnabled(false)
                showValidationError()
            } else {
                vm.setTelegramEnabled(isChecked)
            }
        }

        vb.btnTestTelegram.setOnClickListener {
            val telegramEnabled = vm.isTelegramEnabled()
            val token = vm.getBotToken()
            val chatId = vm.getChatId()
            if (!telegramEnabled || token.isNullOrEmpty() || chatId.isNullOrEmpty()) {
                // More specific warning
                android.widget.Toast.makeText(requireContext(), getString(R.string.telegram_enable_first), android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Launch in Main scope to show toasts on UI thread
            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) { vm.sendTestTelegramMessage(requireContext()) }
                android.widget.Toast.makeText(
                    requireContext(),
                    result.message,
                    if (result.success) android.widget.Toast.LENGTH_SHORT else android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        vb.btnClearLog.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_clear_log_title))
                .setMessage(getString(R.string.settings_clear_log_message))
                .setPositiveButton(getString(R.string.settings_clear_log_confirm)) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        vm.clearLog()
                    }
                }
                .setNegativeButton(getString(R.string.settings_clear_log_cancel), null)
                .show()
        }

        vb.cardAboutTitle.text = getString(R.string.settings_about_title)
        val aboutText = getString(R.string.settings_about_text)
        val version = ru.wizand.powerwatchdog.BuildConfig.VERSION_NAME
        vb.cardAboutText.text = "$aboutText\nv$version"
    }

    private fun isBotTokenValid(): Boolean {
        return vb.editBotToken.text.toString().isNotBlank()
    }

    private fun isChatIdValid(): Boolean {
        return vb.editChatId.text.toString().isNotBlank()
    }

    private fun validateTelegramSettings() {
        val isValid = isBotTokenValid() && isChatIdValid()
        vb.switchTelegram.isEnabled = isValid
        if (vm.isTelegramEnabled() && !isValid) {
            vm.setTelegramEnabled(false)
            vb.switchTelegram.isChecked = false
        }
    }

    private fun showValidationError() {
        // Optional: Show a toast or snackbar if needed, but for now, just disable the switch
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}