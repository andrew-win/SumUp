package com.andrewwin.sumup

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.andrewwin.sumup.data.repository.PublicSubscriptionsSyncManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SumUpApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var publicSubscriptionsSyncManager: PublicSubscriptionsSyncManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching { FirebaseApp.initializeApp(this) }
            .onFailure { e -> }
        applicationScope.launch {
            runCatching { publicSubscriptionsSyncManager.sync(force = true) }
        }
        try {
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
                .let { config ->
                    WorkManager.initialize(this, config)
                }
        } catch (e: Exception) {
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}






