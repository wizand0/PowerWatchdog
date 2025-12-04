package ru.wizand.powerwatchdog.data.repository

import ru.wizand.powerwatchdog.data.dao.PowerEventDao
import ru.wizand.powerwatchdog.data.dao.PowerSessionDao
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PowerRepository(private val eventDao: PowerEventDao, private val sessionDao: PowerSessionDao) {
    // Existing methods for events
    suspend fun insert(event: PowerEvent) = withContext(Dispatchers.IO) {
        eventDao.insert(event)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        eventDao.deleteAll()
    }

    fun getAllEventsDesc(): Flow<List<PowerEvent>> = eventDao.getAllDesc()

    // New methods for sessions
    suspend fun insertSession(session: PowerSession): Long = withContext(Dispatchers.IO) {
        sessionDao.insert(session)
    }

    suspend fun closeSession(id: Long, endTs: Long, durationSec: Long) = withContext(Dispatchers.IO) {
        sessionDao.closeSession(id, endTs, durationSec)
    }

    fun getAllSessionsDesc(): Flow<List<PowerSession>> = sessionDao.getAllDesc()
}