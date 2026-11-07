# Todoist for Mudita Kompakt

A deliberately tiny Todoist client for the Mudita Kompakt, built with
[Mudita Mindful Design (MMD)](https://github.com/mudita/MMD) (`com.mudita:MMD`),
Mudita's E Ink-optimised Jetpack Compose component library.

## What it does

- Shows your open tasks that are **overdue or due today** (Todoist filter `today | overdue`),
  sorted by due date and time with the oldest first. On each day, timed tasks come before all-day ones.
- Tap a task, or its checkbox, to complete it. The completion is synced to Todoist immediately.
- A collapsible **Completed today** section at the bottom lists completed tasks due today.
  Tap one to mark it incomplete again (reopen).
- Add a task by name. It is created with the due date "today", so it shows up in the list.
- Re-syncs every time the app comes to the foreground, and has a refresh icon (top right)

It has no reminders, notifications, background sync, due date or priority editing, or projects.

## Auth

The app uses your Todoist **personal API token**:
Todoist → Settings → Integrations → Developer → API token.
Paste it on first launch. The app checks it against the API, then stores it in the app's
private SharedPreferences (backup is disabled). The log-out icon (top right, with a confirmation) clears it.

## Build and run in the emulator

Requirements: JDK 17 and the Android SDK at `~/android-sdk` with `platforms;android-35`.
MMD pulls in `activity-compose 1.10`, which needs compileSdk 35. The app still targets SDK 31,
which is MuditaOS K / Android 12.

```bash
./gradlew assembleDebug

# Kompakt-shaped emulator: 480x800 @ 240dpi, API 31
emulator -avd mudita_kompakt_api31 &
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.alexj.todoistkompakt/.MainActivity
```

The `mudita_kompakt_api31` AVD was created with:

```bash
avdmanager create avd -n mudita_kompakt_api31 -k "system-images;android-31;google_apis;x86_64"
# then in ~/.android/avd/mudita_kompakt_api31.avd/config.ini:
#   hw.lcd.width=480  hw.lcd.height=800  hw.lcd.density=240  hw.keyboard=yes
```

Tip: to avoid typing the token into the emulator by hand, tap the token field, then run
`adb shell input text "<your token>"`.

## Install on the Kompakt

Sideload `app-debug.apk`, or a signed release build, through Mudita Center:
Manage Files → App Installers.
