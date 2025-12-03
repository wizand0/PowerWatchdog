package ru.wizand.powerwatchdog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.wizand.powerwatchdog.data.model.PowerEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerEventDao {
    @Insert
    suspend fun insert(event: PowerEvent)

    @Query("DELETE FROM power_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM power_events ORDER BY timestamp DESC")
    fun getAllDesc(): Flow<List<PowerEvent>>
}