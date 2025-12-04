package ru.wizand.powerwatchdog.ui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import ru.wizand.powerwatchdog.data.database.AppDatabase
import ru.wizand.powerwatchdog.data.repository.PowerRepository

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PowerRepository

    val events = AppDatabase.getInstance(application).powerEventDao().getAllDesc().asLiveData()

    init {
        val db = AppDatabase.getInstance(application)
        repo = PowerRepository(db.powerEventDao(), db.powerSessionDao())
    }

    suspend fun clearAll() {
        repo.clearAll()
    }
}