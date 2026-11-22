package com.dosecircle.app

import android.app.Application
import com.dosecircle.app.reminders.Channels
import com.dosecircle.app.reminders.ReminderHost
import com.dosecircle.app.reminders.ReminderScheduler
import com.dosecircle.core.DataStore
import com.dosecircle.core.DoseService

class DoseCircleApp : Application(), ReminderHost {
    lateinit var container: AppContainer
        private set

    override val store: DataStore get() = container.store
    override val doseService: DoseService get() = container.doseService
    override val scheduler: ReminderScheduler get() = container.scheduler

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this)
        container.start()
    }
}
