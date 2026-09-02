package com.mtzallqmy.agentna.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Local source of truth for Agentna. No application server is required. */
@Database(
    entities = [
        AgentEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ApprovalEntity::class,
        ExecutionLogEntity::class,
        AgentStateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun agentStateDao(): AgentStateDao

    companion object {
        const val DATABASE_NAME = "agentna_local.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE approvals ADD COLUMN argumentsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE approvals ADD COLUMN sourcePrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var INSTANCE: AgentDatabase? = null

        fun getDatabase(context: Context): AgentDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AgentDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}

typealias AppDatabase = AgentDatabase
