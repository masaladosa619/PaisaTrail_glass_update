package com.paisatrail

import android.app.Application
import com.paisatrail.work.ReminderNotifications
import com.paisatrail.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaisaTrailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
        // The daily check is always scheduled; the worker itself does nothing unless
        // reminders are switched on in Settings.
        ReminderScheduler.schedule(this)
    }
}
