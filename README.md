# Velvet — Local Music Streaming

Premium music streaming for Windows and Android, built around the files you already own. Live spectrum, floating island, tray-first playback, and zero network calls.

[![License: MIT](https://img.shields.io/badge/license-MIT-8a2be2.svg)](LICENSE)

![Velvet desktop](docs/velvet-desktop.png)

Velvet is a local-first player with its own visual identity: a dark crimson-and-veins desktop app built with WPF, and a Compose app for Android with a stylized notification, a live spectrum, and a custom volume HUD. Nothing is uploaded, streamed, or phoned home — Velvet reads folders on disk and plays them.

## Highlights

- **Local library** — point Velvet at a folder and it indexes MP3, M4A/AAC, FLAC, WAV, OGG, and Opus files, tags, and embedded artwork.
- **Real spectrum visualizer** — 48-band analysis drives the on-screen bars and the five accent lines on the desktop.
- **Floating island** — a draggable, resizable always-on-top pill with cover art, progress, and transport controls. It snaps to the top edge and screen center, and shrinks to a one-line mode with `0:12/3:36`, previous, play/pause, and next.
- **Tray-first on Windows** — closing the window keeps the music playing; Velvet drops into the notification area and gets pinned next to Wi-Fi, volume, and battery. Left-click the icon to reopen, right-click to quit.
- **Windows integration** — publishes to the Windows media session (SMTC), so media keys and the system flyout drive playback.
- **Portable window chrome** — custom title bar, rounded corners, per-monitor DPI awareness, and work-area clamping, so it behaves the same on scaled, multi-monitor, or stock Windows installs. No Windhawk or other window mods are required.
- **Android extras** — Media3 playback service with a stylized notification, dismissible volume overlay with a per-app mixer, and procedural cover art.

## Download

| Platform | File |
| --- | --- |
| Windows 10/11 (x64) | `Velvet.exe` in the repository root (self-contained) |
| Android 8.0+ | `Velvet-1.4.apk` in the repository root |

The Windows executable looks for a `music` folder next to itself first, so cloning the repo and double-clicking `Velvet.exe` works immediately.

## Repository layout

```
app/                        Android app (Kotlin, Jetpack Compose, Media3)
desktop/Velvet.Desktop/     Windows app (C#, WPF, NAudio, TagLibSharp)
music/                      Tracks that ship with the build
docs/                       Screenshots
tools/                      Helper scripts used for screenshots and icon generation
```

## Build from source

### Windows

Requirements: Windows 10 2004+ or Windows 11, and the [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).

```powershell
dotnet publish desktop/Velvet.Desktop/Velvet.Desktop.csproj -c Release -o publish
```

`publish/` receives a self-contained single-file `Velvet.exe` plus a copy of `music/`. Run the exe and the bundled tracks load automatically; use **Change folder** to point Velvet at your own library.

Running straight from `dotnet build` also works: the app looks for a `music` folder next to the executable, then walks up the repository tree, and finally falls back to your Windows Music library.

Settings, the library cache, generated covers, and logs live in `%LOCALAPPDATA%\Velvet`.

### Android

Requirements: JDK 17 and Android Studio (or the Android SDK command line tools).

```powershell
.\gradlew.bat assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. On first launch, pick the folder that holds your music; Velvet stores a persisted access grant, so later launches rescan it automatically.

## Bundled music

`music/` contains the 31 tracks the app starts with, so a fresh clone has something to play. Those audio files are demo content and are **not** covered by the MIT license — replace them with music you have the rights to distribute, or delete the folder and point Velvet at your own library.

## Notes

- Velvet never modifies your audio files. Artwork it generates is written to `%LOCALAPPDATA%\Velvet\covers`.
- Tray pinning uses the same per-user registry setting Windows itself writes when you drag an icon out of the overflow flyout; it applies after Explorer restarts or the next sign-in.
- No third-party theme or window mod is needed: the app draws its own title bar, corners, and shadows.

## License

[MIT](LICENSE) © 2026 Umar
