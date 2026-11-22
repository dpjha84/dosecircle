package com.dosecircle.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Persistence port. The Android app implements it with Room and SharedPreferences; tests use InMemoryDataStore. Calls are blocking: run them off the main thread. */
interface DataStore {
    fun recipients(): List<CareRecipient>
    fun saveRecipient(r: CareRecipient)
    /** Removes the person with their medications and history. */
    fun deleteRecipient(id: String)

    fun members(): List<CircleMember>
    fun saveMember(m: CircleMember)
    fun deleteMember(id: String)

    fun medications(recipientId: String? = null, includeArchived: Boolean = false): List<Medication>
    fun medication(id: String): Medication?
    fun saveMedication(m: Medication)
    fun deleteMedication(id: String)

    fun entries(recipientId: String, from: LocalDate, to: LocalDate): List<DoseEntry>
    fun entry(id: String): DoseEntry?
    fun entryByKey(occurrenceKey: String): DoseEntry?
    fun recentEntriesForMed(medicationId: String, since: Instant): List<DoseEntry>
    fun saveEntry(e: DoseEntry)
    fun deleteEntry(id: String)

    fun settings(): AppSettings
    fun saveSettings(s: AppSettings)

    fun eraseEverything()
}

class InMemoryDataStore : DataStore {
    private val recipients = ArrayList<CareRecipient>()
    private val members = ArrayList<CircleMember>()
    private val meds = ArrayList<Medication>()
    private val entries = ArrayList<DoseEntry>()
    private var settings = AppSettings()

    override fun recipients() = recipients.toList()
    override fun saveRecipient(r: CareRecipient) = upsert(recipients, r) { it.id == r.id }
    override fun deleteRecipient(id: String) {
        recipients.removeAll { it.id == id }; meds.removeAll { it.recipientId == id }; entries.removeAll { it.recipientId == id }
    }

    override fun members() = members.toList()
    override fun saveMember(m: CircleMember) = upsert(members, m) { it.id == m.id }
    override fun deleteMember(id: String) { members.removeAll { it.id == id } }

    override fun medications(recipientId: String?, includeArchived: Boolean) =
        meds.filter { (recipientId == null || it.recipientId == recipientId) && (includeArchived || !it.isArchived) }
    override fun medication(id: String) = meds.firstOrNull { it.id == id }
    override fun saveMedication(m: Medication) = upsert(meds, m) { it.id == m.id }
    override fun deleteMedication(id: String) { meds.removeAll { it.id == id }; entries.removeAll { it.medicationId == id } }

    override fun entries(recipientId: String, from: LocalDate, to: LocalDate) =
        entries.filter { it.recipientId == recipientId && it.dateKey >= from.toString() && it.dateKey <= to.toString() }
    override fun entry(id: String) = entries.firstOrNull { it.id == id }
    override fun entryByKey(occurrenceKey: String) = entries.firstOrNull { it.occurrenceKey == occurrenceKey }
    override fun recentEntriesForMed(medicationId: String, since: Instant) =
        entries.filter { it.medicationId == medicationId && !it.loggedAt.isBefore(since) }
    override fun saveEntry(e: DoseEntry) = upsert(entries, e) { it.id == e.id }
    override fun deleteEntry(id: String) { entries.removeAll { it.id == id } }

    override fun settings() = settings
    override fun saveSettings(s: AppSettings) { settings = s }
    override fun eraseEverything() { recipients.clear(); members.clear(); meds.clear(); entries.clear(); settings = AppSettings() }

    private fun <T> upsert(list: MutableList<T>, item: T, match: (T) -> Boolean) {
        val i = list.indexOfFirst(match)
        if (i >= 0) list[i] = item else list.add(item)
    }
}

data class LogRequest(
    val medicationId: String,
    val occurrenceKey: String,
    val dueLocal: LocalDateTime,
    val status: DoseStatus,
    val memberId: String,
    val overrideGuard: Boolean = false,
    val note: String? = null,
    val asNeeded: Boolean = false,
)

data class LogResult(
    val success: Boolean,
    val needsConfirmation: Boolean,
    val guard: GuardResult,
    val entry: DoseEntry?,
    val error: String?,
) {
    companion object {
        fun fail(error: String) = LogResult(false, false, GuardResult.CLEAR, null, error)
    }
}

class DoseService(private val store: DataStore, private val clock: () -> Instant = { Instant.now() }) {

    fun log(req: LogRequest): LogResult {
        if (req.status != DoseStatus.TAKEN && req.status != DoseStatus.SKIPPED) {
            return LogResult.fail("A dose can only be marked taken or skipped.")
        }
        val member = store.members().firstOrNull { it.id == req.memberId } ?: return LogResult.fail("Choose who is logging this dose.")
        if (!RolePolicy.canLogDoses(member.role)) return LogResult.fail("${member.name} can view doses but not log them.")
        val med = store.medication(req.medicationId) ?: return LogResult.fail("That medication no longer exists.")

        val now = clock()
        val existing = store.entryByKey(req.occurrenceKey)
        val recent = store.recentEntriesForMed(med.id, now.minus(DoseGuard.minInterval(med)).minus(Duration.ofHours(1)))
        val guard = DoseGuard.evaluate(med, req.occurrenceKey, req.status, now, existing, recent)
        if (guard.kind != GuardKind.NONE && !req.overrideGuard) return LogResult(false, true, guard, null, null)

        val wasTaken = existing?.status == DoseStatus.TAKEN
        val base = existing ?: DoseEntry(
            recipientId = med.recipientId, medicationId = med.id, occurrenceKey = req.occurrenceKey,
            dueLocal = req.dueLocal, isAsNeeded = req.asNeeded,
        )
        val entry = base.copy(
            status = req.status, loggedAt = now, loggedByMemberId = member.id,
            note = req.note?.trim()?.takeIf { it.isNotEmpty() } ?: base.note,
        )
        store.saveEntry(entry)

        val isTaken = req.status == DoseStatus.TAKEN
        if (isTaken != wasTaken) adjustSupply(med, if (isTaken) -1 else +1)
        return LogResult(true, false, guard, entry, null)
    }

    fun undo(entryId: String, memberId: String): Boolean {
        val member = store.members().firstOrNull { it.id == memberId }
        if (member == null || !RolePolicy.canLogDoses(member.role)) return false
        val entry = store.entry(entryId) ?: return false
        store.deleteEntry(entryId)
        if (entry.status == DoseStatus.TAKEN) store.medication(entry.medicationId)?.let { adjustSupply(it, +1) }
        return true
    }

    private fun adjustSupply(med: Medication, direction: Int) {
        val remaining = med.unitsRemaining ?: return
        store.saveMedication(med.copy(unitsRemaining = maxOf(0.0, remaining + direction * med.unitsPerDose)))
    }
}
