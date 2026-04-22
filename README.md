# 📺 ScreenCast

Stream your Android screen directly to Restream (or any RTMPS server) — no watermark, no camera needed.

Built with [RootEncoder](https://github.com/pedroSG94/RootEncoder) · Targets Android 14+

---

## Features

- 🖥️ Screen share via MediaProjection (Android 14 single-app or full screen)
- 🔴 RTMPS streaming (works with Restream, YouTube, Facebook, custom servers)
- 🎙️ Mic audio with hardware noise suppression & echo canceler
- 📱 Streams at 720p / 2.5Mbps / 30fps (battery-friendly)
- 🔔 Foreground notification with quick Stop button
- 💾 Saves your URL & stream key between sessions

---

## Requirements

- Android 14+ (API 34)
- Internet connection (upload ~3 Mbps recommended)

---

## How to Use

### Get RTMPS credentials from Restream

1. Go to [restream.io](https://restream.io) → dashboard
2. Click **New Stream** → choose **Encoder / RTMP**
3. Copy the **RTMPS URL** (starts with `rtmps://`)
4. Copy the **Stream Key**

### Stream

1. Open **ScreenCast** app
2. Paste RTMPS URL and Stream Key
3. Tap **GO LIVE**
4. Grant microphone + screen capture permission
5. Choose **full screen** or **single app** (Android 14+)
6. You're live 🔴

---

## Build from Source

```bash
git clone https://github.com/YOUR_USERNAME/ScreenCastApp.git
cd ScreenCastApp
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/
```

---

## GitHub Actions

Every push to `main` automatically builds both Debug and Release APKs.

To download:
1. Go to **Actions** tab on GitHub
2. Click the latest workflow run
3. Scroll to **Artifacts** section
4. Download `ScreenCast-debug-*` or `ScreenCast-release-*`

---

## Project Structure

```
app/src/main/java/com/screencast/app/
├── ui/
│   └── MainActivity.kt          # UI, permissions, state display
└── streaming/
    ├── StreamingManager.kt      # RTMPS core (encode + push)
    ├── ScreenCaptureService.kt  # Foreground service
    └── StreamState.kt           # State enum
```

---

## License

MIT
