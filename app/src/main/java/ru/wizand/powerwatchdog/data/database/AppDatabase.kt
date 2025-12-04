package ru.wizand.powerwatchdog.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.wizand.powerwatchdog.data.dao.PowerEventDao
import ru.wizand.powerwatchdog.data.dao.PowerSessionDao
import ru.wizand.powerwatchdog.data.model.Converters
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerSession

@Database(entities = [PowerEvent::class, PowerSession::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun powerEventDao(): PowerEventDao
    abstract fun powerSessionDao(): PowerSessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS power_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startTs INTEGER NOT NULL, endTs INTEGER, durationSec INTEGER)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "power_watchdog_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = inst
                inst
            }
        }
    }
}