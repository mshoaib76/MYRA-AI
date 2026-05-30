# MYRA — Android AI Voice Assistant

Production-ready Android voice assistant using **Kotlin**, **MVVM**, **Gemini Live WebSocket**, and native **PCM audio**.

## Requirements

- Android Studio Hedgehog or newer
- Android device/emulator API 26+ (Android 8.0)
- [Google AI Gemini API key](https://aistudio.google.com/apikey)

## Setup

1. Open this folder in **Android Studio**
2. Copy `local.properties.example` → `local.properties` (SDK path is auto-detected on most machines)
3. Let Gradle sync complete (uses Java 17+ — Android Studio’s bundled JBR works)
4. Run on a physical device (recommended for mic/speaker/calls)
4. Open **Settings** and enter your **Gemini API key** and name
5. Enable **MYRA Accessibility** service for app open/close commands
6. Grant **overlay** permission when prompted (double power-press overlay)

## Architecture

| Layer | Files |
|-------|-------|
| AI | `GeminiLiveClient.kt`, `AudioEngine.kt`, `CommandParser.kt` |
| UI | `MainActivity.kt`, `OrbAnimationView.kt`, `WaveformView.kt` |
| Services | Overlay, Call Monitor, Accessibility |
| Data | `PrefsHelper`, `MainViewModel` |

## Gemini Live WebSocket

- URL: `wss://generativelanguage.googleapis.com/ws/...BidiGenerateContent?key=API_KEY`
- Mic: 16 kHz PCM mono → WebSocket
- Speaker: 24 kHz PCM mono ← WebSocket
- Session renew: 9 min | Keepalive: 8 sec silent PCM

## Voice commands (Roman Urdu / English)

| Bolo | Kaam |
|------|------|
| WhatsApp kholo | App open |
| App band karo / close app | Current app close (Accessibility ON) |
| Back karo | Back button |
| Ali ko whatsapp par message karo salam | WhatsApp chat |
| 03001234567 ko whatsapp message karo hello | Number par WhatsApp |
| Admin 1234 (apna PIN) | Admin session |
| Unlock whatsapp | Vault password (Settings → Admin) |
| Tumhe kis ne banaya? | Creator answer (Muhammad Shoaib) |

## Test Checklist

1. Settings → **TEST: Open WhatsApp** — agar ye chale to voice bhi chalegi
2. **Accessibility** ON zaroori (open/close/back/unlock)
3. "YouTube kholo" / "WhatsApp kholo"
4. Prime contact call / WhatsApp message
5. Incoming call announcement + accept/reject via voice
6. Long-press mic → interrupt speech
7. Double screen off quickly → floating orb overlay

## Build from command line (Windows)

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Release APK (signed)

1. In Android Studio: **Build → Generate Signed Bundle / APK**
2. Choose **APK**, create or select a keystore
3. Build type: **release** (ProGuard enabled in `app/build.gradle`)
4. Install: `adb install app-release.apk`

Or via CLI after creating `keystore.properties` and signing config in `app/build.gradle`.

## Package

`com.myra.assistant` — minSdk 26, targetSdk 34
