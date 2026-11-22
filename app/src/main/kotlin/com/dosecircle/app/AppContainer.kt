package com.dosecircle.app

import android.app.Application
import com.dosecircle.app.billing.BillingRepository
import com.dosecircle.app.data.RoomDataStore
import com.dosecircle.app.export.Exporter
import com.dosecircle.app.reminders.ReminderScheduler
import com.dosecircle.core.AppSettings
import com.dosecircle.core.DataStore
import com.dosecircle.core.DoseService
import com.dosecircle.core.PlanTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/** Hand-wired dependencies: small enough that a DI framework would only add build time and failure modes. */
class AppContainer(val app: Application) {
    val store: DataStore = RoomDataStore(app)
    val doseService = DoseService(store)
    val scheduler = ReminderScheduler(app, store)
    val exporter = Exporter(app)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Emits whenever stored data or settings change, so every screen can refresh. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _settings = MutableStateFlow(store.settings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val billing = BillingRepository(app, appScope, this)

    fun tier(): PlanTier = _settings.value.effectiveTier(Instant.now())

    /** Runs blocking storage work off the main thread. */
    suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    suspend fun updateSettings(change: (AppSettings) -> AppSettings) {
        val next = io {
            val updated = change(store.settings())
            store.saveSettings(updated)
            updated
        }
        _settings.value = next
        notifyChanged()
    }

    fun notifyChanged() { _changes.tryEmit(Unit) }

    /** Rebuilds alarms after any change that affects reminders. */
    suspend fun reschedule() = io { scheduler.reschedule() }

    suspend fun afterDataChange() {
        reschedule()
        notifyChanged()
    }

    suspend fun eraseEverything() {
        io { scheduler.cancelEverything(); store.eraseEverything() }
        _settings.value = store.settings()
        notifyChanged()
    }

    fun start() {
        appScope.launch {
            reschedule()
            billing.refreshEntitlement()
        }
    }
}
