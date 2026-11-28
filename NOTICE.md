# NOTICE

This repository is a **modified fork** of [termux/termux-app](https://github.com/termux/termux-app),
originally released under **GPLv3 only**. The original copyright notices and
`LICENSE.md` are retained in full.

Upstream baseline at the time this tree was exported: `termux/termux-app` master,
commit `d22ea57`.

## Modifications

| Area | Change |
|------|--------|
| `app/src/main/java/com/termux/app/LauncherActivity.java` | Rewritten as an Android TV launcher (5-column grid, D-pad navigation, hide/sort apps, boot autostart with delay tiers). Registers `LAUNCHER`, `LEANBACK_LAUNCHER`, `HOME` and `IOT_LAUNCHER` entry points. |
| `tvdecoy/` | New module `com.hzc.tvdecoy`: a separate-UID dummy activity used to absorb the foreground-app kill performed by MIUI TV when the screen turns off, so background `sshd`/`crond` processes survive. Optional, degrades gracefully when not installed. |
| `terminal-emulator/` | Image rendering removed: `TerminalSixel`, `ITermImage`, `TerminalBitmap` deleted; `TerminalEmulator` correspondingly reduced. Sixel and iTerm2 inline images are therefore **not supported**. |
| `app/build.gradle` | ABI splits limited to `arm64-v8a` and `armeabi-v7a`; `universalApk` disabled, so there is no all-ABI fallback package. |
| `.github/workflows/release.yml` | Custom release pipeline: downloads prebuilt offline `.deb` bundles, two-ABI split APKs. |
| `.github/workflows/release-android5.yml` | Added. Builds the `apt-android-5` variant (bootstrap `2022.04.28-r6`) for Android 5.0–6.0, triggered by `a5-v*` tags or manually. |
| `.github/workflows/build-decoy.yml` | Added. Builds the `tvdecoy` module. |
| `.github/workflows/build-decoy-release.yml` | Added. Publishes a `tvdecoy` release APK to GitHub Releases when a `decoy-v*.*.*` tag is pushed; version name/code are derived from the tag and asserted against the built APK. |

## Signing

Release builds are signed with a key supplied at build time from the repository
secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
No release keystore is stored in this repository. Debug builds use
`app/testkey_untrusted.jks`, the upstream public test key, which is **not** a
secret and must not be used to sign distributed builds.

## License

All modifications above are distributed under the same terms as the upstream
project: GPLv3 only. See `LICENSE.md`.
