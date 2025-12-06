package ru.wizand.powerwatchdog.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.databinding.FragmentSettingsBinding
import ru.wizand.powerwatchdog.utils.Constants
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
        val prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        vb.switchSound.isChecked = prefs.getBoolean(Constants.PREF_SOUND, true)
        vb.switchVibrate.isChecked = prefs.getBoolean(Constants.PREF_VIBRATE, true)

        vb.switchSound.setOnCheckedChangeListener { _, isChecked -> vm.setSoundEnabled(isChecked) }
        vb.switchVibrate.setOnCheckedChangeListener { _, isChecked -> vm.setVibrateEnabled(isChecked) }

        vb.editBotToken.setText(vm.getBotToken())
        vb.editChatId.setText(vm.getRawChatIdString())
        vb.switchTelegram.isChecked = vm.isTelegramEnabled()
        validateTelegramSettings()

        vb.editBotToken.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                vm.saveBotToken(vb.editBotToken.text.toString())
                validateTelegramSettings()
            }
        }
        vb.editChatId.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                vm.saveChatId(vb.editChatId.text.toString())
                validateTelegramSettings()
            }
        }

        vb.switchTelegram.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && (!isBotTokenValid() || !isChatIdValid())) {
                vb.switchTelegram.isChecked = false
                vm.setTelegramEnabled(false)
                Toast.makeText(requireContext(), getString(R.string.telegram_enable_first), Toast.LENGTH_SHORT).show()
            } else {
                vm.setTelegramEnabled(isChecked)
            }
        }

        // --- ЛОГИКА ТЕСТА ЧЕРЕЗ WORKMANAGER ---
        vb.btnTestTelegram.setOnClickListener {
            // Принудительно сохраняем текущий ввод перед тестом
            vm.saveBotToken(vb.editBotToken.text.toString())
            vm.saveChatId(vb.editChatId.text.toString())
            validateTelegramSettings()

            if (!vm.isTelegramEnabled() || !isBotTokenValid() || !isChatIdValid()) {
                Toast.makeText(requireContext(), getString(R.string.telegram_enable_first), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Запускаем и получаем ID задачи
            val workId = vm.sendTestTelegramMessage(requireContext())

            if (workId != null) {
                vb.btnTestTelegram.isEnabled = false
                vb.btnTestTelegram.text = "Wait..." // Лучше вынести в ресурсы

                // Подписываемся на результат выполнения Worker-а
                WorkManager.getInstance(requireContext())
                    .getWorkInfoByIdLiveData(workId)
                    .observe(viewLifecycleOwner) { workInfo ->
                        if (workInfo == null) return@observe

                        when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                vb.btnTestTelegram.isEnabled = true
                                vb.btnTestTelegram.text = getString(R.string.settings_test_telegram)
                                Toast.makeText(requireContext(), getString(R.string.test_success), Toast.LENGTH_SHORT).show()
                            }
                            WorkInfo.State.FAILED -> {
                                vb.btnTestTelegram.isEnabled = true
                                vb.btnTestTelegram.text = getString(R.string.settings_test_telegram)
                                // Получаем ошибку из outputData, которую мы положили в Worker-е
                                val errorMsg = workInfo.outputData.getString("error") ?: "Unknown Error"
                                Toast.makeText(requireContext(), "Error: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                            WorkInfo.State.CANCELLED -> {
                                vb.btnTestTelegram.isEnabled = true
                                vb.btnTestTelegram.text = getString(R.string.settings_test_telegram)
                            }
                            else -> {
                                // ENQUEUED или RUNNING - ничего не делаем, ждем
                            }
                        }
                    }
            }
        }

        vb.btnClearLog.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_clear_log_title))
                .setMessage(getString(R.string.settings_clear_log_message))
                .setPositiveButton(getString(R.string.settings_clear_log_confirm)) { _, _ ->
                    // Используем lifecycleScope для корутин во фрагменте
                    lifecycleScope.launch(Dispatchers.IO) {
                        vm.clearLog()
                    }
                }
                .setNegativeButton(getString(R.string.settings_clear_log_cancel), null)
                .show()
        }

        setupAboutSection()
    }

    private fun setupAboutSection() {
        vb.cardAboutTitle.text = getString(R.string.settings_about_title)
        val version = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) { "Unknown" }

        val aboutText = getString(R.string.settings_about_text)
        // ... ваши ссылки ...
        // (код сокращен для краткости, оставьте как был)
        val githubUrl = "https://github.com/wizand0/PowerWatchdog"
        val feedbackUrl = "mailto:makandrei@gmail.com"

        val htmlContent = """
            $aboutText<br>
            <b>Ver:</b> $version<br><br>
            GitHub: <a href="$githubUrl">PowerWatchdog Repo</a><br>
            Feedback: <a href="$feedbackUrl">Написать предложение</a>
        """.trimIndent()

        vb.cardAboutText.text = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_COMPACT)
        vb.cardAboutText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    private fun isBotTokenValid(): Boolean = vb.editBotToken.text.toString().isNotBlank()
    private fun isChatIdValid(): Boolean = vb.editChatId.text.toString().isNotBlank()

    private fun validateTelegramSettings() {
        val isValid = isBotTokenValid() && isChatIdValid()
        vb.switchTelegram.isEnabled = isValid
        if (vm.isTelegramEnabled() && !isValid) {
            vm.setTelegramEnabled(false)
            vb.switchTelegram.isChecked = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}