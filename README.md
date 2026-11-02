# Velvet

**Your music. Everywhere. No ads. No paywall.**

Velvet is a local music player for Windows and Android, built around the files you already own. It keeps your library on your devices, carries synced lyrics with every track it can, and puts the controls where you actually want them: a floating island on the desktop, a full volume controller on the phone, and a cloud library that both apps can pull from.

[![License: Velvet Community 1.0](https://img.shields.io/badge/license-Velvet%20Community%201.0-8a2be2.svg)](LICENSE)
![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg)

![Velvet desktop](desktop%20screenshots/Normal%20desktop%20screenshot.png)

## Download

| Platform | File | Requirements |
| --- | --- | --- |
| Windows 10 2004+ / 11 (x64) | [`Velvet.exe`](Velvet.exe) | None — self-contained, no .NET install |
| Android 8.0+ | [`Velvet-2.0.1.apk`](Velvet-2.0.1.apk) | None |

The Windows build is one self-contained executable. It carries its own .NET runtime, WPF, and native libraries, so a fresh PC can double-click it and play. There is no dependency installer.

### Installing on Android

The APK installs on a phone that has never had Velvet. No old version is required.

1. Download `Velvet-2.0.1.apk` and open it from notifications or a file manager.
2. If Android asks, allow **Install unknown apps** for the browser or file manager you used. On many phones this is **Settings → Apps → Special app access → Install unknown apps**.
3. If Play Protect shows a warning, tap **More details → Install anyway**. Velvet is signed by the project, not distributed through Play, so Play Protect has no reputation data for it.

## What's new in 2.0.1

- **Clean installs.** The APK is signed with the v1, v2, and v3 schemes, so a phone that has never had Velvet can install it directly. OEM installers and Play Protect can still prompt; the steps above cover them.
- **Lyrics that arrive instantly and stay in sync.** Accurate `.lrc` sidecars now ship inside the Android APK and sit beside the desktop library, so a cloud track shows timed lyrics the moment it starts — no network wait. Every candidate is still checked for title, artist, duration, and lyric span before it is shown.
- **No invented timings.** Velvet only displays genuinely synced lyrics. Plain text is never stretched across a track, and a sidecar written for another cut is rejected when its last line falls outside the audio.
- **Artists on cloud tracks.** The Velvet Cloud manifest carries each track's ID3 title, artist, and album. Android applies that catalogue to downloaded files, so tracks no longer fall back to "Unknown artist".
- **From The Start** is in the cloud library, with its synced sidecar.

## Desktop

![Velvet desktop disco mode](desktop%20screenshots/Desktop%20disco%20mode.png)

*Disco mode: sourced beams, a liquid floor, beat-locked fireworks, and animated lyrics.*

![Minimal island and volume controller](desktop%20screenshots/minimal%20island%20and%20volume%20controller%20desktop.png)

*Desktop screenshot of minimized floating island and volume controller.*

The desktop app is a full listening room: library, album grid, search, themes, a Velvet Mix, and a now-playing panel with a spectrum ring. Close the window and playback keeps going from the tray.

## Android

<table>
  <tr>
    <td align="center"><img src="mobile%20screenshots/Velvet%20Mobile%20app%20screenshot.jpg" width="240"><br><sub>Library</sub></td>
    <td align="center"><img src="mobile%20screenshots/Mobile%20Disco%20mode.jpg" width="240"><br><sub>Disco mode</sub></td>
    <td align="center"><img src="mobile%20screenshots/mobile%20volume%20controller%20expanded.jpg" width="240"><br><sub>Volume mixer</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="mobile%20screenshots/mobile%20volume%20controller%20%28cool%20animations%29.jpg" width="240"><br><sub>Volume controller</sub></td>
    <td align="center"><img src="mobile%20screenshots/velvet%20notification%20-%20media%20controller.jpg" width="240"><br><sub>Notification</sub></td>
    <td align="center"><img src="mobile%20screenshots/velvet%20notification%20locked.jpg" width="240"><br><sub>Lock screen</sub></td>
  </tr>
</table>

![Velvet Mobile tour](mobile%20screenshots/mobile%20app%20showcase.gif)

![Flawless cloud syncing](mobile%20screenshots/Flawless%20syncing%20to%20cloud.gif)

![Mobile volume controller animations](mobile%20screenshots/Mobile%20volume%20controller%20animations.gif)

## Velvet Cloud

Velvet Cloud is a hosted copy of the shared library on a public GitHub release, outside this repository:

```
https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer/releases/download/velvet-cloud/manifest.json
```

- **31 tracks**, hardcoded into both apps. Nothing to configure.
- Each track publishes its ID3 title, artist, and album, plus a matching `.lrc` sidecar when a real sync exists.
- On launch, both apps notice which cloud tracks are missing from your folder and offer to download them.
- Android downloads from Velvet Cloud. There is no upload button on mobile, so the cloud cannot be filled by strangers.
- Desktop downloads and publishes. Publishing uses your local GitHub credential through Git Credential Manager; `tools/publish-cloud.mjs` is the command-line path used to seed the release.
- A partial or failed download is deleted, never left behind as a broken track.
- **The release stays public.** Private release assets require authentication, and both apps download anonymously.

### Backup mirrors

Every track can carry a `mirrors` array. Both apps try the primary URL, then each backup in order, and report failure only when every source fails. If the release manifest itself disappears, clients fall back to the committed manifest in the repository and its CDN copy.

Mirrors are backend-only. Neither app shows, names, or links a backup host. To add an S3-compatible store, set `VELVET_MIRRORS` before running the publisher:

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

The publisher signs each upload with AWS Signature V4, skips files already present at the right size, and writes public URLs into the manifest. Credentials stay in the environment and never enter the repository, the manifest, or either app.

**No host can guarantee that media will never be removed.** Backups reduce the chance that one takedown breaks every client; keep your own copy of the library.

## Lyrics

Lyrics are resolved in this order:

1. The sidecar bundled inside the app for a Velvet Cloud track.
2. A `.lrc` file beside the audio (`Song.mp3` → `Song.lrc`).
3. The lyric sidecar published with that track in Velvet Cloud.
4. A runtime lookup against a public synced-lyrics catalogue.

Every candidate is checked before it is used. Titles and artists are normalised — brackets, `feat.`, remaster and radio-edit suffixes, and punctuation are stripped — then scored. The duration must be close, the result must be genuinely synced, and the last line must fit inside the audio. That stops a cover, a karaoke version, a 34-second clip, or a shortened edit from wearing another recording's timing. If nothing scores high enough, Velvet says there are no lyrics instead of showing the wrong ones. **Search again** drops a bad cache entry and retries.

Timed `[mm:ss.xx]` lines follow playback on a frame clock, honour `[offset:±ms]`, re-anchor immediately on a seek or skip, and are cached locally after the first lookup. `tools/fetch-lyrics.mjs` fills in sidecars for a local library; `tools/publish-cloud.mjs` uploads them beside the audio.

## Volume controller

**Windows.** While Velvet runs — including from the tray at boot — the volume keys are captured system-wide. Velvet steps the Windows media volume through Core Audio and draws its own compact glass HUD at the bottom of the screen. The stock Windows flyout is suppressed. Mute works too. Quit Velvet and the keys return to Windows.

**Android.** Android hides accessibility access for sideloaded apps until you allow it:

1. Open **Settings → Apps → Velvet**.
2. Tap the **three-dot menu** and choose **Restricted access** (or **Allow restricted settings**).
3. Return to Velvet, open its **three-dot menu**, and choose **Volume keys takeover**.

The Velvet accessibility service then opens the full volume pill anywhere: lock screen, inside games, or with nothing playing. The near-opaque floating orb panel stays readable, with liquid, bubbles, and a live level readout easing into every change. Drag anywhere on the rail to choose any level; Velvet keeps the continuous value and commits the closest step the device exposes.

Tap **MIX** for Media, Ring, Alerts, Alarm, Calls, and the Velvet channel, and set an exact percentage for any of them. Android exposes one shared media stream to every app, so the mixer controls device streams while the **Velvet channel** is separate — it pushes Velvet from 0% to **150%**, so it sits above a game or video. The service only filters volume keys and never reads screen content.

## The floating island

- Drag it anywhere; it snaps to the top edge and to the horizontal centre.
- Double-click to switch between the full island and the one-line minimal island.
- The minimal island shows cover, title, time, previous, play/pause, and next.
- Drag its right edge to resize it horizontally.
- While dragging, a glowing centre line appears when the minimal island is snapped to the top middle, then fades on release.
- Resize, rotation, opacity, and minimal state are remembered between launches.
- It follows the same playback clock as the tray media session, so both read the same time.

## Everything in Velvet

### Library

- Indexes MP3, M4A/AAC, FLAC, WAV, OGG, Opus, and WMA, with tags and embedded artwork.
- Folder picker and drag-and-drop selection, remembered between launches.
- Automatic rescan when the folder changes, plus a manual **Rescan** on Windows.
- Scan screen with the current file and overall progress.
- Search across title, artist, and album.
- Sort by title, artist, album, or duration.
- Favourites, with a dedicated filter.
- Album view with a full track list.
- Daily Velvet Mix that reshuffles every day.
- Track durations, album names, and clean fallbacks for missing tags.
- Velvet Cloud catalogue tags applied to downloaded files that have weak or missing metadata.

### Playback

- Background playback that survives the window closing on Windows and the app leaving the foreground on Android.
- Real media sessions: SMTC and media keys on Windows; Media3 session and lock-screen controls on Android.
- Play/pause, previous, next, seek, shuffle, and queue position.
- Playback continues from the tray on Windows; the tray icon opens the app.
- Velvet registers at boot but does not force its window open.
- Android audio focus, so Velvet pauses for calls and other players.
- No ads, no tracking, no account.

### Visuals

- 48-band playback analysis drives the desktop lines, mobile visualizer, disco stage, and vein beat response.
- Desktop spectrum visualizer with a smoothed low-end response.
- Desktop disco: sourced spotlights and corner washes, a liquid floor, kick-locked fireworks, embers, shockwaves, a passing ship, and an animated lyric stage.
- Mobile disco: the same sound-driven stage, plus a spectrum ring and an animated lyric strip, all drawn at 60 FPS.
- Each disco run picks its own palette, burst order, and ship path at runtime.
- Per-track procedural cover art for tracks without embedded artwork, generated deterministically from the track.
- Animated glow sliders, liquid transitions, framed artwork, and four accent themes: Amethyst, Gold, Crimson, and Azure.

Velvet is polished, clean, and cool without disco mode. Disco is still included — it is not perfect, but it is here just because.

### Velvet Cloud

- Hosted shared library of 31 tracks, hardcoded into both apps; nothing to configure.
- Missing-track detection with a one-tap download offer on launch.
- Audio and matching `.lrc` sidecars download together.
- Download progress, cancellation, and automatic cleanup of partial files.
- Backend-only mirror failover, plus manifest fallbacks through the repository and its CDN copy.
- Publishing from desktop through GitHub Credential Manager.

### Volume

- System-wide volume key takeover on Windows with a custom glass HUD.
- Full-screen volume pill on Android through the optional accessibility service.
- Continuous drag anywhere on the rail, not just the device's discrete steps.
- Near-opaque, readable panel with liquid, bubbles, and a live level readout.
- Per-stream mixer for media, ring, alerts, alarm, and calls.
- Separate Velvet channel from 0% to 150%.
- In-app volume HUD when the accessibility service is off.
- The Windows hook is active only while Velvet runs.

### Desktop shell

- Floating dynamic island with snap-to-top, snap-to-centre, and a blue snap line.
- Full and minimal island layouts, double-click switching, and horizontal resize.
- Remembered island geometry, rotation, opacity, and mode.
- Portable custom window chrome: rounded corners, custom title bar, per-monitor DPI awareness, and work-area clamping.
- Tray-first design with a white tray icon and a clean session shutdown.
- Velvet Cloud download and publish from the app.
- Local settings, library cache, generated covers, and logs in `%LOCALAPPDATA%\Velvet`.

### Android shell

- Jetpack Compose UI with a bottom mini player and a full player screen.
- First-run onboarding for folder selection.
- Favourites, albums, search, sorting, and the daily mix.
- DISCO mode and LYRICS mode with animated synced lines.
- Framed media notification with artwork, artist ticker, queue position, and no auto-dismiss while playing.
- Lock-screen artwork and controls.
- Procedural covers, accent theming, and smooth transitions.
- Optional volume-key takeover with a per-stream mixer.

## First run

1. Start Velvet and choose the folder that holds your music.
2. Velvet scans it and remembers the folder permission.
3. If Velvet Cloud has tracks your folder does not, a banner offers to download them.
4. On Windows, close the window when you are done — playback continues from the tray.

Settings, the library cache, generated covers, and logs live in `%LOCALAPPDATA%\Velvet` on Windows.

## Repository layout

```
app/                        Android app (Kotlin, Jetpack Compose, Media3)
desktop/Velvet.Desktop/     Windows app (C#, WPF, NAudio, TagLibSharp)
desktop screenshots/        Windows screenshots used above
mobile screenshots/         Android screenshots and GIFs used above
tools/                      Cloud publishing, lyric fetching, and screenshot helpers
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

`tools/fetch-lyrics.mjs` fills in `.lrc` sidecars from track tags before publishing. `tools/publish-cloud.mjs` reconciles the `velvet-cloud` release with your local library: it uploads new tracks and their lyrics, replaces changed ones, removes tracks you deleted, rebuilds `manifest.json`, and republishes it. It reads a GitHub token from Git Credential Manager; no token is stored in this repository.

```powershell
node tools/fetch-lyrics.mjs "C:\Users\You\Velvet Cloud"
node tools/publish-cloud.mjs
```

## Notes

- Velvet never modifies your audio files. Cloud downloads write a matching `.lrc` sidecar, and generated artwork and lyric caches live in Velvet's own app-data folder.
- Tray pinning uses the same per-user registry setting Windows writes when you drag an icon out of the overflow flyout.
- The Windows volume hook is active only while Velvet runs. Quitting Velvet returns volume keys to Windows.
- Android's accessibility service is optional. Without it, the in-app volume HUD still works while Velvet is open.
- Backup hostnames are configuration only. No app screen names or links a mirror.

## License

[Velvet Community License 1.0](LICENSE) © 2026 Umar.

Velvet is free to use, study, modify, and share. You may not add ads, lock features, or put Velvet or any derivative behind a paywall. Anyone who wants to monetize Velvet must get written permission from the copyright holder and compensate them first. Velvet itself stays free forever.
