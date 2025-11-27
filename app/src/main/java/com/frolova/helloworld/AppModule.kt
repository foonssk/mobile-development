package com.frolova.helloworld

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singleton для базы данных
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().scoreDao() }

    // Singleton для сервиса курса золота
    single { CbrApiService(androidContext()) }

    // ViewModel
    viewModel { GameViewModel(get()) }
}