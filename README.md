# Promptly — Android

The phone version of the Promptly desktop app. A small floating button (white
circle with a black box) sits on top of whatever you are doing. Tap it to
speak, tap it again to stop — your words are transcribed and copied to the
clipboard so you can paste them anywhere.

## How it works

1. Tap the floating button (or the panel button) → the black box turns **red**
   and recording starts
2. Tap it again → the box turns **blue** while your speech is turned into text
3. When the box turns back to **black**, the text is already copied — just tap
   and paste it where you want it

## Features

- **Floating button** — a white circle with a black box, draggable anywhere
  on the screen. Red while recording, blue while working.
- **Quick settings panel buttons** — add "Promptly record" (start/stop
  recording without the bubble) and "Promptly button" (show/hide the bubble)
  to the panel you pull down from the top of the screen.
- **Battery friendly** — the app is fully asleep when the bubble is hidden.
  It wakes up only for the moments you record and transcribe, then sleeps
  again by itself.
- **Accurate transcription by default** — uses Groq's `whisper-large-v3`
  (accurate) model. Flip the switch in the app for the faster
  `whisper-large-v3-turbo` model if you prefer speed.
- **AI text polish** — before the text is copied, a fast Groq AI model cleans
  it up: grammar, punctuation, filler words ("um", "I mean"), and broken
  sentences — without changing the meaning or language. On by default; flip
  the switch in the app for raw transcription. If the AI is unreachable, the
  raw text is copied instead.
- **My words — a personal word list** — add tricky terms (names, brands,
  jargon) in the app, one per line, and the app learns them: mishearings like
  "cloud code" get corrected to "Claude Code". Safe by design — genuine uses
  of similar-sounding words ("save the backup to the cloud") are never
  replaced.
- **Clipboard paste** — the transcribed text is copied to the clipboard, so
  you paste it wherever you like (phones do not allow apps to type into other
  apps automatically).
- **Works from anywhere** — recording works even when the bubble is hidden,
  via the panel button.

## Requirements

- Android 8.0 (2017) or newer
- A free Groq API key from [console.groq.com](https://console.groq.com)
- Internet connection (transcription happens in the cloud)

## Installation

Download the latest `app-release.apk` from the
[Releases](https://github.com/AhmedSaeed4/promptly-mobile/releases) page,
open it on your phone and press **Install**. The phone will ask once to allow
installing apps from this source — that is normal for any app that is not on
the Google Play Store.

### First run

1. Open Promptly and paste your Groq API key
2. Press **"Show floating button"** and allow the permissions the phone asks
   for (microphone, notifications, display over other apps)
3. Tap **"Add buttons to the notification panel"** — or swipe down twice from
   the top, press the pencil icon, and drag the two Promptly buttons in

## Building from source

Requires Android Studio (or the Android SDK) and Java 17+.

```bash
# Debug build (for testing)
gradle assembleDebug

# Release build (signed with your keystore)
gradle assembleRelease
```

Outputs:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Signing (release builds)

The release build is signed with a keystore. The signing details live in
`keystore.properties` and the keystore itself is `promptly-release.keystore`
— **keep both files safe and never commit them**. They are the app's identity:
without them, future versions cannot be installed over older ones. Both files
are excluded via `.gitignore`.

## Permissions

| Permission | Why |
|---|---|
| Microphone | To hear your voice |
| Display over other apps | To float the button above other apps |
| Notifications | To keep the button working in the background and show status |

## Project structure

```
promptly-mobile/
├── app/
│   ├── build.gradle.kts       # App module config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/promptly/mobile/
│       │   ├── MainActivity.kt        # App screen — API key, settings, start/stop
│       │   ├── OverlayService.kt      # Floating bubble + recording + transcribing
│       │   ├── RecordTileService.kt   # Quick settings "record" button
│       │   ├── OverlayTileService.kt  # Quick settings "show/hide" button
│       │   ├── Recorder.kt            # Microphone recording (m4a)
│       │   └── GroqApi.kt             # Groq Whisper transcription
│       └── res/                       # Layouts, icons, strings
├── overlay-preview.html               # Design preview of the floating button
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── README.md
```

## Privacy

Your audio is sent only to Groq for transcription. Nothing else is uploaded
or stored. The app keeps no history of your recordings or transcripts.
