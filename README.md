# Skylight Hatch — v0.1.0 prototype

A minimal, reversible Android app for the Skylight Calendar Max
(Rockchip RK3566, Android 13 / SDK 33, portrait 1440x2560 on the wall).

It draws one small muted gear icon on Skylight's permanent bottom
bar, immediately left of the Sleep icon, in the empty gap between
Photos and Sleep (measured from the 2026-10-29 adb screencap:
bar y 2478–2560, Photos (809, 2504), Sleep (1283, 2504),
Settings (1387, 2504), gap x 864–1246).

- Tap the gear: nothing.
- Hold it for 5 seconds: a blue progress ring fills, then Android
  Settings opens (plain Settings, not Developer Options directly).
- Short taps on Skylight's own Settings gear are untouched: this
  icon does not cover it, so no tap-passthrough hack is needed.

No root, no system modification, no launcher replacement.
Uninstalling the app removes the icon completely.

## Build (on a machine with the Android SDK, e.g. the one grafmote
## was built on — the Blum VM has no Java/SDK, so it cannot build
## this APK itself)

Requires JDK 17, Android SDK with platform 33, and Gradle 8.x
(Android Studio's bundled Gradle is fine).

```bash
cd skylight-overlay
gradle assembleDebug
# or, in Android Studio: Open project > Run > assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Debug signing is fine for this lab prototype. For a keeper build,
sign with a release key: a new alias in the grafmote keystore is
the recommended middle ground (same file to back up, separate key
per app). After generating a new alias, re-attach the updated
keystore file in Vaultwarden — otherwise the 3-2-1 backup holds
the old file without the new key.

## Install + grant (Max plugged in via USB, adb authorized)

```bash
adb devices
# expect: device, not unauthorized

adb install -r app/build/outputs/apk/debug/app-debug.apk

# Draw-over permission: normally granted in Settings, which is the
# chicken-and-egg on the wall. Via adb it is one appop:
adb shell appops set --uid com.cogdev.skylightoverlay SYSTEM_ALERT_WINDOW allow
adb shell appops get com.cogdev.skylightoverlay SYSTEM_ALERT_WINDOW
# expect: allow

# Notification permission for the foreground-service notification
# (harmless if this is already granted):
adb shell pm grant com.cogdev.skylightoverlay android.permission.POST_NOTIFICATIONS

# Start the service directly (no need to open the activity):
adb shell am startservice -n com.cogdev.skylightoverlay/.OverlayService

# Or open the control panel and tap Start:
adb shell am start -n com.cogdev.skylightoverlay/.MainActivity
```

## Test, in order

1. Screenshot, check position:
   ```bash
   adb exec-out screencap -p > skylight-overlay.png
   ```
   Expect: a small grey gear immediately left of Sleep, inside
   the bar, overlapping no Skylight icon or label.

2. Short tap on the gear: nothing happens. Tap Skylight's own
   Settings gear: Skylight Settings still opens normally.

3. Hold the gear for 5 seconds: the blue ring fills, Android
   Settings opens. Back / Home returns to the Skylight calendar.

4. If the icon does not appear at all, check logcat first:
   ```bash
   adb logcat -d | grep -i -E 'skylight|OverlayService|HatchView' | tail -50
   ```
   The most likely cause is the draw-over permission not actually
   granted (step above), or the service being killed. Do not
   change Skylight, sleep, or Dream settings to debug this.

## Known untested items (v0.1.0)

- Whether the bar is Skylight in-app UI (overlay draws over it)
  vs the Android system bar (overlay sits below it). The
  screenshot test above settles this: if the gear is visible
  over the bar, it is in-app.
- Whether `skylight.watchdog` or battery optimization kills the
  foreground service after ~30 minutes, or after a reboot.
  BootReceiver is included, but boot persistence is untested.
- Whether the overlay survives a Skylight app update that moves
  the bar layout. Position is relative fractions (82.0% width,
  97.8% height, size = 3.2% of height), not hardcoded pixels,
  but a layout move still needs a re-measure.

## Uninstall (full revert)

```bash
adb uninstall com.cogdev.skylightoverlay
```

No settings are changed by this app, so there is nothing else
to revert. The probe baseline (stay_on=1, screen_off_timeout=0,
FlipperDream, etc.) is untouched by v0.1.0.
