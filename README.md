# GoatLauncher

A home screen for Android TV and Google TV.

## Screenshots

| | |
|---|---|
| ![Continue watching: Spotlight](docs/screenshots/home-android-tv.jpg) | ![Continue watching: Spotlight with what's next](docs/screenshots/home-android-tv-spotlight-next.jpg) |
| ![Continue watching: row of cards](docs/screenshots/home-android-tv-cards.jpg) | ![Continue watching: apps on the side](docs/screenshots/home-android-tv-side.jpg) |
| ![Continue watching: carousel](docs/screenshots/home-android-tv-carousel.jpg) | ![Home, tvOS style](docs/screenshots/home-tvos-day.jpg) |
| ![Home, tvOS style at night](docs/screenshots/home-tvos-night.jpg) | ![Change icon: use the app's icon](docs/screenshots/change-icon.jpg) |
| ![Settings](docs/screenshots/settings.jpg) | ![Settings, wallpaper](docs/screenshots/settings-wallpaper.jpg) |
| ![Appearance, Android TV style](docs/screenshots/appearance-android-tv.jpg) | ![Appearance, tvOS style](docs/screenshots/appearance-tvos.jpg) |
| ![Aurora focus effect](docs/screenshots/effect-aurora.jpg) | ![Floating ring focus effect](docs/screenshots/effect-floating-ring.jpg) |

Continue watching from a Jellyfin client (RealJF) with open movies: Big Buck Bunny, Sintel, Tears of Steel and Cosmos
Laundromat (© Blender Foundation, CC BY 3.0/4.0, [blender.org](https://studio.blender.org/films/)) and Sita Sings the
Blues (Nina Paley, CC0, [sitasingstheblues.com](https://www.sitasingstheblues.com/)); and from GoatTV with a demo
playlist (made-up channels). Wallpaper: "Marmolada, Italy" by Marco Bonomo (CC0, Wikimedia Commons).

## Install

Latest version: https://github.com/GOAThings/GoatLauncher/releases/latest/download/goatlauncher.apk

On the TV, open the [Downloader](https://www.aftvnews.com/downloader/) app and enter the code **2462776**.

To make it the home screen, with adb:

```bash
adb shell cmd package set-home-activity cc.slokker.goatlauncher/.MainActivity
```

Once installed, the launcher finds new versions by itself (Settings → Updates).

## Features

- Favorite apps, the time and weather, and **Continue watching** from the apps on the TV (row of cards,
  spotlight, spotlight with what's next, apps on the side or carousel), including YouTube with a linked Google
  account.
- Two layouts: Android TV style or tvOS style.
- Hide, rename, reorder and change the icon of any app.
- Wallpapers: built-in, your own images, or a photo of the day from Wikimedia Commons or NASA, darker at night.
- Focus effects and animations.
- Ambient mode and screensaver.
- Remote buttons that open the app you choose.
- Open an app when the TV turns on.
- Parental control.
- GOAT account sync.
- 15 languages.

## Build

Needs Android Studio (for the Android SDK and JDK).

```bash
./gradlew assembleDebug
```

Release builds are signed with a key that is not part of this repository.

## License

GoatLauncher is free software under the [GNU General Public License v3.0](LICENSE).

The license covers the code only. The names "GOAT" and "GoatLauncher" and their logos are not licensed: forks
must use their own name and artwork.

Third-party: [Adwaita Sans](third_party/adwaita-sans-OFL.txt) (SIL Open Font License 1.1).
