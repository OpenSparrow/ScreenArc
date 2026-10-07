package com.screenarc.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenarc.app.data.local.db.dao.CategoryDao
import com.screenarc.app.data.local.db.dao.OcrDao
import com.screenarc.app.data.local.db.dao.OrganizedHistoryDao
import com.screenarc.app.data.local.db.dao.RuleDao
import com.screenarc.app.data.local.db.dao.ScreenshotDao
import com.screenarc.app.data.local.db.entity.CategoryEntity
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.OrganizedHistoryEntity
import com.screenarc.app.data.local.db.entity.RuleEntity
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ScreenshotEntity::class,
        OcrTextEntity::class,
        RuleEntity::class,
        OrganizedHistoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun screenshotDao(): ScreenshotDao
    abstract fun ocrDao(): OcrDao
    abstract fun ruleDao(): RuleDao
    abstract fun organizedHistoryDao(): OrganizedHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rules` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `primaryKeywords` TEXT NOT NULL,
                        `secondaryKeywords` TEXT NOT NULL DEFAULT '',
                        `regexPattern` TEXT,
                        `targetCategoryId` INTEGER NOT NULL,
                        `targetCategoryName` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `onlyNewScreenshots` INTEGER NOT NULL,
                        `matchWholeWord` INTEGER NOT NULL,
                        `isRegexEnabled` INTEGER NOT NULL,
                        `secondaryThreshold` INTEGER NOT NULL,
                        `matchCount` INTEGER NOT NULL,
                        `lastRunTimestamp` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_priority` ON `rules` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_targetCategoryId` ON `rules` (`targetCategoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_isEnabled` ON `rules` (`isEnabled`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `organized_history` (
                        `id` TEXT NOT NULL,
                        `screenshotId` INTEGER NOT NULL,
                        `uri` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `targetFolder` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `success` INTEGER NOT NULL,
                        `ruleName` TEXT,
                        `triggerType` TEXT NOT NULL,
                        `matchedKeywords` TEXT NOT NULL DEFAULT '',
                        `matchReason` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_organized_history_timestamp` ON `organized_history` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_organized_history_categoryName` ON `organized_history` (`categoryName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_organized_history_triggerType` ON `organized_history` (`triggerType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_organized_history_ruleName` ON `organized_history` (`ruleName`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screenarc_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Prepopulate default categories on first database creation
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).categoryDao().insertCategories(
                                    CategoryEntity.DEFAULT_ENTITIES
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
