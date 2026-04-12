package com.example.neckguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PostureLog::class], version = 1, exportSchema = false)
abstract class NeckGuardDatabase : RoomDatabase() {
    abstract fun postureLogDao(): PostureLogDao

    companion object {
        @Volatile
        private var INSTANCE: NeckGuardDatabase? = null

        fun getDatabase(context: Context): NeckGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NeckGuardDatabase::class.java,
                    "neckguard_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
