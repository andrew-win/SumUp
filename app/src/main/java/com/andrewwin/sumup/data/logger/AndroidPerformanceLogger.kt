package com.andrewwin.sumup.data.logger

import android.util.Log
import com.andrewwin.sumup.domain.logger.PerformanceLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPerformanceLogger @Inject constructor() : PerformanceLogger {
    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }
}
