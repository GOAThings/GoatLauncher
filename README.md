# GoatLauncher

A home screen for Android TV and Google TV (Kotlin, Jetpack Compose for TV), made for the NVIDIA SHIELD.

This repository is private. Published versions and the user-facing README are in
[GOAThings/GoatLauncher-Releases](https://github.com/GOAThings/GoatLauncher-Releases).

## Build

Needs Android Studio (for the Android SDK and JDK).

```bash
./gradlew assembleDebug
```

Release builds are signed with the shared GOAT key, read from `keystore.properties` (not in git). Without it,
the release build is signed with the debug key and can't update an installed release.

## Release

```bash
scripts/release.sh 0.8.7 "What changed (in English)"
```

The script bumps `versionCode` and `versionName`, builds the release APK, keeps the R8 mapping in
`build/mapping-<version>.txt` (crash reports from TVs are obfuscated without it), commits, tags, pushes and
publishes `goatlauncher-<version>.apk` and `goatlauncher.apk` (the fixed name the Downloader code and the in-app
updater use) to GoatLauncher-Releases.

## Code

- `MainActivity`, `ui/`: the launcher (home, apps, settings, activation with a GOAT account).
- `HomeGuardService`: optional accessibility service that keeps GoatLauncher as the home screen, opens apps from
  remote buttons, enforces parental locks and opens apps at power-on (`AutoStart`). It only asks Android for key
  events when it needs them (see `updateKeyFilter`): on the SHIELD's Android 11, key filtering plus a volume key
  from a phone remote crashes `system_server`.
- `AmbientDream`: the screensaver.
- `data/`: settings (`SettingsRepository`), installed apps, Continue watching (`WatchNext`), YouTube, weather,
  wallpapers, parental control, updates (`Updater`) and the GOAT backend (`api/`).
- `data/PhoneApps`: a small HTTP server on port 6470, private addresses only, for
  [GoatRemote](https://github.com/GOAThings/GoatRemote): `GET /apps`, `GET /icon/<package>` and
  `POST /volume/up|down|mute`.

## Testing on a SHIELD

Install the release build over the existing one (`adb install -r`; a debug build has a different signature), then
make it the home screen again:

```bash
adb shell cmd package set-home-activity cc.slokker.goatlauncher/.MainActivity
```
