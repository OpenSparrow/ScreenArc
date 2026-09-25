package com.screenarc.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenarc.app.data.local.db.dao.CategoryDao
import com.screenarc.app.data.local.db.dao.OcrDao
import com.screenarc.app.data.local.db.dao.ScreenshotDao
import com.screenarc.app.data.local.db.entity.CategoryEntity
import com.screenarc.app.data.local.db.entity.OcrTextEntity
import com.screenarc.app.data.local.db.entity.ScreenshotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ScreenshotEntity::class,
        OcrTextEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun screenshotDao(): ScreenshotDao
    abstract fun ocrDao(): OcrDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screenarc_database"
                )
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
