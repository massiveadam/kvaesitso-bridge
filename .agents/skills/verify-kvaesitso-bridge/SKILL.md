---
name: verify-kvaesitso-bridge
description: Build, lint, and exercise Kvaesitso Bridge on a disposable Android emulator using real notifications and a standard widget host.
---

# Verify Kvaesitso Bridge

## Launch

From the repository root, with a full JDK 21 and Android SDK configured:

```sh
./gradlew build testDebugUnitTest lint assembleDebug
./gradlew -PdeviceFixture :device-fixture:assembleDebug
```

Start a disposable Android 13+ emulator using Android Studio or `emulator -avd YOUR_AVD`. Choose an explicit serial from `adb devices -l`. Install both APKs:

```sh
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r tools/device-fixture/build/outputs/apk/debug/device-fixture-debug.apk
```

The optional fixture module is excluded from the default project and never ships in the Bridge APK.

## Doctor

```sh
adb devices -l
adb -s emulator-5560 shell getprop sys.boot_completed
adb -s emulator-5560 shell getprop ro.build.version.sdk
adb -s emulator-5560 shell dumpsys package com.adamdelisi.kvaesitsobridge
```

Require a booted, explicitly selected disposable emulator. Do not drive an unknown shared emulator or a personal phone. The driver refuses physical device serials. It clears Bridge and fixture data, changes listener access, posts synthetic notifications, and binds a widget. It does not open unrelated apps or touch another device.

## Drive

```sh
python3 tools/verify_device.py --serial emulator-5560 --evidence .verification/device
python3 tools/verify_device.py --extended-only --serial emulator-5560 --evidence .verification/device
```

Use `--adb /absolute/path/to/adb` if adb is not on PATH. The extended pass also kills the background process, reinstalls the APK, reboots the emulator and changes its night mode and font scale. It restores appearance and clears synthetic notifications at the end. The script drives the app's actual buttons, Android notification access, real NotificationManager events, and the widget's own PendingIntents/callbacks. It waits on observable UI text instead of altering repository state.

Also sign `app/build/outputs/apk/release/app-release-unsigned.apk` with a disposable local test key using the SDK's `apksigner`, install it, and run `--release-smoke --evidence .verification/release`. This resets Bridge preferences and positively verifies minified startup, widget content, notification opening and the reflective dismiss callback. Restore the debug APK afterward. Never commit the key or a test-signed release APK.

After the debug passes, run `--source-uninstall --evidence .verification/uninstall` to verify the observed source disappears from settings and DataStore. This removes the fixture APK and its widget host; run it last.

For manual checks, start Bridge with `adb -s emulator-5560 shell am start -n com.adamdelisi.kvaesitsobridge/.MainActivity`. Notification access opens Android's detail settings. Kvaesitso setup uses its stock widget picker.

## Evidence

The driver writes PNG screenshots, UI XML trees, device API version and `results.json` beneath the requested evidence directory. A failed assertion identifies the expected visible outcome. Capture app-specific logcat after a failure; avoid collecting real notification content from personal devices.

Build reports live at `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. APKs live in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

## Cleanup

The driver clears its synthetic notifications at the end. For a disposable emulator, uninstall the fixture and Bridge or stop the emulator:

```sh
adb -s emulator-5560 uninstall com.adamdelisi.bridgefixture
adb -s emulator-5560 emu kill
```

Keep screenshots and reports. Never stop an emulator that this verification run does not own.

## Features

| User path | Proof |
| --- | --- |
| Onboarding and Android settings button | Missing-access screen and system settings screenshot |
| Grant access and zero notifications | Enabled status and empty widget |
| Receive a notification | Content visible in standard AppWidgetHost |
| Tap a row | Source activity displays the clicked notification ID |
| Dismiss a row | Android cancels notification and row disappears |
| Same-app messages and updates | Independent rows, update replaces old text |
| Per-app App only / Hidden | UI controls change the widget content and empty state |
| Ongoing background status | Default feed suppresses it |
| Android notification groups | Children display without aggregate duplication |
| Access revocation and reconnect | Text disappears, then Android active state repopulates |
| Configuration and appearance | Manually inspect light/dark text and large fonts |
| Reboot, replacement and restoration | Reboot disposable emulator, reopen host and compare active notifications; do not restore saved notification content |
