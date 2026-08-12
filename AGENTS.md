# AGENTS.md

## Non-negotiable: publishing rules

- **NEVER commit, push, deploy, upload to a Release, or touch git remotes
  without the user's explicit permission.**
- Building/installing for testing is fine; publishing is NOT. "Build the
  file" means build and stop.
- The user grants permission only after testing passes, and they run the
  push-merge skill themselves. I do not start it.
- Never install software on the user's PC without asking first.
- **Never overwrite an existing Release's app file (no `--clobber`, no
  "replacing" files in a release).** A changed app is a NEW release with a
  NEW version number (v1.0.0 → v1.0.1 → …). Old releases stay untouched as
  history. Never "update in place" something already published.

## What this is

Promptly — Android companion to the desktop voice-input app. Floating bubble
(white circle + black box) over any app; tap to record, tap again to
transcribe via Groq Whisper; text is copied to the clipboard (Android apps
cannot auto-paste — by design). Quick-settings tiles: record and show/hide.

## Build & run (no Android Studio needed)

- SDK: `%LOCALAPPDATA%\Android\Sdk`; emulator device is `emulator-5554`
  (Pixel 10 Pro, API 37) when running.
- Gradle is not on PATH; use the downloaded dist:
  `$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin" -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName`
- Debug build: `& $gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Install on emulator: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk`
- Release build: `& $gradle assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (signs with `promptly-release.keystore` + `keystore.properties` at repo root —
  never commit either; losing them breaks future updates)
- **Gotcha:** running a CLI build while Android Studio has the project open
  can hang on file locks (and vice versa). If a build stalls, `& $gradle --stop`
  and retry; ask the user to leave the IDE idle.
- No test suite exists; verification is manual on the emulator.

## Architecture

- `MainActivity` — API key, accurate-model switch, start/stop overlay,
  quick-settings instructions. Settings in SharedPreferences `"promptly"`
  (keys: `api_key`, `accurate_model` (default true), `overlay_visible`,
  `recording`).
- `OverlayService` — state machine IDLE → RECORDING → TRANSCRIBING; builds
  the bubble view in code; owns recording + transcription coroutine.
  - Bubble hidden ⇒ `stopSelf()` (app sleeps; zero background). Hidden + tile
    tap re-wakes it.
  - Tap while TRANSCRIBING cancels the in-flight OkHttp call
    (`currentCall.cancel()`).
  - 45 s read timeout; "Still working…" toast at 15 s.
  - Model: `whisper-large-v3` (accurate) or `whisper-large-v3-turbo` — read
    from prefs each transcription.
- `RecordTileService` / `OverlayTileService` — quick-settings tiles that
  `startForegroundService` with an action; state reflected via prefs.
- `Recorder` — MediaRecorder (AAC/m4a) into `cacheDir`; `stop()` throws if
  recording < ~1 s — callers must catch.
- `GroqApi` — returns an unexecuted `okhttp3.Call` (caller executes and maps
  errors); multipart to `api.groq.com/.../audio/transcriptions`.

## Repo notes

- Remote: `https://github.com/AhmedSaeed4/promptly-mobile` (public). `gh`
  CLI lives at `C:\Program Files\GitHub CLI\gh.exe` (not on PATH); git auth
  goes through gh (run `gh auth setup-git` if push asks for a password).
- `overlay-preview.html` and signing files are deliberately gitignored.
- Workflow for changes: feature branch → PR → squash merge (push-merge
  skill) — only ever initiated by the user.
