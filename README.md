# DoseCircle

Family medication and caregiver organizer for Android. Native Kotlin, Jetpack Compose, Material 3.

**Idea in one line:** reminders that keep nudging until the dose is logged, a record of *who* gave each dose, and a guard that stops anyone giving the same dose twice.

## Plans

| | Free | Pro |
|---|---|---|
| Medicines | Unlimited | Unlimited |
| Reminders | Persistent nudges, snooze, heads-up | Same |
| History and CSV export | Full history | Same |
| People looked after | 1 | Up to 25 |
| Care circle | You + 1 person who can view | Up to 12; family and aides can log under their own names |
| Missed-dose alerts | 1 caregiver | Chain of up to 5, escalating |
| Refill tracking | – | Yes |
| Doctor report (PDF) | – | Yes |

All plan rules live in one place: `core/.../Models.kt` → `Entitlements`.

## Project layout

```
core/   Pure Kotlin (no Android). All rules + a dependency-free test suite.
          Models, ScheduleExpander, DoseGuard, DoseSlotBuilder, EscalationPlanner,
          ReminderPlanner, HistoryBuilder/Adherence/CSV/DoctorReport, DoseService, Entitlements
app/    Android app
  data/        Room database + DataStore implementation
  reminders/   AlarmManager scheduling, notifications, receivers (alarm, actions, boot)
  billing/     Google Play Billing
  export/      CSV + PDF sharing
  ui/          Compose screens: onboarding, today, medicines, circle, history, settings, paywall
```

How reminders work: `ReminderPlanner` (pure function) decides which alarms should exist on *this* phone for the next 72 hours.
`ReminderScheduler` turns that plan into exact alarms and rebuilds it whenever data, settings, the plan or permissions change,
and after reboot. When an alarm fires, the receiver re-checks that the dose is still unlogged before showing the notification.

## Build

Requirements: Android Studio (current stable), JDK 17.

```bash
# first time only, if you want the wrapper in the repo
gradle wrapper --gradle-version 8.13

./gradlew :core:check        # runs the core test suite (47 tests)
./gradlew :app:assembleDebug
```

Versions are pinned in `gradle/libs.versions.toml`. Bump them to current stable if you like; nothing here depends on a specific version beyond
AGP needing a Gradle that supports it.

## Before you ship (Play Console)

1. Create the subscription **`dosecircle_pro`** with base plans **`monthly`** (P1M) and **`annual`** (P1Y), and a 7-day free-trial offer on `annual`.
   Suggested prices: $4.99 / month, $39.99 / year. The paywall reads prices from Play, so nothing is hard-coded.
2. Add a privacy policy URL. Data safety form: no data collected or shared (everything stays on the device).
3. Declare the exact-alarm permission use (`SCHEDULE_EXACT_ALARM`) as medication reminders. Complete the health-app declarations.
4. Add server-side purchase verification before relying on Pro for anything sensitive. The app currently trusts Play's on-device result and
   caches Pro for 7 days between successful checks.
5. Test reminders on real devices from several makers (Samsung, Xiaomi, OnePlus, Pixel) with battery saver on. Aggressive vendors can still delay alarms;
   the Settings screen shows live status and has a test-reminder button.

## Known limits of v1

- **Single-phone circle.** Several people can be in a circle and log doses under their own names on one shared phone (for example a home aide
  and family using the same phone). Separate phones for each person need cloud sync and push, which is not built. `Feature.CLOUD_SYNC` is reserved for it.
  The planner is already written so each phone computes only its own member's alerts, so sync only has to replicate the dose log.
- English only. Strings are in code; move them to `strings.xml` before localizing.
- No instrumented or screenshot tests yet.

## What has been verified

- `core`: compiled and **47/47 tests passing** (Kotlin 2.1.20, JDK 21). Covers DST gaps/overlaps, weekday maths, locale independence,
  double-dose guard, escalation timing and quiet hours across time zones, plan limits, adherence, CSV safety, onboarding.
- `reminders/` and `export/` were type-checked against the real Android framework API (android.jar, API 34).
- View models, data layer and container were type-checked against core + the Android framework with stubs for Room/lifecycle/billing.
- **Not compiled or run:** Compose UI, Room code generation, Play Billing, Gradle configuration, and nothing has been run on a device or emulator.
  Expect a few small fixes on first build.
