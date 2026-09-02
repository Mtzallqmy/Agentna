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
        AgentStateEntity::class,
        AutomationEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun agentStateDao(): AgentStateDao
    abstract fun automationDao(): AutomationDao

    companion object {
        const val DATABASE_NAME = "agentna_local.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE approvals ADD COLUMN argumentsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE approvals ADD COLUMN sourcePrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN fallbackProvider TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE agents ADD COLUMN fallbackModel TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS automations (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        type TEXT NOT NULL,
                        cronExpression TEXT NOT NULL,
                        agentId TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        lastRunAt TEXT,
                        nextRunAt TEXT,
                        lastStatus TEXT
                    )""".trimIndent()
                )
            }
        }

        @Volatile private var INSTANCE: AgentDatabase? = null

        fun getDatabase(context: Context): AgentDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AgentDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
        }
    }
}

typealias AppDatabase = AgentDatabase
