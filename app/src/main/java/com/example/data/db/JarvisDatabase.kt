package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CommandHistoryEntity::class,
        AutomationRuleEntity::class,
        SavedLocationEntity::class,
        MemoryNoteEntity::class,
        LearnedMacroEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun memoryNoteDao(): MemoryNoteDao
    abstract fun learnedMacroDao(): LearnedMacroDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_core.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
