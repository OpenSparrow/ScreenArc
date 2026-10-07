package com.screenarc.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.screenarc.app.data.local.db.AppDatabase
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito

class RoomMigrationTest {

    @Test
    fun testMigration1To2SqlExecution() {
        val mockDb = Mockito.mock(SupportSQLiteDatabase::class.java)
        AppDatabase.MIGRATION_1_2.migrate(mockDb)

        // Verify CREATE TABLE and CREATE INDEX were executed
        Mockito.verify(mockDb, Mockito.atLeastOnce()).execSQL(Mockito.contains("CREATE TABLE IF NOT EXISTS `rules`"))
        Mockito.verify(mockDb, Mockito.atLeastOnce()).execSQL(Mockito.contains("CREATE TABLE IF NOT EXISTS `organized_history`"))
        Mockito.verify(mockDb, Mockito.atLeastOnce()).execSQL(Mockito.contains("CREATE INDEX IF NOT EXISTS `index_rules_priority`"))
        Mockito.verify(mockDb, Mockito.atLeastOnce()).execSQL(Mockito.contains("CREATE INDEX IF NOT EXISTS `index_organized_history_timestamp`"))
    }
}
