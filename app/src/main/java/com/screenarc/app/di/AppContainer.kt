package com.screenarc.app.di

import android.content.Context
import com.screenarc.app.data.AppSettingsRepository
import com.screenarc.app.data.CategoryRepository
import com.screenarc.app.data.OcrRepository
import com.screenarc.app.data.OcrRepositoryImpl
import com.screenarc.app.data.OrganizedHistoryRepository
import com.screenarc.app.data.RulesRepository
import com.screenarc.app.data.ScreenshotRepository
import com.screenarc.app.data.local.db.AppDatabase

interface AppContainer {
    val database: AppDatabase
    val settingsRepository: AppSettingsRepository
    val categoryRepository: CategoryRepository
    val historyRepository: OrganizedHistoryRepository
    val rulesRepository: RulesRepository
    val screenshotRepository: ScreenshotRepository
    val ocrRepository: OcrRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val settingsRepository: AppSettingsRepository by lazy {
        AppSettingsRepository(context)
    }

    override val historyRepository: OrganizedHistoryRepository by lazy {
        OrganizedHistoryRepository(context, database)
    }

    override val rulesRepository: RulesRepository by lazy {
        RulesRepository(context, database)
    }

    override val screenshotRepository: ScreenshotRepository by lazy {
        ScreenshotRepository(context, database, historyRepository)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(context)
    }

    override val ocrRepository: OcrRepository by lazy {
        OcrRepositoryImpl(
            context = context,
            database = database,
            settingsRepository = settingsRepository,
            screenshotRepository = screenshotRepository
        )
    }
}
