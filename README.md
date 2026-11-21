# PulseTime ⌚〰️

**Silent haptic time-awareness & interval pacing for Wear OS.**
PulseTime gives you an ambient *sense of time passing* through distinct wrist vibrations, so you know what quarter of the hour it is — or how far into a focus block or meeting you are — without looking at a screen.

## Features

| | |
|---|---|
| **Intervals** | 15 / 30 / 45 / 60 min quick picks + custom 5–120 min |
| **Modes** | *All-Day Anchor* (hourly, on the hour) · *Deep Focus* (every 30 min from start) · *Meeting Pacer* (every 15 min, micro-taps) |
| **Alignment** | Clock-aligned (`:00 :15 :30 :45`) or relative (counted from start) |
| **Feel the Time** | `:00` / interval complete → 1 firm 300 ms pulse · `:30` → 2 crisp 100 ms pulses · `:15`/`:45` → gentle 60-80-60 double-tap · other marks → soft tick |
| **Intensity** | Gentle / Medium / Strong with on-wrist preview and a "Test Feel" sequence |
| **Quiet Hours** | e.g. 22:30 – 07:00; schedule keeps running, watch is never woken during the window |
| **Auto-pause off-wrist** | Uses `TYPE_LOW_LATENCY_OFFBODY_DETECT` when present, otherwise falls back to always-on |
| **Snooze 1 h** | One tap on the home screen |
| **Tile** | "Next in 14m" live countdown, interval, Start/Pause |
| **Complication** | `SHORT_TEXT` countdown and `RANGED_VALUE` progress arc |

## Battery-first architecture

- **One alarm, nothing else.** `AlarmManager.setExactAndAllowWhileIdle()` wakes a `BroadcastReceiver` that vibrates, arms the next alarm and exits. No foreground service, no polling loops — the process can be killed between pulses.
- **Quiet Hours & snooze are skipped at scheduling time**, so there are zero silent wake-ups overnight.
- **Tile countdown is a ProtoLayout dynamic expression** and the **complication uses `TimeDifferenceComplicationText`** — both tick down on the system's clock without waking the app. Tile freshness is `0` (push-only).
- **Off-body sensor is sampled once per pulse** (register → first event → unregister, 600 ms timeout).
- **Pure black OLED theme** and ScalingLazyColumn layouts for round and square screens.
- **Resilient:** re-arms on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, app update and exact-alarm permission changes. Clock-aligned slots are computed in local wall-clock time, so DST changes don't shift them.

## Project structure

```
app/src/main/java/com/pulsetime/
├── PulseController.kt            # All actions: persist → re-arm alarm → refresh tile/complication
├── data/
│   ├── PulseSettings.kt          # Model, presets, enums
│   └── PulseRepository.kt        # Jetpack DataStore (Preferences)
├── haptics/HapticEngine.kt       # VibrationEffect waveforms + intensity scaling
├── schedule/
│   ├── PulseMath.kt              # Pure, unit-tested time math (alignment, quiet hours, pattern choice)
│   ├── PulseScheduler.kt         # AlarmManager wrapper (single exact alarm)
│   ├── PulseAlarmReceiver.kt     # Fires a pulse, schedules the next
│   ├── RescheduleReceiver.kt     # Boot / time / timezone resilience
│   └── OffBodyDetector.kt        # One-shot off-wrist check
├── tile/PulseTileService.kt
├── complication/PulseComplicationService.kt
└── presentation/
    ├── MainActivity.kt           # SwipeDismissableNavHost (max 2 levels below Home)
    ├── theme/Theme.kt
    └── screens/ Home · Settings · QuietHours · TimePicker
```

## Build & run

1. Open the folder in **Android Studio** (Ladybug or newer). It will download Gradle 8.9 from the wrapper properties and sync.
   - To use the command line, generate the wrapper once: `gradle wrapper --gradle-version 8.9`, then `./gradlew assembleDebug`.
2. Start a **Wear OS emulator** (API 30+) or connect a watch with ADB debugging.
3. Run the `app` configuration.
4. Add the tile from the tile carousel and the complication from your watch face editor.

Unit tests for the scheduling logic: `./gradlew test`.

## Permissions

| Permission | Why |
|---|---|
| `VIBRATE` | Haptic pulses |
| `USE_EXACT_ALARM` (API 33+) / `SCHEDULE_EXACT_ALARM` (API 31–32) | Pulses must land on exact clock boundaries |
| `RECEIVE_BOOT_COMPLETED` | Re-arm after restart |
| `WAKE_LOCK` | Keep CPU awake for the < 1 s receiver work |

The off-body sensor needs no runtime permission.

> **Google Play note:** `USE_EXACT_ALARM` is restricted to apps whose core function is alarms/timers. If Play review objects, remove it and keep only `SCHEDULE_EXACT_ALARM` (without `maxSdkVersion`); the app already falls back to inexact alarms and shows an "Allow exact alarms" chip when needed.

## Known limits

- In deep Doze, Android throttles `setExactAndAllowWhileIdle` to roughly one alarm per ~9 minutes per app, so 5-minute custom intervals may arrive late while the watch is idle. Pulses more than 5 minutes late are dropped rather than played, so a pulse never lies about the time.
- Dependency versions are pinned to known-good releases; Android Studio's upgrade assistant can bump them.
