package com.dosecircle.app.reminders

import com.dosecircle.core.DataStore
import com.dosecircle.core.DoseService

/** What the broadcast receivers need from the Application. Receivers can run in a cold process, so they never touch UI state. */
interface ReminderHost {
    val store: DataStore
    val doseService: DoseService
    val scheduler: ReminderScheduler
}
