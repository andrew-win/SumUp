package com.andrewwin.sumup

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SumUpApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        runCatching { FirebaseApp.initializeApp(this) }
            .onFailure { e -> Log.e("SumUpApp", "Firebase init failed", e) }
        try {
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
                .let { config ->
                    WorkManager.initialize(this, config)
                }
        } catch (e: Exception) {
            Log.e("SumUpApp", "WorkManager already initialized or failed", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
