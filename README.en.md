# ToFA

[中文](README.md) ・ [**English**](README.en.md)

An offline TOTP authenticator for Android phones and Android watches. Secrets never leave the device; no account is required and it works without a network connection.

## Download

**[Download ToFA 0.2.2 (Release)](https://github.com/whmwws/ToFA/releases/tag/0.2.2)**

[Phone APK](https://github.com/whmwws/ToFA/releases/download/0.2.2/ToFA-0.2.2-phone.apk) · [Watch APK](https://github.com/whmwws/ToFA/releases/download/0.2.2/ToFA-0.2.2-watch.apk)

Gitee release: <https://gitee.com/whmwws/ToFA/releases/tag/0.2.2>

Website: <https://whmwws.icu/tools/ToFA> ・ Activator: <https://whmwws.icu/tools/ToFA/activate> ・ Donate: <https://whmwws.icu/tools/ToFA/donate>

## Overview

ToFA is a fully local, offline two-factor authentication (TOTP) tool for people who want codes on their wrist without handing secrets to any cloud service. The phone app imports, manages, encrypts and backs up accounts, and pushes them to the watch; the watch shows codes at a glance, copies them, supports manual entry, and keeps working while disconnected.

- **No online dependency**: core features run offline with no internet backend. Activation, import and export stay on the device, and phone-to-watch sync uses a local adb connection.
- **Zero account**: no sign-up, no upload, no analytics.
- **Two-device workflow**: the phone pushes secrets, theme colors and UI language to the watch over wireless debugging (adb); the watch can also generate codes on its own.

## Modules

| Directory | Description | Version rule |
| --- | --- | --- |
| [`phone/`](phone) | Android phone app (Jetpack Compose) | `0.x.y` |
| [`watch-android/`](watch-android) | Android watch app (Jetpack Compose, round and square screens) | `0.x.y` |
| [`open-source/`](open-source) | Contribution, security, privacy, licensing, release material and platform templates | — |
| `watch/`, `phone-hmos/` | Early HarmonyOS experiments; not part of the current build or release | — |

The single source of truth for the version is `versionName` in each `app/build.gradle.kts`; the UI reads it through `PackageManager` instead of hard-coding it.

### Features

**Phone**

- Add, edit, delete and drag-reorder TOTP accounts; configurable display-name style
- Scan a QR code or pick an image, manual entry, import from `otpauth://` and common export formats
- Encrypted export (custom password or device-unique key), or a TXT file with one `otpauth://` URI per line; imports are auto-detected
- App lock, wipe after repeated failures, emergency password, recent-task privacy, screenshot blocking
- Light/dark themes and accent colors; UI language (Simplified Chinese / English / Traditional Chinese / Japanese, with offline language pack import)
- Push secrets, activation file, theme color and language to the watch over wireless debugging; install and launch the watch app from the phone
- First-run privacy notice and activation guide, plus an open-source licenses screen

**Watch**

- One account per page, swipe or rotate the crown to page, ring countdown progress
- Optional "round page" layout: square by default, shaped to the round screen when enabled
- Copy codes, show a QR code, manual entry and secret management
- Independent app lock, password and emergency password
- Built-in Chinese / English; more languages after the phone pushes a language pack
- Global accent color, light/dark mode, launch cover, recent-task privacy

## Privacy

- All secrets are stored only in the app's private storage on the device; export files go wherever the user chooses.
- The phone's `INTERNET` permission is used only for the user-enabled local adb / wireless debugging connection; there is no internet backend and no analytics or ad SDK.
- Copying a code schedules an automatic clipboard wipe; recent-task thumbnails can hide codes.
- Activation is verified locally; activation codes are generated offline by the website page.

## Build

Requirements: JDK 17 and Android SDK 34.

```bash
# 1. Build the watch app first
cd watch-android
./gradlew :app:assembleDebug

# 2. Then build the phone app; it syncs the latest watch APK asset automatically
cd ../phone
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat :app:assembleDebug`. Before the first build, create `local.properties` in each project directory (do not commit it):

```properties
sdk.dir=/path/to/Android/Sdk
```

The phone app ships `phone/app/src/main/assets/watch-app.apk` as the bundled watch installer, so the file must stay in the source tree. When building yourself, build the watch app first and copy the output to that path.

## Install

- Phone: install `app-debug.apk` or a release APK. Minimum Android 8.0 (API 26).
- Watch: minimum API 26, adapted to round screens. The phone's "Transfer" screen can install and launch the watch app over wireless debugging.

## License

This project is licensed under the [Apache License 2.0](LICENSE). That choice allows commercial and closed-source distribution while keeping patent and attribution requirements, which suits an offline security tool meant to be widely integrated or redistributed. The in-app "Open source licenses" screen lists the third-party libraries actually used, their licenses and project URLs.

- [Full open-source handbook](OPEN_SOURCE.md): security cleanup, GitHub / Gitee, build, signing, release and maintenance.
- [Open-source material index](open-source/README.md): contribution, security, privacy, support, third-party notices and release templates.

## Contributing

Issues and pull requests are welcome; please read the [contribution guide](open-source/CONTRIBUTING.md) first. Changes touching secret handling or the encrypted format must include test notes. Never paste real secrets or codes in an issue. Report security vulnerabilities privately following the [security policy](open-source/SECURITY.md).
