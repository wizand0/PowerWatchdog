package ru.wizand.powerwatchdog.data.repository


import ru.wizand.powerwatchdog.data.dao.PowerEventDao
import ru.wizand.powerwatchdog.data.model.PowerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PowerRepository(private val dao: PowerEventDao) {
    suspend fun insert(event: PowerEvent) = withContext(Dispatchers.IO) {
        dao.insert(event)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    fun getAllDesc(): Flow<List<PowerEvent>> = dao.getAllDesc()
}