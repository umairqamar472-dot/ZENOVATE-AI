package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.NlveDao
import com.example.data.local.entity.CommandLogEntity
import com.example.data.local.entity.ProjectEntity

@Database(
    entities = [ProjectEntity::class, CommandLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NlveDatabase : RoomDatabase() {
    abstract fun nlveDao(): NlveDao

    companion object {
        @Volatile
        private var INSTANCE: NlveDatabase? = null

        fun getInstance(context: Context): NlveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NlveDatabase::class.java,
                    "nlve_video_editor.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
