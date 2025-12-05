package ru.wizand.powerwatchdog.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import ru.wizand.powerwatchdog.utils.Constants

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: PowerRepository

    private val _powerState = MutableLiveData<PowerState>(PowerState.CONNECTED)
    val powerState: LiveData<PowerState> = _powerState

    private val _isServiceActive = MutableLiveData(false)
    val isServiceActive: LiveData<Boolean> = _isServiceActive

    // expose latest state from DB (Flow)
    val latestPowerState = AppDatabase.getInstance(application).powerEventDao()
        .getAllDesc()
        .map { list -> list.firstOrNull()?.type }

    init {
        val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)
        val startTs = prefs.getLong(Constants.PREF_SERVICE_START_TS, 0L)

        _isServiceActive.value = wasRunning

        // Если нужно — можно выставить состояние таймера
        // (например later передать startTs в UI через LiveData)

        val db = AppDatabase.getInstance(application)
        repo = PowerRepository(db.powerEventDao(), db.powerSessionDao())
        viewModelScope.launch {
            val last = db.powerEventDao().getAllDesc().firstOrNull()
            val state = last?.firstOrNull()?.type
            state?.let { _powerState.postValue(it) }
        }
    }

    fun setPowerState(state: PowerState) {
        _powerState.postValue(state)
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.postValue(active)
    }

    fun refreshServiceState() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        val running = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)
        val lastBeat = prefs.getLong("pref_last_heartbeat_ts", 0L)

        val alive = if (running) {
            val delta = System.currentTimeMillis() - lastBeat
            delta < 90_000 // сервис шлёт пульс каждые 30 сек → считаем живым ≤ 90 сек
        } else false

        _isServiceActive.postValue(alive)
    }
}