# Plan: Reels Counter (working name: mindware)

App ID: `com.mindware.reels`. Sideload/personal build, not targeting the Play Store.

## Toolchain
- JDK 21 and adb installed. Android SDK and Gradle wrapper set up in M0.
- A milestone is only "done" if the build and tests actually ran.
- Library versions and targetSdk are verified online at the time they are added.

## Architecture

```
AccessibilityService ──emits──▶ DetectionEvent ──▶ SessionTracker (pure Kotlin) ──▶ Repository ──▶ Room
   (signals only)               (SharedFlow)        (rules, clock-injected)            │
        │                                                                              ▼
        └──────────▶ OverlayController (XML TextView) ◀── live state (StateFlow) ── Compose UI
```

### Detection (`detection/`)
- `ReelsAccessibilityService` receives events and hands the root node to `InstagramReelsDetector`.
- The detector returns a small `Signals` value: `instagramForeground`, `reelsViewerVisible`, `reelId` (opaque string).
- The service emits `DetectionEvent`s onto a `SharedFlow`.
- Events are throttled/debounced (~100-150 ms) on the event thread. Heavier work goes to a coroutine on a single-thread dispatcher.
- `InstagramConfig` is a data class holding every Instagram-specific string. It is the only place to patch.

### Session logic (`session/`, no Android imports)
- `SessionTracker(config, clock)` takes `DetectionEvent`s plus `tick(now)` and returns `SessionAction`s (`OpenSession`, `RecordReel`, `CloseSession`).
- Rules in `SessionConfig`: `MIN_REEL_MS = 1000`, `IDLE_TIMEOUT_MS = 30_000`.
- The tracker stays pure. The service owns a coroutine that calls `tick()`; tests drive a fake clock.

### UI (`ui/`)
- Compose screens read Flows from `ReelsRepository`. The overlay uses a plain XML layout.

## Key design decisions
- **Reel identity.** The reel is whatever the detector says. First try the content description of the on-screen reel node; fall back to the pager's selected child identity (bounds/index). The tracker dedupes consecutive identical IDs. A same ID reappearing after a different one counts again (user scrolled back).
- **Counting.** A reel counts after 1 s on screen. The tracker holds a `pendingReel` and commits it once `now - firstSeen >= 1000` on a tick or next event. Duration is time until the reel changes or the session ends.
- **Dangling session recovery.** A throttled heartbeat (~5 s) persists `lastEventTs`. On service start, any session with `end_ts == NULL` is closed at the last heartbeat, capped by the idle timeout.
- **Time zones.** Timestamps are UTC millis. Day queries use `[startOfDay, endOfDay)` computed in Kotlin with `ZoneId.systemDefault()` at query time (handles 23/25 h DST days). Hourly, weekly and monthly buckets use local day boundaries. A session belongs to the day it started. No SQLite `strftime('localtime')`.
- **DI.** Manual DI via a single `AppContainer` in `Application` (database, repository, settings, detector, overlay). No Hilt.
- **Settings.** Preferences DataStore. No extra permission, no network.
- **Overlay.** `TextView` pill added through the service's `WindowManager` as `TYPE_ACCESSIBILITY_OVERLAY`, with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`. Top inset from the status bar height resource. Updates on repository state, once per second for the timer.
- **Node recycling.** `AccessibilityNodeInfo.recycle()` is required below API 33 and a no-op from 33. A small helper calls it in try/finally on every level, with the deprecation suppressed.
- **Privacy.** The detector reads only `viewIdResourceName`, `className`, `contentDescription` and selected state. Content descriptions are matched against patterns, then hashed or discarded. Raw text is never stored or logged. The debug node dump logs only view IDs, class names, bounds and flags; an opt-in toggle can add content-desc. Debug logs stay in logcat and a local file. A persistent banner shows while debug mode is on.
- **Manifest.** No `INTERNET` permission, with a build check that fails if it appears. `allowBackup=false` and the database is excluded from backup.

## Package layout (`com.mindware.reels`)
```
data/        db/ (AppDatabase, entities, ReelDao, schemas/), ReelsRepository, SettingsStore
session/     SessionConfig, SessionTracker, DetectionEvent, SessionAction
detection/   InstagramConfig, InstagramReelsDetector, ReelsAccessibilityService, NodeDump
overlay/     OverlayController, res/layout/overlay_pill.xml
ui/          nav/, today/, history/, onboarding/, settings/, debug/, privacy/, theme/
```

## Data model
- `reel_session(id PK, start_ts, end_ts NULL, reel_count)`
- `reel_view(id PK, session_id FK -> cascade, ts, duration_ms)`, index on `session_id`
- Room exports schemas; a migration test scaffold is included.
- Aggregates come from DAO queries (totals by range, hourly buckets, per-day grouping). No summary table.

## Milestones
| M  | Scope | Done when |
|----|-------|-----------|
| M0 | SDK + wrapper setup | `./gradlew assembleDebug` works on an empty app |
| M1 | Skeleton, Room + DAO tests (Robolectric), SessionTracker + tests | Unit tests pass |
| M2 | Service, config-driven detector, debug mode and log screen | Builds. Stop here with device test instructions |
| M3 | Wire detection -> tracker -> Room, heartbeat recovery | Only after detection is confirmed working on device |
| M4 | Overlay | |
| M5 | Today and History screens | |
| M6 | Onboarding, OEM battery guidance, Settings, privacy screen, polish | |

Each milestone ends in a commit with the Co-Authored-By trailer.

## Risks
- **Instagram view IDs are obfuscated and change.** Config holds best-guess candidates, all marked UNVERIFIED. Reels often use a pager whose children lack stable IDs, so a structural fallback is also supported (full-screen vertical scrollable container plus a content-description pattern). Unverifiable without a device.
- **Reel-change detection may be unreliable.** Content-description changes may fire sparsely. `TYPE_VIEW_SCROLLED` may be needed; add it only if M2 testing shows it is, and ask first.
- **OEM background kills** (Xiaomi, Oppo, Vivo, Samsung) can stop the service mid-session. Recovery protects data; M6 guidance covers the rest.
- **Service disabled after app update or force-stop** on some OEMs. Onboarding status re-checks on resume.
- **Play Store policy** is strict for Accessibility apps; not targeted.
- **Live Instagram behavior cannot be tested from the dev machine.** Builds and unit tests are verifiable; detection is not.

## M2 handoff (device steps)
Collect:
- `adb shell uiautomator dump /sdcard/reel.xml && adb pull /sdcard/reel.xml` with a Reel on screen, again after scrolling to the next reel, and once on the Home feed (negative control).
- Debug log lines showing matched signals.

Redact usernames and captions before sharing anything.
