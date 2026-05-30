# MYRA — Android AI Voice Assistant

Production-ready Android voice assistant using **Kotlin**, **MVVM**, **Gemini Live WebSocket**, and native **PCM audio**.


## What the app is about

MYRA is an AI-powered Android application developed to provide users with a smart and interactive digital assistant experience. The app combines modern mobile technology with artificial intelligence to make communication and task handling easier. It offers a secure and user-friendly platform with a clean interface and responsive design. The application is designed to improve productivity and provide intelligent assistance directly on mobile devices.

## Who can use this app

This application can be used by students, professionals, business users, and general smartphone users. Anyone who needs quick assistance, smart communication, or AI-based features can benefit from the app. It is especially useful for people who want a simple and efficient digital assistant on Android devices. The app is designed for users of all experience levels with an easy-to-use interface.

## What problem it solves

MYRA helps solve the problem of managing communication and daily digital tasks efficiently. Many users struggle with slow responses, complicated applications, or insecure platforms, so this app provides a faster and smarter solution. It improves user interaction by using AI-powered features and secure authentication systems. The app also reduces manual effort by offering intelligent and automated assistance.

## App Screenshots

Add application screenshots:
![Home Screen](screenshots/splash_screen.jpeg)
![Splash Screen](screenshots/dashboardscreen.jpeg)
![Login Screen](screenshots/Login_screen.jpeg)
![Signup Screen](screenshots/signup_screen.jpeg)
![History Screen](screenshots/historyscreen.jpeg)

## Features

* User Signup
* User Login
* Secure Authentication
* Dashboard Screen
* AI Assistant Integration
* Firebase Integration
* Real-Time Database
* Responsive Android Design
* User-Friendly Interface
* Accessibility Support
* Modern UI/UX Design
* Fast Navigation System
* Cloud Data Management
* Profile Management
* Secure User Data
* Interactive User Experience
* Mobile Optimization
* Intelligent Response System

## APK Download 
downlaod the apk file [here](https://github.com/mshoaib76/MYRA-AI/tree/main/apk)


## How to Install the APK 
1. Download the APK file. 
2. Open the APK file on an Android mobile phone. 
3. Allow installation from unknown sources if required. 
4. Install and run the application.
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
## How to Run the Project 
1. Clone or download this project. 
2. Open the project in Android Studio. 
3. Sync Gradle files. 
4. Connect Firebase if required. 
5. Run the app on an emulator or Android device.

## Future Enhancements 
* AI-Based Smart Recommendations
* Dark Mode and Theme Customization
* Real-Time Notifications
* Cloud Backup and Sync
* Advanced Security Features
* Face Recognition Login
* Offline Mode Support
* Cross-Platform Compatibility
* AI-Powered Task Automation
* Data Analytics Dashboard
* Enhanced User Personalization
* Push Notification System
* Advanced Firebase Features


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

## Developed By 
Muhammad Shoaib 

Muzammil Nazeer

semester 6th

Department  Computer Science

Github Profile Link: https://github.com/mshoaib76/
