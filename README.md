# Car Launcher

A home-screen launcher for landscape Android tablets and head units (built with an Atoto S8 in mind).
Waze runs in split-screen beside the dashboard, so you keep its police and hazard reports, while the
launcher shows the time, your speed, what's playing in [Wavelength](https://github.com/Spacegoose02/wavelength),
and a dock of your car apps.

## What's in v0.1

| | |
|---|---|
| **Waze in the map box** | The map pane opens Waze (or Google Maps) as a floating window sized to the box. Where floating windows aren't available, it uses split-screen, and otherwise full screen. The launcher checks what actually happened and stops trying the box if the device ignored it. You can force a mode in settings. |
| **Wavelength media card** | Talks to Wavelength directly through its media browser service, so it needs no extra permission. It shows the track, artist and cover with large previous, play/pause and next buttons. It can also host Wavelength's own "Now playing" home-screen widget. |
| **Other music apps** | With notification access granted, the card also follows Spotify, YouTube Music, podcasts and so on. Choose "Wavelength first", "Wavelength only" or "Whatever is playing". |
| **GPS speedometer** | Big digits in mph or km/h. It works out speed from the last two fixes when the GPS module doesn't report it. |
| **Dock and app drawer** | Long-press an app in the drawer to add it to the dock, and long-press a dock icon to remove it. The drawer has search. |
| **Theming** | Dark, light or follow-system, and six accent colours. Full screen is on by default: the status bar, navigation bar and taskbar are hidden, and you swipe from an edge to show them. |

Everything is sized for driving: touch targets are at least 64dp and the text is large.

## Install

1. Download the APK from the [latest debug build](../../releases/tag/latest-debug), or from the
   **Build** workflow's artifacts on the Actions tab.
2. On the tablet or head unit, allow "install unknown apps" for your browser or file manager, then open the APK.
3. Press Home and choose **Car Launcher** as the default home app. You can also do this from
   **Settings → Launcher → Choose default home app** inside the launcher.
4. Tap **Allow** on the speed card for location access.
5. Optional: **Settings → Media → Let the media card follow other apps** grants notification access, so
   the card can follow apps other than Wavelength.
6. Optional, Android 7 to 11 only (many head units): turn on **Car Launcher split-screen helper** in
   Accessibility. Without it, those Android versions can't start split-screen by themselves (see below).

## How the Waze pane works

Third-party apps can't embed Waze or read its alerts, so the launcher shows Waze's real window next to its own.

| Device | What happens with "Automatic" |
|---|---|
| Floating windows available (Samsung pop-up view, freeform head units, Android desktop windowing) | Waze opens as a floating window over the map box. If Android opens it full screen instead, Automatic switches to split-screen from then on. |
| Android 12L or newer (most current tablets) | Waze opens with `FLAG_ACTIVITY_LAUNCH_ADJACENT` and the system splits the screen. |
| Already in split-screen | Waze opens in the other half. |
| Android 7 to 11 with the split-screen helper on | The helper enters split-screen, then Waze opens in the other half. |
| None of the above | Waze opens full screen. Press Home to come back. |

When the launcher is half of a split screen, it switches to a compact layout with the widgets only.

## Build it yourself

You need JDK 17 and the Android SDK (Android Studio installs both).

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the folder in Android Studio and press Run.

Every push to `main` builds the APK on GitHub Actions (`.github/workflows/build.yml`) and replaces the
`latest-debug` pre-release with the new build.

## Roadmap

Planned, and the code is laid out for them (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):

- OBD2 data (rpm, coolant, fuel) through a Bluetooth ELM327 adapter
- A layout editor for choosing and arranging the widgets
- Automatic day and night themes
- Auto-actions when the car starts or Bluetooth connects (open Waze, resume Wavelength, ...)
- Profile sync and backup
