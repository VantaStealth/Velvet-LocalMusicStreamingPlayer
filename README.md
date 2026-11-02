# Velvet

**Your music. Everywhere. No ads. No paywall.**

Velvet is a local music player for Windows and Android, built around the files you already own. It has a floating dynamic island, a real spectrum visualizer, a custom volume controller, and Velvet Cloud — a hosted copy of the shared library that both apps can pull from.

[![License: Velvet Community 1.0](https://img.shields.io/badge/license-Velvet%20Community%201.0-8a2be2.svg)](LICENSE)
![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg)

![Velvet desktop](docs/velvet-desktop.png)

## Download

| Platform | File | Requirements |
| --- | --- | --- |
| Windows 10 2004+ / 11 (x64) | [`Velvet.exe`](Velvet.exe) | None — self-contained, no .NET install |
| Android 8.0+ | [`Velvet-1.8.apk`](Velvet-1.8.apk) | None |

The Windows build is a single self-contained executable. It carries its own .NET runtime, WPF, and native libraries, so a fresh PC can double-click it and play. There is no separate dependency installer to run.

## What's new in 1.8

- **The volume pill is alive again** — the Android overlay is the full 1.6 design at 79% translucency. The liquid sloshes against the glass, the surface rolls in a double wave, and bubbles rise, drift, and pop.
- **Disco mode** — beat-synced colour fields drift across the room, the room flashes on kicks, and lyrics fade through the centre of the stage.
- **Synced lyrics** — drop a matching `.lrc` file beside a track and the lyric stage follows playback. No lyrics are bundled with Velvet, so every word comes from your own library.
- **Veins hit every beat** — the desktop veins now read the spectrum's smoothed bass on every rendered frame, so they breathe with the kick instead of flashing or skipping beats.
- **Desktop volume HUD, smaller and clean** — 320×132 glass card with the glow given room to fade, so there are no rectangular cut-outs.
- **Velvet Cloud failover** — manifests can carry backup mirror URLs, and both apps try each source in order. The manifest itself has repository and CDN fallbacks. Backup hosts live entirely in the backend and never appear in either app.

## Velvet Cloud

Velvet Cloud lives on a public GitHub release, outside this repository:

```
https://github.com/VantaStealth/Velvet-LocalMusicStreaming/releases/download/velvet-cloud/manifest.json
```

- The manifest and all 30 tracks are hardcoded into both apps. There is nothing to configure.
- On launch, both apps notice which cloud tracks are missing from your selected folder and offer to download them.
- Android can download from Velvet Cloud. There is no upload button on mobile, so the cloud cannot be filled by strangers.
- Desktop can download and publish. Publishing uses your local GitHub credential (Git Credential Manager); `tools/publish-cloud.mjs` is the command-line path used to seed the release.
- A partial download is deleted, not left behind as a broken track.
- **The release must stay public.** Private GitHub release assets require an authenticated request, and the apps download anonymously. Making the release private would break cloud downloads for both apps.

### Backup mirrors

Every track can carry a `mirrors` array in the manifest. Both apps try the primary URL, then each backup in order, and only report failure when every source fails. If the release manifest itself disappears, clients fall back to the committed manifest in the repository and its CDN copy.

Mirrors are configured only in the backend. Neither app shows, names, or links a backup host. To add an S3-compatible store, set `VELVET_MIRRORS` before running the publisher with a JSON array of endpoints:

```json
[
  {
    "endpoint": "https://<account>.r2.cloudflarestorage.com",
    "bucket": "velvet-cloud",
    "region": "auto",
    "accessKey": "...",
    "secretKey": "...",
    "publicUrl": "https://music.example.com"
  }
]
```

The publisher signs and uploads each track with AWS Signature V4, skips files already present with the right size, and writes the public URLs into the manifest. Credentials stay in the environment and are never written to the repository, the manifest, or either app.

**No host can guarantee that copyrighted media will never be removed.** Backups reduce the chance that one takedown breaks every client; they are not immunity. Velvet Cloud contains copyrighted recordings, so keep your own copy of the library. Use backup storage you control and are allowed to use.

### Lyrics

Velvet does not ship lyrics. Place a `.lrc` file with the same name as the track beside it (`Song.mp3` → `Song.lrc`) and open **LYRICS** on Android or **Disco mode** on desktop. Timed `[mm:ss.xx]` lines follow playback; metadata tags are ignored. If there is no sidecar file, the app says so instead of inventing lyrics.

## Volume controller, everywhere

**Windows.** While Velvet is running — including from the tray at boot — the volume keys are captured system-wide. Velvet steps the Windows media volume through Core Audio and draws its own compact glass HUD at the bottom of the screen. The stock Windows volume flyout is suppressed. Mute works too.

**Android.** Open the **More** menu and choose **Volume keys takeover**. Enabling the Velvet accessibility service lets volume key presses open the full Velvet volume pill anywhere on the phone: on the lock screen, inside games, or with nothing playing at all. The pill is 79% translucent and animated even when paused. Tap **MIX** on the pill for Media, Ring, Alerts, Alarm, Calls, and the Velvet channel. The service only filters volume keys and never reads screen content.

Android exposes one shared media stream to every app, so the mixer controls device streams while the **Velvet channel** slider is separate; it can push Velvet up to 150% so it sits above a game or video.

## The floating island

- Drag it anywhere; it snaps to the top edge and to the horizontal centre.
- Double-click to switch between the full island and the one-line minimal island.
- The minimal island shows cover, title, time, previous, play/pause, and next.
- Drag its right edge to resize it horizontally.
- While dragging, a glowing blue centre line appears when the minimal island is snapped to the top middle, then disappears when you release.
- Resize, rotate, opacity, and minimal state are remembered between launches.

## On Android

<table>
  <tr>
    <td align="center"><img src="mobile%20screenshots/Velvet%20Mobile%20app%20screenshot.jpg" width="240"><br><sub>Library</sub></td>
    <td align="center"><img src="mobile%20screenshots/Audio%20Visualizer%20Mobile.jpg" width="240"><br><sub>Spectrum</sub></td>
    <td align="center"><img src="mobile%20screenshots/volume%20controller%20expanded.jpg" width="240"><br><sub>Volume mixer</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="mobile%20screenshots/volume%20controller%20normal.jpg" width="240"><br><sub>Volume rail</sub></td>
    <td align="center"><img src="mobile%20screenshots/velvet%20notification%20-%20media%20controller.jpg" width="240"><br><sub>Media notification</sub></td>
    <td align="center"><img src="mobile%20screenshots/velvet%20notification%20locked.jpg" width="240"><br><sub>Lock screen</sub></td>
  </tr>
</table>

- Media3 playback service with a framed, accent-tinted notification.
- Procedural cover art for tracks without embedded artwork.
- Favourites, sorting, search, albums, and a daily Velvet Mix.
- **DISCO** mode for beat-synced lights, and **LYRICS** for timed `.lrc` sidecars.
- Per-stream mixer for media, ring, alerts, alarms, and calls, plus the Velvet channel.

## Features

- **Local library** — point Velvet at a folder and it indexes MP3, M4A/AAC, FLAC, WAV, OGG, and Opus files, tags, and embedded artwork.
- **Velvet Cloud** — download missing tracks from the hosted shared library on both platforms, with backend-only mirror failover.
- **Real spectrum** — 48-band playback analysis drives the desktop lines, the mobile visualizer, and the vein beat response.
- **Disco mode** — moving colour fields, kick flashes, and timed lyrics on both platforms.
- **Tray-first Windows app** — closing the window keeps playback alive. Velvet pins itself next to Wi-Fi, volume, and battery in the notification area.
- **Starts with Windows, stays out of the way** — the tray icon is registered at boot; clicking it opens the app, and it is not forced open on login.
- **Windows media session** — SMTC integration so media keys and system media controls drive playback.
- **Portable window chrome** — custom title bar, rounded corners, per-monitor DPI awareness, and work-area clamping. No window mods required.
- **No ads, no tracking, no account** — the only network access is Velvet Cloud downloads.

## First run

1. Start Velvet and choose the folder that holds your music.
2. Velvet scans it and remembers the folder permission.
3. If Velvet Cloud has tracks that folder does not, a banner offers to download them.
4. On Windows, close the window when you are done — playback continues from the tray.

Settings, the library cache, generated covers, and logs live in `%LOCALAPPDATA%\Velvet` on Windows.

## Repository layout

```
app/                        Android app (Kotlin, Jetpack Compose, Media3)
desktop/Velvet.Desktop/     Windows app (C#, WPF, NAudio, TagLibSharp)
docs/                       Desktop screenshot
mobile screenshots/         Android screenshots used above
tools/                      Cloud publishing and screenshot helpers
```

Audio files are **not** stored in this repository. They live in the public `velvet-cloud` GitHub release and are downloaded by the apps.

## Build from source

### Windows

Requirements: Windows 10 2004+ or Windows 11, and the [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).

```powershell
dotnet publish desktop/Velvet.Desktop/Velvet.Desktop.csproj -c Release -o publish
```

`publish/Velvet.exe` is a self-contained single-file build. Copy it anywhere and run it.

### Android

Requirements: JDK 17 and the Android SDK.

```powershell
.\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

### Publishing to Velvet Cloud

`tools/publish-cloud.mjs` reconciles the `velvet-cloud` release with your local library: it uploads new tracks, replaces changed ones, removes tracks you deleted, rebuilds `manifest.json`, and republishes it. It reads a GitHub token from Git Credential Manager; no token is stored in this repository.

```powershell
node tools/publish-cloud.mjs
```

## Notes

- Velvet never modifies your audio files. Generated artwork is written to Velvet's own app-data folder.
- Tray pinning uses the same per-user registry setting Windows writes when you drag an icon out of the overflow flyout.
- The Windows volume hook is active only while Velvet is running. Quitting Velvet returns volume keys to Windows.
- Android's accessibility service is optional. Without it, the in-app volume HUD still works while Velvet is open.

## License

[Velvet Community License 1.0](LICENSE) © 2026 Umar.

Velvet is free to use, study, modify, and share. You may not add ads, lock features, or put Velvet or any derivative behind a paywall. Anyone who wants to monetize Velvet must get written permission from the copyright holder and compensate them first. Velvet itself stays free forever.
