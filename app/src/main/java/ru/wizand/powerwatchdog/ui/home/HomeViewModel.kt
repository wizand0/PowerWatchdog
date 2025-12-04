package ru.wizand.powerwatchdog.ui.home

import android.app.Application
import androidx.lifecycle.*
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.data.repository.PowerRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

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
        val db = AppDatabase.getInstance(application)
        repo = PowerRepository(db.powerEventDao(), db.powerSessionDao())
        viewModelScope.launch {
            // initialize UI with latest DB state if exists
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
}