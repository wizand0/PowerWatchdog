package ru.wizand.powerwatchdog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.wizand.powerwatchdog.data.model.PowerSession
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerSessionDao {
    @Insert
    suspend fun insert(session: PowerSession): Long

    @Query("UPDATE power_sessions SET endTs = :endTs, durationSec = :durationSec WHERE id = :id")
    suspend fun closeSession(id: Long, endTs: Long, durationSec: Long)

    @Query("SELECT * FROM power_sessions ORDER BY startTs DESC")
    fun getAllDesc(): Flow<List<PowerSession>>
}