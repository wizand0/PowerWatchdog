package ru.wizand.powerwatchdog.data.database


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.wizand.powerwatchdog.data.dao.PowerEventDao
import ru.wizand.powerwatchdog.data.model.Converters
import ru.wizand.powerwatchdog.data.model.PowerEvent

@Database(entities = [PowerEvent::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun powerEventDao(): PowerEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "power_watchdog_db"
                ).build()
                INSTANCE = inst
                inst
            }
        }
    }
}