package moe.lyniko.dreambreak.data.history

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [RuntimeStateEventEntity::class, PostponeDecisionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DreamBreakDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val DATABASE_NAME = "dream_break_history.db"

        @Volatile
        private var instance: DreamBreakDatabase? = null

        fun getInstance(context: Context): DreamBreakDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DreamBreakDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { database ->
                    instance = database
                }
            }
        }
    }
}
