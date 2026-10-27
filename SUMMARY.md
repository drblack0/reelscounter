# Reels Counter: app summary

An Android app that counts the Instagram Reels you watch and how long you spend watching them. It runs
entirely on the phone. This document describes what is built, what it can access, what it stores and how.
Facts here were checked against the code at commit time; things that are untested are marked as such.

- Package: `com.mindware.reels`, version 0.1.0, sideload/personal build (not aimed at the Play Store).
- Android: minSdk 26, targetSdk/compileSdk 37.
- Stack: Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00, Material 3), Room 2.8.5, coroutines 1.11.0,
  Navigation Compose, AGP 9.4.1, JDK 21. Manual dependency injection (no Hilt).
- Status: milestones M0 to M6 in `PLAN.md` are done. 50 unit tests pass, 0 fail. Detection was verified by
  hand on one phone (screen 1264x2780) only. A release (minified) build has not been built or tested.

## What the user sees

| Screen | Contents |
|---|---|
| Onboarding (first launch) | Explains the app, status of the accessibility service (re-checked on return), a hint for Android 13+ "restricted settings", and battery guidance for the phone's maker |
| Today | Reels, time, sessions, longest session for the local day; live "watching now" card; reels-by-hour chart; warning banner if the service is off |
| History | Week or Month bar chart of reels per local day; tap a day to list its sessions (start time, reels, duration) |
| Settings | Service status, overlay on/off, reel threshold slider (1 to 5 s), links to Keep it running, Privacy and Debug tools, "Delete all data" |
| Overlay pill | `12 reels · 03:41`, shown just below the camera cutout while a Reels session runs; not touchable |
| Privacy | Plain-language description of what is read and stored |
| Debug tools | Structure-only logging and tree dump for diagnosing Instagram layout changes; a banner shows on every screen while on |

## Permissions

**Requested in the manifest (`uses-permission`): none.** In particular there is no `INTERNET`, no storage,
location, contacts, camera, microphone, notification, foreground-service or usage-stats permission.
A build step fails the build if `INTERNET` ever appears in the merged manifest.

| Item | Kind | Why |
|---|---|---|
| Accessibility service (user enables it in Android Settings → Accessibility) | Special access granted by the user, not a runtime permission | Lets the app receive window events and read the screen's node tree. This is the only sensitive capability. It is declared with `canRetrieveWindowContent`, `flagReportViewIds` and `flagRetrieveInteractiveWindows` |
| `BIND_ACCESSIBILITY_SERVICE` on the service | Protection on the service, not requested by us | Only the Android system may bind to the service |
| Overlay window type `TYPE_ACCESSIBILITY_OVERLAY` | Comes with the accessibility service | Draws the pill without `SYSTEM_ALERT_WINDOW` ("draw over other apps") |
| `com.mindware.reels.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added automatically by AndroidX; private to this app | Protects internal receivers; not a user-facing permission |
| Android 13+ "Allow restricted settings" | One-time user step for sideloaded apps | Android blocks accessibility services from sideloaded apps until allowed in the app's info page |
| Battery optimisation | Not a permission | The app only opens Android's battery settings list. It does not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

Backups: `allowBackup="false"`, so neither the database nor the settings are copied to Google backup or
device transfer.

## What the app reads from the screen

Only while the accessibility service is on.

- **Instagram windows** (`com.instagram.android`): at most about every 120 ms the detector walks the node tree
  (up to 600 nodes, depth 30) and reads, per node: view ID, class name, on-screen bounds, selected/scrollable/visible
  flags, and the content description.
- **Content descriptions** are used only to recognise a reel and tell it apart from the next. The text is matched
  against a pattern, reduced to a 32-bit fingerprint (Java `hashCode`, not a cryptographic hash) and discarded.
  The fingerprint is the "reel ID". It lives in memory and in debug-log lines, and is **never written to the database**.
- **Other apps:** only the package name of a window-change event, used to end a session when you leave Instagram.
  Ignored: system UI, this app's own overlay, and installed keyboards. No node tree is read from other apps.
- **Never read or stored:** usernames, captions, comments, messages, video or images, text typed by the user.

Side effect to know about: the service no longer filters by package in its XML config, so Android delivers
window events from every app to it. It returns immediately for non-Instagram events, but this wakes it more often
than an Instagram-only service would.

## What is stored, and where

All data is in the app's private storage (not accessible to other apps without root) and is removed on uninstall
or "Clear storage".

**Room database `reels.db` (schema version 1, exported to `app/schemas/`)**

| Table | Columns | Meaning |
|---|---|---|
| `reel_session` | `id`, `start_ts`, `end_ts` (null while open), `reel_count` | One continuous Reels viewing session |
| `reel_view` | `id`, `session_id` (cascade delete), `ts`, `duration_ms` | One counted reel: when it appeared and how long it stayed |

All timestamps are UTC milliseconds. There is one `reel_view` row per counted reel and nothing else about it.

**SharedPreferences `heartbeat`:** `last_activity_ts`, written at most every 5 seconds during a session. Used only
to close a session left open after a crash or kill.

**SharedPreferences `settings`:** `overlay` (boolean), `min_reel_ms` (1000 to 5000), `onboarded` (boolean).

**In memory only (lost when the process ends):** the live session state for the overlay, and the last 200 debug
log lines. While debug logging is on, the same lines also go to Android's system log under tag `ReelsDebug`, where
they stay until the system rotates them. They contain structure (IDs, classes, bounds, flags) and reel fingerprints,
never on-screen text.

**Deleting:** Settings → Delete all data removes all rows from both tables (it does not reset the two preference
files). Uninstalling removes everything.

## How counting works

```
Instagram window events ──▶ detector (node tree → Signals) ──▶ single consumer loop
                                                                  │
          overlay pill ◀── live state ◀── SessionTracker (pure rules) ──▶ ReelsRepository ──▶ Room
```

1. **Detect.** The service throttles events (about 120 ms, plus one trailing run after a burst so the settled state
   is seen). The detector says "Reels is visible" only when the Reels pager `clips_viewer_view_pager` is on screen
   with non-empty bounds. Instagram keeps that pager in the tree, hidden and zero-width, on other tabs, so a hidden
   one does not count. A structural fallback (tall scrollable pager plus "Reel" description) exists but is switched
   off, because it matched reel posts on the Home feed.
2. **Identity.** The reel ID is the fingerprint of the content description of the on-screen reel node. Consecutive
   equal IDs are one reel. Scrolling back to an earlier reel counts again, as a new view.
3. **Count.** A reel counts after it has stayed on screen for the threshold (default 1 s, 1 to 5 s in Settings).
   Its stored duration is updated when it changes or the session ends.
4. **Sessions.** A session starts lazily at the first counted reel. It ends when: the Reels viewer is gone and a
   bottom-bar tab is visible (Home, Search, Profile…); a different app comes to the front; or 30 s pass with no
   detection (end time is the last activity).
5. **Sheets.** If neither the viewer nor the bottom bar is visible (for example the comments sheet), the state is
   "unknown" and a running session is kept alive for up to 2 minutes.
6. **Persist.** One consumer coroutine feeds the pure `SessionTracker` and writes its actions to Room. It wakes
   once a second only while a session or pending reel exists.
7. **Recover.** On service start, any session with no end time is closed at the last heartbeat, but no later than
   30 s after its last recorded reel and never in the future.
8. **Days.** "Today" and per-day charts use local-time days computed in Kotlin at query time (so 23/25-hour daylight
   saving days work). A session belongs to the day it started in. Hourly buckets use the local hour.

## Code map (`app/src/main/java/com/mindware/reels/`)

```
detection/  InstagramConfig (all Instagram-specific strings, the one file to patch), InstagramReelsDetector,
            ReelsAccessibilityService, NodeDump, DebugLog, ServiceStatus, NodeCompat (node recycling)
session/    SessionTracker + SessionConfig, Signals, SessionAction, LiveState (no Android imports)
data/       ReelsRepository, SettingsStore, HeartbeatStore, AppContainer, db/ (AppDatabase, entities, ReelDao)
overlay/    OverlayController, formatPill      stats/  local-day and hourly helpers
ui/         nav, today, history, settings, onboarding (incl. battery guidance), privacy, debug, components, theme
```

## Known limits and risks

- **Instagram can change its view IDs at any time.** Counting would then stop or misfire. The fix is in
  `InstagramConfig`; the debug tools show what the new layout looks like. The current IDs were verified only on one
  phone and one app version.
- **Same-author reels may merge.** The ID comes from a description that names the author. Two consecutive reels by
  the same account might count as one. This was never observed either way.
- **Phone makers kill background services** (Xiaomi, Oppo, Vivo, Samsung and others), and some turn the service off
  after an app update or force-stop. Recovery protects the data; the onboarding guidance and the Today warning banner
  are the mitigation.
- **A very long reel with no screen events for 30 s** would end the session. Not observed.
- **Instagram's terms and Play Store policy:** accessibility services that read other apps face strict review.
  This is built as a personal, sideloaded app.
- **Not covered by tests:** the detector against a live Instagram tree, the overlay, and the Compose screens.
  Their behaviour was checked by hand on one device.
