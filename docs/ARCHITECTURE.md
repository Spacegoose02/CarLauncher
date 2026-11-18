# Architecture

Kotlin, Jetpack Compose, a single `app` module. Package `com.spacegoose.carlauncher`.

```
HomeActivity            HOME intent. Forwards to DashboardActivity and finishes.
DashboardActivity       The real launcher screen (normal activity, own task, resizeable).
DashboardViewModel      UI state + actions; reads everything from AppGraph.
CarLauncherApp/AppGraph Hand-wired singletons.

settings/   LauncherSettings (one data class) + SettingsRepository (DataStore)
layout/     DashboardLayout: which widgets go in which row
config/     ServerConfig (pure: parse the config server's JSON), ConfigRepository (fetch + cache)
maps/       MapLaunchPlanner (pure: which strategy), MapLauncher (does it), SplitScreenAccessibilityService
media/      MediaRepository (Wavelength via MediaBrowserCompat + other apps via MediaSessionManager),
            chooseMediaSource (pure), WavelengthWidgetHost (AppWidgetHost), MediaNotificationListener
vehicle/    VehicleDataSource interface, GpsSpeedSource, DrivingState (pure: "is the car moving" for bigger buttons)
vehicle/obd ObdProtocol (pure: ELM327 commands and answers), Elm327Source (Bluetooth SPP connection and polling)
daynight/   SunPosition (pure: sun elevation, so ThemeMode.AUTO_SUN goes dark at sunset)
phone/      ContactsRepository: favourite/frequent contacts, calling, finding the dialer or BT phone app
automation/ CarStart (boot, screen-on after a long sleep, power back: bring the dashboard up), CarStartRules (pure)
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

- **Driving mode**: `LocalCarSizes` (in `ui/Common.kt`) holds the touch target, dock height and similar sizes.
  `DashboardActivity` provides `CarSizes.Driving` while `DashboardViewModel.drivingMode` is on. New cards should
  size their buttons from `TouchTarget` or `LocalCarSizes` so they grow too.
- **OBD2**: `Elm327Source` connects only between the dashboard's onStart and onStop. The `CAR_DATA` module is
  hidden until an adapter is picked. More PIDs: add an `ObdPid` and a field on `ObdReading`.
- **Car start**: head units mostly sleep instead of powering off, so `CarStart` watches screen-on (after 2+ minutes
  off), boot, and power coming back, and starts `DashboardActivity` with `EXTRA_CAR_START`. Android 10+ blocks
  activity starts from the background unless "display over other apps" is granted; settings offer that.
- **Layouts are modular**: a `DashboardLayout` is columns of rows of `WidgetType` modules (the map is
  just the `MAP` module), with the dock along the bottom. It serialises to JSON (`toJson`/`fromJson`),
  so a layout editor, shared layout files, or a config server (for example a small Docker service that
  builds layouts and themes) only need to hand the launcher JSON. Built-in layouts: map right, map left,
  no map. `DashboardScreen` renders whatever layout it is given.
- **Config server**: `server/` is that Docker service (Node, no dependencies, web editor). The launcher's
  `ConfigRepository` fetches `/api/config`, keeps the last good copy in `files/server_config.json`, and
  `ServerConfig.parseServerResponse` skips any layout or theme it can't read (for example a module added to
  the server before the app). A server layout with a built-in's id replaces it. Server themes map onto the
  Material colour scheme in `CarLauncherTheme`. Adding a module means a new `WidgetType` here and in
  `WIDGET_TYPES` in `server/server.js`.
- **Profile sync**: everything user-facing is in the one `launcher_settings` DataStore, so export and
  import cover the whole profile.
