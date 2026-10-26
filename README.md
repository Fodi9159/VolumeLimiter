# Volume Limiter

Android app that caps the device's maximum volume level. Built for **Android TV / Google TV**
boxes, where the stock OS offers no per-app or global volume limit, handy for keeping the
night quiet, or for stopping kids (and guests) from pinning the volume to max.

Works two independent ways, and uses both at once:

- **Volume-key interception**, an accessibility service sees `KEYCODE_VOLUME_UP` before the
  system does and pulls every stream back down to your limit, so pressing up past the limit
  does nothing at all.
- **Continuous clamping**, a foreground service polls all audio streams every 200 ms and also
  reacts to `VOLUME_CHANGED_ACTION`, so volume raised by any other app, a remote, a
  Bluetooth device or a cast session gets clamped too.

## Requirements

| | |
|---|---|
| Android version | 5.0 (API 21) and up |
| Target SDK | 34 |
| Build tools | Android Gradle Plugin 8.1.0, Gradle 8.0, JDK 17 |
| Dependencies | `androidx.leanback`, `androidx.appcompat`, `androidx.preference` |
| Extra setup | Accessibility service must be enabled manually (see below) |

## Install

### From source

```bash
git clone https://github.com/Fodi9159/VolumeLimiter.git
cd VolumeLimiter
./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r app-debug.apk` or copy it to the device and open it in a file manager.

### Side-load on a TV

Enable *Developer options* → *ADB debugging* on the TV, then:

```bash
adb connect <tv-ip>
adb install app-debug.apk
```

## Setup

1. Open **Volume Limiter** from the launcher (or open `volumelimiter://open`, the app
   registers a deep link, so you can launch it from any browser or `adb`:
   `adb shell am start -a android.intent.action.VIEW -d "volumelimiter://open"`).
2. Drag the slider or type a number. Default limit is **50** out of 100.
3. Tap **Start protection**.
4. Tap **Enable volume-key blocking** and switch on *Volume Limiter* under
   **Settings → Accessibility → Downloaded apps**.

Step 4 is required for the volume-key interception. The clamp service alone already caps
volume changed by other means, but the key handler only exists once the accessibility
service is running.

### Android TV: enable accessibility over ADB

The TV accessibility screen is easy to get lost in, so the app ships a shortcut, on the TV
layout the settings screen prints the exact commands:

```bash
adb shell settings put secure enabled_accessibility_services com.volume.limiter/.VolumeLimiterService
adb shell settings put secure accessibility_enabled 1
```

## Usage

- **Current volume** shows the live music stream level next to the limit.
- **Start / Stop protection** toggles the clamp service. The ongoing notification shows
  `Limiter: <limit> | Music: x Notif: y System: z` so you can see the clamp working.
- **Show app icon** hides the launcher icon when you want the app out of sight. Bring it
  back with the deep link above, or by re-enabling the component:
  `adb shell pm enable com.volume.limiter/.LauncherActivity`.
- The limit and the enabled state are stored in `SharedPreferences`
  (`VolumeLimiterPrefs`), so they survive restarts.
- **BootReceiver** restarts the clamp service automatically after a reboot, but only if
  protection was on when the device shut down.

## Streams covered

`STREAM_MUSIC`, `STREAM_VOICE_CALL`, `STREAM_NOTIFICATION`, `STREAM_SYSTEM`: the four
streams a TV normally exposes. Each is clamped independently, and the limit is an absolute
index on the stream's own 0–100 scale, so set the slider with the stream you care about at
its real maximum first.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/volume/limiter/
│   ├── LauncherActivity.java      # opens Settings and finishes
│   ├── SettingsActivity.java      # UI: slider, toggle, icon switch, live volume
│   ├── VolumeLimiterService.java  # accessibility service, intercepts volume keys
│   ├── VolumeClampService.java    # foreground service, polls + VOLUME_CHANGED clamp
│   └── BootReceiver.java          # restarts clamp on BOOT_COMPLETED
└── res/
    ├── layout/activity_settings.xml         # TV / leanback layout
    ├── layout/activity_settings_phone.xml   # phone layout
    ├── xml/volume_limiter_service.xml       # accessibility config
    ├── values/strings.xml, themes.xml
    └── drawable/ic_notification.xml, tv_banner.xml
```

## Permissions

| Permission | Why |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Change stream volumes |
| `FOREGROUND_SERVICE` | Keep the clamp running |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14 requirement for the service type used |
| `RECEIVE_BOOT_COMPLETED` | Restart protection after reboot |
| `BIND_ACCESSIBILITY_SERVICE` | Volume-key interception |

## Known limitations

- The clamp service polls every 200 ms. It's cheap, but not free, battery use is small yet
  non-zero while protection is on.
- Volume-key interception stops working if Android kills the accessibility service (low
  memory, or the user revoking it). The TV layout hints at the ADB command because this is
  the most reliable way to get it back on a TV.
- Limits are absolute stream indices, not percentages of each stream's own range. On a
  device whose music stream maxes at 25, a limit of 50 is simply "full".
- Release builds are unsigned and unminified (`minifyEnabled false`). Sign it yourself
  before distributing.

## License

MIT: do what you like, no warranty.
