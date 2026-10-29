# Skylight Escape Hatch

Adds a small gear icon to the Skylight bottom bar, next to Sleep.
Tap it: nothing. Hold it for 5 seconds: Android Settings opens.

## Install

```bash
adb devices
adb install -r app-debug.apk
adb shell appops set --uid net.cogdev.skylightoverlay SYSTEM_ALERT_WINDOW allow
adb shell pm grant net.cogdev.skylightoverlay android.permission.POST_NOTIFICATIONS
adb shell am startservice -n net.cogdev.skylightoverlay/.OverlayService
```

## Uninstall

```bash
adb uninstall net.cogdev.skylightoverlay
```
