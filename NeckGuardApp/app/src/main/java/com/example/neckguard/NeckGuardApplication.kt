package com.example.neckguard

import android.app.Application

class NeckGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Boot up our custom custom Crash Telemetry Engine immediately 
        // to catch any startup crashes.
        CrashReporter.initialize(this)
    }
}
