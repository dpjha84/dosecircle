package com.dosecircle.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.exitProcess

// Dependency-free test runner so `./gradlew :core:check` needs nothing beyond the Kotlin stdlib.
private val tests = ArrayList<Pair<String, () -> Unit>>()
private fun test(name: String, body: () -> Unit) { tests.add(name to body) }
private fun isTrue(cond: Boolean, what: String) { if (!cond) throw AssertionError("Expected true: $what") }
private fun <T> eq(expected: T, actual: T, what: String) { if (expected != actual) throw AssertionError("$what: expected <$expected> but was <$actual>") }

private const val CHI = "America/Chicago"
private const val SEA = "America/Los_Angeles"
private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
private fun ldt(y: Int, m: Int, day: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, m, day, h, mi)
private fun at(tz: String, y: Int, m: Int, day: Int, h: Int, mi: Int = 0): Instant = ZoneTime.toInstant(ldt(y, m, day, h, mi), ZoneId.of(tz))
private val chi = ZoneId.of(CHI)

private fun med(name: String, recipientId: String, vararg hours: Int) = Medication(
    name = name, recipientId = recipientId, startDate = d(2026, 1, 1), timesOfDayMinutes = hours.map { it * 60 },
)
private fun raj() = CareRecipient(name = "Raj", timeZoneId = CHI)

private fun registerSchedule() {
    test("Daily schedule yields every time on every day") {
        val due = ScheduleExpander.expand(med("A", "r", 8, 20), d(2026, 11, 21), d(2026, 11, 22))
        eq(4, due.size, "count"); eq(ldt(2026, 11, 21, 8), due[0], "first"); eq(ldt(2026, 11, 22, 20), due[3], "last")
    }
    test("Times are de-duplicated, sorted and out-of-range values ignored") {
        val m = med("A", "r").copy(timesOfDayMinutes = listOf(1200, 480, 480, -5, 1440))
        val due = ScheduleExpander.expand(m, d(2026, 11, 21), d(2026, 11, 21))
        eq(2, due.size, "count"); eq(8, due[0].hour, "first"); eq(20, due[1].hour, "second")
    }
    test("Days of week honours the mask (Mon, Wed, Fri)") {
        val m = med("A", "r", 9).copy(kind = ScheduleKind.DAYS_OF_WEEK, daysOfWeekMask = (1 shl 1) or (1 shl 3) or (1 shl 5))
        val days = ScheduleExpander.expand(m, d(2026, 11, 16), d(2026, 11, 22)).map { it.dayOfWeek }
        eq(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), days, "days")
    }
    test("Sunday is bit 0") {
        val m = med("A", "r", 9).copy(kind = ScheduleKind.DAYS_OF_WEEK, daysOfWeekMask = 1)
        val days = ScheduleExpander.expand(m, d(2026, 11, 16), d(2026, 11, 22)).map { it.dayOfWeek }
        eq(listOf(DayOfWeek.SUNDAY), days, "sunday only")
    }
    test("Every N days anchors on the start date") {
        val m = med("A", "r", 9).copy(kind = ScheduleKind.EVERY_N_DAYS, intervalDays = 3, startDate = d(2026, 11, 1))
        eq(listOf(1, 4, 7, 10), ScheduleExpander.expand(m, d(2026, 11, 1), d(2026, 11, 10)).map { it.dayOfMonth }, "days")
    }
    test("Start and end dates bound the schedule") {
        val m = med("A", "r", 9).copy(startDate = d(2026, 11, 10), endDate = d(2026, 11, 12))
        eq(3, ScheduleExpander.expand(m, d(2026, 11, 1), d(2026, 11, 30)).size, "count")
    }
    test("As-needed has no doses; a stopped medicine without end date has none but one with an end date keeps its past") {
        eq(0, ScheduleExpander.expand(med("A", "r", 9).copy(kind = ScheduleKind.AS_NEEDED), d(2026, 11, 1), d(2026, 11, 30)).size, "prn")
        val stopped = med("B", "r", 9).copy(isArchived = true)
        eq(0, ScheduleExpander.expand(stopped, d(2026, 11, 1), d(2026, 11, 30)).size, "stopped")
        eq(3, ScheduleExpander.expand(stopped.copy(endDate = d(2026, 11, 3)), d(2026, 11, 1), d(2026, 11, 30)).size, "with end")
    }
    test("Schedule description reads naturally") {
        val m = med("A", "r", 8, 21)
        eq("Every day at 8:00 AM, 9:00 PM", ScheduleExpander.describe(m, false), "daily")
        eq("Mon, Fri at 08:00, 21:00", ScheduleExpander.describe(m.copy(kind = ScheduleKind.DAYS_OF_WEEK, daysOfWeekMask = (1 shl 1) or (1 shl 5)), true), "days")
    }
    test("Clock text uses a plain space and handles noon and midnight") {
        eq("12:00 AM", TimeText.clock(0, false), "midnight"); eq("12:30 PM", TimeText.clock(750, false), "noon")
        eq("11:59 PM", TimeText.clock(1439, false), "late"); eq("00:05", TimeText.clock(5, true), "24h")
    }
}

private fun registerTime() {
    test("DST gap moves forward and overlap uses the earlier instant") {
        eq(Instant.parse("2026-03-08T08:30:00Z"), ZoneTime.toInstant(ldt(2026, 3, 8, 2, 30), chi), "gap shifts by the gap length")
        eq(Instant.parse("2026-11-01T06:30:00Z"), ZoneTime.toInstant(ldt(2026, 11, 1, 1, 30), chi), "overlap -> first (CDT)")
        eq(Instant.parse("2026-11-21T14:00:00Z"), ZoneTime.toInstant(ldt(2026, 11, 21, 8), chi), "CST")
    }
    test("Stable ids are deterministic and spread out") {
        eq(DoseKeys.stableId("abc"), DoseKeys.stableId("abc"), "same")
        isTrue(DoseKeys.stableId("abc") != DoseKeys.stableId("abd"), "different"); isTrue(DoseKeys.stableId("x") > 0, "positive")
    }
    test("Keys ignore the default locale") {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("th-TH-u-nu-thai"))
            eq("m1|202611210800", DoseKeys.make("m1", ldt(2026, 11, 21, 8)), "key")
            eq("2026-11-21", DoseEntry(dueLocal = ldt(2026, 11, 21, 8)).dateKey, "date key")
            eq("8:00 AM", TimeText.clock(480, false), "clock")
        } finally { java.util.Locale.setDefault(old) }
    }
}

private fun registerGuard() {
    test("Auto minimum interval: twice daily is half the gap, once daily 8h, as-needed 4h, custom wins") {
        eq(Duration.ofHours(6), DoseGuard.minInterval(med("A", "r", 8, 20)), "bid")
        eq(Duration.ofHours(8), DoseGuard.minInterval(med("A", "r", 8)), "daily")
        eq(Duration.ofHours(4), DoseGuard.minInterval(med("A", "r", 8).copy(kind = ScheduleKind.AS_NEEDED)), "prn")
        eq(Duration.ofMinutes(90), DoseGuard.minInterval(med("A", "r", 8).copy(minIntervalMinutes = 90)), "custom")
    }
    test("Guard flags a repeat of the same dose and a too-soon dose, but never a skip") {
        val m = med("A", "r", 8, 20); val now = Instant.parse("2026-11-21T18:00:00Z")
        val taken = DoseEntry(medicationId = m.id, occurrenceKey = "k1", status = DoseStatus.TAKEN, loggedAt = now.minusSeconds(20 * 60))
        eq(GuardKind.ALREADY_LOGGED, DoseGuard.evaluate(m, "k1", DoseStatus.TAKEN, now, taken, listOf(taken)).kind, "same dose")
        eq(GuardKind.TOO_SOON, DoseGuard.evaluate(m, "k2", DoseStatus.TAKEN, now, null, listOf(taken)).kind, "too soon")
        eq(GuardKind.NONE, DoseGuard.evaluate(m, "k1", DoseStatus.SKIPPED, now, taken, listOf(taken)).kind, "skip")
        eq(GuardKind.NONE, DoseGuard.evaluate(m, "k2", DoseStatus.TAKEN, now.plusSeconds(7 * 3600), null, listOf(taken)).kind, "later is fine")
    }
}

private class Fixture(start: Instant) {
    var now: Instant = start
    val store = InMemoryDataStore()
    val raj = CareRecipient(name = "Raj", timeZoneId = CHI)
    val priya = CircleMember.create("Priya", CircleRole.PRIMARY_CAREGIVER, true, CHI)
    val maria = CircleMember.create("Maria", CircleRole.AIDE, false, CHI)
    val viewer = CircleMember.create("Arun", CircleRole.VIEWER, false, SEA)
    val med = med("Metformin", raj.id, 8, 20).copy(strength = "500 mg", unitsRemaining = 10.0, unitsPerDose = 1.0)
    val svc = DoseService(store) { now }
    init {
        store.saveRecipient(raj); listOf(priya, maria, viewer).forEach(store::saveMember); store.saveMedication(med)
    }
    fun req(due: LocalDateTime, who: CircleMember, s: DoseStatus = DoseStatus.TAKEN, over: Boolean = false) =
        LogRequest(med.id, DoseKeys.make(med.id, due), due, s, who.id, over)
    fun supply() = store.medication(med.id)!!.unitsRemaining
}

private fun registerService() {
    val due8 = ldt(2026, 11, 21, 8)
    val t0 = Instant.parse("2026-11-21T14:04:00Z")
    test("Logging stores who gave the dose and reduces supply") {
        val f = Fixture(t0); val r = f.svc.log(f.req(due8, f.maria))
        isTrue(r.success, "success"); eq(f.maria.id, r.entry!!.loggedByMemberId, "who"); eq(9.0, f.supply(), "supply")
    }
    test("A second person logging the same dose is stopped and told who already gave it") {
        val f = Fixture(t0); f.svc.log(f.req(due8, f.maria)); f.now = Instant.parse("2026-11-21T14:30:00Z")
        val r = f.svc.log(f.req(due8, f.priya))
        isTrue(!r.success && r.needsConfirmation, "needs confirmation"); eq(GuardKind.ALREADY_LOGGED, r.guard.kind, "kind")
        eq(f.maria.id, r.guard.conflict!!.loggedByMemberId, "owner"); eq(9.0, f.supply(), "supply unchanged")
    }
    test("Override after confirmation is allowed but does not double-count supply") {
        val f = Fixture(t0); f.svc.log(f.req(due8, f.maria))
        isTrue(f.svc.log(f.req(due8, f.priya, over = true)).success, "override"); eq(9.0, f.supply(), "still 9")
    }
    test("Too-soon dose from a different slot needs confirmation") {
        val f = Fixture(t0); f.svc.log(f.req(due8, f.maria)); f.now = Instant.parse("2026-11-21T16:00:00Z")
        eq(GuardKind.TOO_SOON, f.svc.log(f.req(ldt(2026, 11, 21, 20), f.priya)).guard.kind, "too soon")
    }
    test("View-only and unknown members cannot log") {
        val f = Fixture(t0)
        isTrue(!f.svc.log(f.req(due8, f.viewer)).success, "viewer")
        isTrue(!f.svc.log(f.req(due8, CircleMember.create("Ghost", CircleRole.CAREGIVER, false))).success, "unknown")
    }
    test("Skipping then taking replaces the entry; undo restores supply") {
        val f = Fixture(t0); val s = f.svc.log(f.req(due8, f.priya, DoseStatus.SKIPPED)); eq(10.0, f.supply(), "skip keeps supply")
        val tk = f.svc.log(f.req(due8, f.priya)); eq(s.entry!!.id, tk.entry!!.id, "same entry")
        eq(DoseStatus.TAKEN, f.store.entry(tk.entry.id)!!.status, "now taken"); eq(9.0, f.supply(), "9")
        isTrue(f.svc.undo(tk.entry.id, f.priya.id), "undo"); eq(10.0, f.supply(), "back"); isTrue(f.store.entry(tk.entry.id) == null, "gone")
    }
    test("Supply never goes below zero") {
        val f = Fixture(t0); f.store.saveMedication(f.med.copy(unitsRemaining = 0.5)); f.svc.log(f.req(due8, f.priya)); eq(0.0, f.supply(), "floor")
    }
}

private fun registerDay() {
    test("Slot status follows the clock: upcoming, due, missed") {
        val r = raj(); val m = med("A", r.id, 8); val s = ReminderSettings()
        fun state(h: Int, mi: Int) = DoseSlotBuilder.buildDay(r, listOf(m), emptyList(), d(2026, 11, 21), at(CHI, 2026, 11, 21, h, mi), s).single().state
        eq(SlotState.UPCOMING, state(7, 30), "7:30"); eq(SlotState.DUE, state(7, 50), "7:50")
        eq(SlotState.DUE, state(8, 59), "8:59"); eq(SlotState.MISSED, state(9, 0), "9:00")
    }
    test("Doses at the same time group into one slot; summary counts them") {
        val r = raj(); val a = med("A", r.id, 8); val b = med("B", r.id, 8); val c = med("C", r.id, 13)
        val e = DoseEntry(recipientId = r.id, medicationId = a.id, occurrenceKey = DoseKeys.make(a.id, ldt(2026, 11, 21, 8)), status = DoseStatus.TAKEN)
        val slots = DoseSlotBuilder.buildDay(r, listOf(a, b, c), listOf(e), d(2026, 11, 21), at(CHI, 2026, 11, 21, 8, 10), ReminderSettings())
        eq(2, slots.size, "slots"); eq(2, slots[0].items.size, "items"); eq(1, slots[0].pendingCount, "pending")
        val sum = DoseSlotBuilder.summarize(slots); eq(3, sum.total, "total"); eq(1, sum.taken, "taken"); eq(2, sum.pending, "pending")
    }
    test("A fully logged slot is Done") {
        val r = raj(); val a = med("A", r.id, 8)
        val e = DoseEntry(recipientId = r.id, medicationId = a.id, occurrenceKey = DoseKeys.make(a.id, ldt(2026, 11, 21, 8)), status = DoseStatus.SKIPPED)
        eq(SlotState.DONE, DoseSlotBuilder.buildDay(r, listOf(a), listOf(e), d(2026, 11, 21), at(CHI, 2026, 11, 21, 12), ReminderSettings()).single().state, "done")
    }
    test("As-needed items report today's count and the last dose") {
        val r = raj(); val p = med("Ibuprofen", r.id).copy(kind = ScheduleKind.AS_NEEDED)
        val e1 = DoseEntry(recipientId = r.id, medicationId = p.id, occurrenceKey = "p1", dueLocal = ldt(2026, 11, 21, 9), isAsNeeded = true, loggedAt = Instant.parse("2026-11-21T15:00:00Z"))
        val e2 = e1.copy(id = "x", occurrenceKey = "p2", dueLocal = ldt(2026, 11, 20, 9), loggedAt = Instant.parse("2026-11-20T15:00:00Z"))
        val item = DoseSlotBuilder.buildAsNeeded(r, listOf(p), listOf(e1, e2), d(2026, 11, 21)).single()
        eq(1, item.takenToday, "today"); eq(e1.loggedAt, item.lastTakenAt, "last")
    }
}

private fun registerEscalation() {
    test("Escalation timing follows the chain; critical medicines escalate twice as fast") {
        val priya = CircleMember.create("Priya", CircleRole.PRIMARY_CAREGIVER, false, CHI); val arun = CircleMember.create("Arun", CircleRole.CAREGIVER, false, SEA)
        val pol = EscalationPolicy(30, 15, 0.5, listOf(priya.id, arun.id)); val due = at(CHI, 2026, 11, 21, 13); val rs = ReminderSettings(quietHoursEnabled = false)
        val n = EscalationPlanner.plan(due, pol, false, PlanTier.PRO, listOf(priya, arun), rs)
        eq(due.plusSeconds(30 * 60), n[0].trigger, "step 1"); eq(due.plusSeconds(45 * 60), n[1].trigger, "step 2")
        val c = EscalationPlanner.plan(due, pol, true, PlanTier.PRO, listOf(priya, arun), rs)
        eq(due.plusSeconds(15 * 60), c[0].trigger, "critical 1"); eq(due.plusSeconds(23 * 60), c[1].trigger, "critical 2 (22.5 rounds up)")
    }
    test("Free plan escalates to one caregiver only; members who opted out are skipped") {
        val priya = CircleMember.create("Priya", CircleRole.PRIMARY_CAREGIVER, false, CHI); val aide = CircleMember.create("Maria", CircleRole.AIDE, false, CHI)
        val arun = CircleMember.create("Arun", CircleRole.CAREGIVER, false, SEA)
        val pol = EscalationPolicy(chainMemberIds = listOf(aide.id, priya.id, arun.id)); val due = at(CHI, 2026, 11, 21, 13); val rs = ReminderSettings(quietHoursEnabled = false)
        val free = EscalationPlanner.plan(due, pol, false, PlanTier.FREE, listOf(priya, aide, arun), rs)
        eq(1, free.size, "free"); eq(priya.id, free[0].memberId, "first valid")
        eq(2, EscalationPlanner.plan(due, pol, false, PlanTier.PRO, listOf(priya, aide, arun), rs).size, "pro")
    }
    test("Quiet hours defer routine alerts to morning but never critical ones") {
        val priya = CircleMember.create("Priya", CircleRole.PRIMARY_CAREGIVER, false, CHI)
        val pol = EscalationPolicy(firstAlertAfterMinutes = 30, chainMemberIds = listOf(priya.id)); val due = at(CHI, 2026, 11, 21, 22, 30)
        eq(at(CHI, 2026, 11, 22, 7), EscalationPlanner.plan(due, pol, false, PlanTier.PRO, listOf(priya), ReminderSettings()).single().trigger, "deferred")
        eq(due.plusSeconds(15 * 60), EscalationPlanner.plan(due, pol, true, PlanTier.PRO, listOf(priya), ReminderSettings()).single().trigger, "critical")
    }
    test("Quiet hours: early morning defers to same morning; daytime untouched; same-day window works") {
        eq(at(CHI, 2026, 11, 21, 7), QuietHours.defer(at(CHI, 2026, 11, 21, 3), chi, 22 * 60, 7 * 60), "3am -> 7am")
        val noon = at(CHI, 2026, 11, 21, 12)
        eq(noon, QuietHours.defer(noon, chi, 22 * 60, 7 * 60), "noon"); eq(at(CHI, 2026, 11, 21, 15), QuietHours.defer(noon, chi, 11 * 60, 15 * 60), "same-day window")
    }
}

private class Family(val tier: PlanTier) {
    val priya = CircleMember.create("Priya", CircleRole.PRIMARY_CAREGIVER, true, CHI)
    val arun = CircleMember.create("Arun", CircleRole.CAREGIVER, false, SEA)
    val maria = CircleMember.create("Maria", CircleRole.AIDE, false, CHI)
    val raj = CareRecipient(name = "Raj", timeZoneId = CHI, escalation = EscalationPolicy(chainMemberIds = listOf(priya.id, arun.id)))
    val meds: List<Medication> = listOf("Metformin", "Lisinopril", "Atorvastatin", "Aspirin").map { med(it, raj.id, 8) } +
        med("Amlodipine", raj.id, 13) + med("Warfarin", raj.id, 21) + med("Metoprolol", raj.id, 21)

    fun plan(me: CircleMember, now: Instant, entries: List<DoseEntry> = emptyList(), tweak: (AppSettings) -> AppSettings = { it }, medsOverride: List<Medication>? = null) =
        ReminderPlanner.plan(PlannerInput(now, tweak(AppSettings(tier = tier)), me, listOf(priya, arun, maria), listOf(raj), medsOverride ?: meds, entries, tier))
}
private fun eightAm() = ldt(2026, 11, 21, 8)

private fun registerPlanner() {
    test("Aide gets reminders and nags for 8:00 but no missed-dose alerts") {
        val f = Family(PlanTier.PRO); val plan = f.plan(f.maria, at(CHI, 2026, 11, 21, 7)).filter { it.dueLocal == eightAm() }
        eq(1, plan.count { it.kind == AlertKind.REMINDER }, "reminder"); eq(6, plan.count { it.kind == AlertKind.NAG }, "nags"); eq(0, plan.count { it.kind == AlertKind.MISSED }, "no missed")
        val rem = plan.single { it.kind == AlertKind.REMINDER }
        eq("Time for Raj's 8:00 AM medicines", rem.title, "title"); isTrue("Metformin" in rem.body && "Aspirin" in rem.body, "body"); eq(at(CHI, 2026, 11, 21, 8), rem.trigger, "trigger")
    }
    test("Priya and Arun get the missed-dose chain at +30 and +45 minutes, with Arun's own clock") {
        val f = Family(PlanTier.PRO); val now = at(CHI, 2026, 11, 20, 20); val nine = ldt(2026, 11, 21, 21)
        val p = f.plan(f.priya, now).single { it.kind == AlertKind.MISSED && it.dueLocal == nine }
        val a = f.plan(f.arun, now).single { it.kind == AlertKind.MISSED && it.dueLocal == nine }
        eq(at(CHI, 2026, 11, 21, 21, 30), p.trigger, "Priya"); eq(at(CHI, 2026, 11, 21, 21, 45), a.trigger, "Arun")
        eq("Raj hasn't had the 9:00 PM medicines", p.title, "title"); isTrue("(7:00 PM your time)" in a.body, "Arun clock: ${a.body}"); isTrue("your time" !in p.body, "Priya same zone")
    }
    test("Free plan: only the first caregiver is alerted") {
        val f = Family(PlanTier.FREE); val now = at(CHI, 2026, 11, 20, 20)
        isTrue(f.plan(f.priya, now).any { it.kind == AlertKind.MISSED }, "Priya"); isTrue(f.plan(f.arun, now).none { it.kind == AlertKind.MISSED }, "Arun")
    }
    test("A dose logged anywhere removes its alerts everywhere") {
        val f = Family(PlanTier.PRO); val now = at(CHI, 2026, 11, 21, 7)
        val entries = f.meds.filter { it.timesOfDayMinutes[0] == 480 }.map {
            DoseEntry(recipientId = f.raj.id, medicationId = it.id, occurrenceKey = DoseKeys.make(it.id, eightAm()), dueLocal = eightAm(), status = DoseStatus.TAKEN)
        }
        for (who in listOf(f.maria, f.priya, f.arun)) eq(0, f.plan(who, now, entries).count { it.dueLocal == eightAm() }, "${who.name} has none")
        val left = f.plan(f.maria, now, entries.take(3)).filter { it.kind == AlertKind.REMINDER && it.dueLocal == eightAm() }
        eq(1, left.size, "remains"); eq(1, left[0].medicationIds.size, "only the pending medicine")
    }
    test("Nothing is scheduled in the past; ids are stable and unique") {
        val f = Family(PlanTier.PRO); val now = at(CHI, 2026, 11, 21, 8, 10)
        val one = f.plan(f.maria, now); val two = f.plan(f.maria, now)
        isTrue(one.all { it.trigger.isAfter(now) }, "future"); eq(one.map { it.id }, two.map { it.id }, "stable"); eq(one.size, one.map { it.id }.distinct().size, "unique")
        eq(4, one.count { it.kind == AlertKind.NAG && it.dueLocal == eightAm() }, "remaining nags")
    }
    test("Gentle style sends one follow-up; heads-up is optional") {
        val f = Family(PlanTier.PRO)
        val plan = f.plan(f.maria, at(CHI, 2026, 11, 21, 7), tweak = { it.copy(reminders = it.reminders.copy(style = ReminderStyle.GENTLE, headsUpMinutes = 10)) }).filter { it.dueLocal == eightAm() }
        eq(1, plan.count { it.kind == AlertKind.NAG }, "one follow-up"); eq(at(CHI, 2026, 11, 21, 7, 50), plan.single { it.kind == AlertKind.HEADS_UP }.trigger, "10 min early")
    }
    test("Self-managed person reads 'your medicines'") {
        val me = CircleMember.create("Raj", CircleRole.PATIENT, true, CHI); val r = raj()
        val plan = ReminderPlanner.plan(PlannerInput(at(CHI, 2026, 11, 21, 7), AppSettings(), me, listOf(me), listOf(r), listOf(med("Metformin", r.id, 8)), emptyList(), PlanTier.FREE))
        eq("Time for your 8:00 AM medicines", plan.first { it.kind == AlertKind.REMINDER }.title, "title")
    }
    test("Refill reminders are a Pro feature, fire at 9 AM, and skip the aide") {
        val pro = Family(PlanTier.PRO); val meds = pro.meds.mapIndexed { i, m -> if (i == 0) m.copy(unitsRemaining = 6.0) else m }
        val now = at(CHI, 2026, 11, 21, 7)
        val refill = pro.plan(pro.priya, now, medsOverride = meds).single { it.kind == AlertKind.REFILL }
        eq(at(CHI, 2026, 11, 21, 9), refill.trigger, "9 AM"); isTrue(refill.title.startsWith("Refill Metformin"), refill.title)
        isTrue(Family(PlanTier.FREE).plan(pro.priya, now, medsOverride = meds).none { it.kind == AlertKind.REFILL }, "not on Free")
        isTrue(pro.plan(pro.maria, now, medsOverride = meds).none { it.kind == AlertKind.REFILL }, "not for aide")
    }
    test("Alert diff cancels what is no longer planned") {
        val f = Family(PlanTier.PRO); val plan = f.plan(f.maria, at(CHI, 2026, 11, 21, 7)); val diff = AlertDiff.compute(listOf(plan[0].id, 123456789), plan)
        eq(listOf(123456789), diff.toCancel, "stale"); eq(plan.size, diff.toSchedule.size, "schedule all")
    }
    test("Supply maths") {
        val m = med("A", "r", 8, 20).copy(unitsRemaining = 28.0, unitsPerDose = 1.0)
        eq(14, SupplyCalculator.daysRemaining(m), "twice daily"); eq(7, SupplyCalculator.daysRemaining(m.copy(unitsPerDose = 2.0)), "two per dose")
        isTrue(SupplyCalculator.needsRefill(m.copy(unitsPerDose = 2.0)), "needs refill"); isTrue(SupplyCalculator.daysRemaining(m.copy(kind = ScheduleKind.AS_NEEDED)) == null, "prn")
    }
}

private fun registerHistory() {
    test("Adherence counts taken, late, skipped and missed, and ignores as-needed") {
        val r = raj(); val m = med("A", r.id, 8).copy(strength = "10 mg"); val maria = CircleMember.create("Maria", CircleRole.AIDE, false, CHI)
        fun e(day: Int, s: DoseStatus, h: Int, mi: Int, prn: Boolean = false) = DoseEntry(
            id = "e$day$prn", recipientId = r.id, medicationId = m.id, occurrenceKey = if (prn) "prn$day" else DoseKeys.make(m.id, ldt(2026, 11, day, 8)),
            dueLocal = ldt(2026, 11, day, 8), status = s, isAsNeeded = prn, loggedByMemberId = maria.id, loggedAt = at(CHI, 2026, 11, day, h, mi),
        )
        val entries = listOf(e(16, DoseStatus.TAKEN, 8, 5), e(17, DoseStatus.TAKEN, 10, 0), e(18, DoseStatus.SKIPPED, 8, 30), e(20, DoseStatus.TAKEN, 8, 0, true))
        val rows = HistoryBuilder.build(r, listOf(m), entries, listOf(maria), d(2026, 11, 16), d(2026, 11, 21), at(CHI, 2026, 11, 21, 12), ReminderSettings())
        val rep = AdherenceCalculator.compute(rows, d(2026, 11, 16), d(2026, 11, 21))
        eq(6, rep.scheduled, "scheduled"); eq(2, rep.taken, "taken"); eq(1, rep.takenLate, "late"); eq(1, rep.skipped, "skipped"); eq(3, rep.missed, "missed"); eq(33.3, rep.percent, "percent")
        isTrue(rows.any { it.outcome == HistoryOutcome.AS_NEEDED }, "prn listed"); eq("Maria", rows.first { it.outcome == HistoryOutcome.TAKEN }.loggedBy, "attribution")
    }
    test("Upcoming doses are not counted as missed; stopped medicines keep their past") {
        val r = raj(); val m = med("A", r.id, 8, 20).copy(isArchived = true, endDate = d(2026, 11, 20))
        val rows = HistoryBuilder.build(r, listOf(m), emptyList(), emptyList(), d(2026, 11, 19), d(2026, 11, 21), at(CHI, 2026, 11, 20, 12), ReminderSettings())
        eq(3, rows.size, "19th x2 + 20th 8:00"); isTrue(rows.all { it.outcome == HistoryOutcome.MISSED }, "all missed")
    }
    test("Entries from an edited schedule still appear") {
        val r = raj(); val m = med("A", r.id, 9)
        val e = DoseEntry(recipientId = r.id, medicationId = m.id, occurrenceKey = DoseKeys.make(m.id, ldt(2026, 11, 20, 8)), dueLocal = ldt(2026, 11, 20, 8), status = DoseStatus.TAKEN, loggedAt = at(CHI, 2026, 11, 20, 8))
        eq(2, HistoryBuilder.build(r, listOf(m), listOf(e), emptyList(), d(2026, 11, 20), d(2026, 11, 20), at(CHI, 2026, 11, 21, 12), ReminderSettings()).size, "orphan + missed 9:00")
    }
    test("CSV escapes quotes and commas and defuses formulas") {
        eq("plain", CsvExporter.cell("plain"), "plain"); eq("\"a,b\"", CsvExporter.cell("a,b"), "comma"); eq("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""), "quote")
        eq("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"), "formula")
        val csv = CsvExporter.build("Raj", listOf(HistoryRow(ldt(2026, 11, 21, 8), "m", "A, B", outcome = HistoryOutcome.TAKEN)))
        isTrue(csv.startsWith("Date,Due time,Person"), "header"); isTrue("2026-11-21,08:00,Raj,\"A, B\"" in csv, "row: $csv")
    }
    test("Doctor report lists active medicines with schedule and adherence") {
        val r = raj(); val a = med("Metformin", r.id, 8, 20).copy(strength = "500 mg", instructions = "With food"); val stopped = med("Old", r.id, 8).copy(isArchived = true, endDate = d(2026, 11, 1))
        val rows = HistoryBuilder.build(r, listOf(a, stopped), emptyList(), emptyList(), d(2026, 11, 20), d(2026, 11, 21), at(CHI, 2026, 11, 21, 23), ReminderSettings())
        val rep = DoctorReport.build(r, listOf(a, stopped), rows, d(2026, 11, 20), d(2026, 11, 21), false)
        eq(1, rep.medications.size, "active only"); eq("Every day at 8:00 AM, 8:00 PM", rep.medications[0].schedule, "schedule"); eq(4, rep.adherence.scheduled, "scheduled"); eq(4, rep.recentMissed.size, "missed")
    }
}

private fun registerPlans() {
    test("Free allows you plus one viewer; Pro opens the circle") {
        isTrue(Entitlements.canAddMember(PlanTier.FREE, 0, CircleRole.PATIENT).allowed, "owner"); isTrue(Entitlements.canAddMember(PlanTier.FREE, 1, CircleRole.VIEWER).allowed, "viewer")
        val co = Entitlements.canAddMember(PlanTier.FREE, 1, CircleRole.CAREGIVER); isTrue(!co.allowed && co.feature == Feature.CO_LOGGING, "co-logging gated")
        isTrue(!Entitlements.canAddMember(PlanTier.FREE, 2, CircleRole.VIEWER).allowed, "full"); isTrue(Entitlements.canAddMember(PlanTier.PRO, 5, CircleRole.AIDE).allowed, "pro")
        isTrue(!Entitlements.canAddRecipient(PlanTier.FREE, 1).allowed, "second person"); eq(1, Entitlements.clampChain(PlanTier.FREE, listOf("a", "b")).size, "chain")
    }
    test("Expired Pro behaves as Free") {
        val s = AppSettings(tier = PlanTier.PRO, proExpires = Instant.parse("2026-11-01T00:00:00Z"))
        eq(PlanTier.FREE, s.effectiveTier(Instant.parse("2026-11-21T00:00:00Z")), "expired"); eq(PlanTier.PRO, s.copy(proExpires = null).effectiveTier(Instant.now()), "no expiry")
    }
    test("Onboarding builds the right roles, alerts and defaults") {
        val today = d(2026, 11, 21)
        val self = OnboardingPlan.build(OnboardingInput(OnboardingRole.SELF, "Raj", firstMedication = med("Metformin", "", 8)), today)
        eq(CircleRole.PATIENT, self.me.role, "self role"); eq("Raj", self.recipient.name, "recipient is me")
        isTrue(self.me.receivesDoseReminders && !self.me.receivesMissedAlerts, "self reminders only"); eq(self.recipient.id, self.medication!!.recipientId, "linked")
        val fam = OnboardingPlan.build(OnboardingInput(OnboardingRole.FAMILY_CAREGIVER, "Priya", "Raj"), today)
        eq(CircleRole.PRIMARY_CAREGIVER, fam.me.role, "family"); isTrue(fam.me.receivesDoseReminders && fam.me.receivesMissedAlerts, "both")
        eq(listOf(fam.me.id), fam.recipient.escalation.chainMemberIds, "in chain"); isTrue(fam.medication == null, "no med yet")
        val aide = OnboardingPlan.build(OnboardingInput(OnboardingRole.AIDE, "Maria", "  "), today)
        eq("Loved one", aide.recipient.name, "fallback"); isTrue(aide.recipient.escalation.chainMemberIds.isEmpty(), "aide not in chain")
        isTrue(aide.settings.onboardingDone && aide.settings.thisDeviceMemberId == aide.me.id, "settings")
    }
}

fun main() {
    registerSchedule(); registerTime(); registerGuard(); registerService(); registerDay(); registerEscalation(); registerPlanner(); registerHistory(); registerPlans()
    var failed = 0
    for ((name, body) in tests) {
        try { body(); println("  PASS  $name") } catch (t: Throwable) { failed++; println("  FAIL  $name\n        ${t.message}") }
    }
    println("\n${tests.size - failed}/${tests.size} passed")
    if (failed > 0) exitProcess(1)
}
