# Architecture

Kotlin, Jetpack Compose, a single `app` module. Package `com.spacegoose.carlauncher`.

```
HomeActivity            HOME intent. Forwards to DashboardActivity and finishes.
DashboardActivity       The real launcher screen (normal activity, own task, resizeable).
DashboardViewModel      UI state + actions; reads everything from AppGraph.
CarLauncherApp/AppGraph Hand-wired singletons.

settings/   LauncherSettings (one data class) + SettingsRepository (DataStore)
layout/     DashboardLayout: which widgets go in which row
maps/       MapLaunchPlanner (pure: which strategy), MapLauncher (does it), SplitScreenAccessibilityService
media/      MediaRepository (Wavelength via MediaBrowserCompat + other apps via MediaSessionManager),
            chooseMediaSource (pure), WavelengthWidgetHost (AppWidgetHost), MediaNotificationListener
vehicle/    VehicleDataSource interface, GpsSpeedSource
apps/       AppRepository: installed apps, icons, launching
ui/         Compose screens and cards
```

## Why two activities

Android never puts an activity of the HOME type into split-screen, so a launcher that is itself the
home activity can't sit next to Waze. `HomeActivity` handles the Home button and immediately brings up
`DashboardActivity`, which has its own `taskAffinity` and is an ordinary, resizeable activity, so the
system can split it with Waze. While the accessibility helper is entering split-screen on Android 7 to
11, `HomeActivity` briefly stops forwarding, so it doesn't cover the second pane.

## Wavelength

Wavelength (`com.tonearm.app`) exports `MediaPlaybackService`, a `MediaBrowserServiceCompat`. Any app can
connect to it and get its session token. Untrusted callers get an empty browse root, which is enough to
read metadata and send play, pause and skip. So the media card follows Wavelength with no permissions.
Browsing Wavelength's library from the launcher would need `com.spacegoose.carlauncher` added to
`CarAccess.trusted` in the Wavelength repo.

Wavelength also has a `NowPlayingWidget` app widget. `WavelengthWidgetHost` hosts it with
`AppWidgetHost`. The system asks the user once to allow the binding.

## Where planned features plug in

- **OBD2**: implement `VehicleDataSource` (for example `Elm327Source`) and add fields such as rpm
  and coolant to the reading. Swap or combine it in `AppGraph`. Add `WidgetType` entries for new gauges.
- **Layout editor**: persist a `DashboardLayout` (rows of `WidgetType` with weights) in the settings store
  instead of `DashboardLayout.Default`. `DashboardScreen` already renders whatever layout it is given.
- **Day/night themes**: add `ThemeMode.AUTO_SUN` and compute it from location and time in `CarLauncherTheme`.
- **Auto-actions**: an `automation/` package with a foreground service or receivers for Bluetooth
  `ACTION_ACL_CONNECTED` and power connected. Its actions call `MapLauncher` and `MediaRepository`.
- **Profile sync**: everything user-facing is in the one `launcher_settings` DataStore, so export and
  import cover the whole profile.
