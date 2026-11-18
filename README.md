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
| **Theming** | Dark, light, follow-system, or **light by day and dark at night** (worked out from sunrise and sunset where the car is), and six accent colours. Full screen is on by default: the status bar, navigation bar and taskbar are hidden, and you swipe from an edge to show them. |
| **Phone module** | Favourite and frequent contacts as big buttons that call with one tap, then everyone else, plus a keypad button that opens the dialer or the head unit's Bluetooth phone app. |
| **Car data module** | Rpm, coolant temperature, fuel level and battery voltage from a Bluetooth ELM327 OBD-II adapter. Pick the paired adapter in **Settings → Car data**; it reconnects by itself when the ignition comes on. |
| **Car start** | When the car starts (a reboot, or a head unit waking from sleep after two minutes or more), the launcher comes to the front and can open the map and resume the music. |
| **Wide screen layout** | "Wide screen (Atoto S8)": clock, speed and car data on the left, the map in the middle, media and phone on the right, with tighter spacing and a shorter dock on wide, short screens. |

Everything is sized for driving: touch targets are at least 64dp and the text is large. **Bigger buttons**
(Settings → Driving) makes the dock, media controls, app list and settings bigger, either always or only
while the car is moving (on above about 12 km/h, off after a minute stopped).

## Install

1. Download the APK from the [latest debug build](../../releases/tag/latest-debug), or from the
   **Build** workflow's artifacts on the Actions tab.
2. On the tablet or head unit, allow "install unknown apps" for your browser or file manager, then open the APK.
3. Press Home and choose **Car Launcher** as the default home app. You can also do this from
   **Settings → Launcher → Choose default home app** inside the launcher.
4. Tap **Allow** on the speed card for location access.
5. Optional: **Settings → Media → Let the media card follow other apps** grants notification access, so
   the card can follow apps other than Wavelength.
6. Optional: if your head unit sleeps rather than switching off, turn on **Settings → Driving → Display over
   other apps**, so Android 10+ lets the launcher come back to the front by itself when the car starts.
7. Optional, Android 7 to 11 only (many head units): turn on **Car Launcher split-screen helper** in
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

## Config server (layouts and themes)

The [`server/`](server) folder is a small Docker app with a web editor for making your own dashboard layouts
and colour themes. Run it on a home server, then in the launcher go to **Settings → Config server**, enter its
address, and tap **Save and sync**. There is a template for Unraid too. See [server/README.md](server/README.md).

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

- Speed from the OBD-II adapter when there's no GPS fix
- Profile sync and backup
