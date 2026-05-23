# Vision Alarm Android App

Native Android app for the ESP32-S3 vision alarm project.

## Features

- Saves ESP32 host/IP, HTTP port and MJPEG stream port
- Polls `GET /sensors` every second
- Polls `GET /status` every four seconds
- Shows radar, light, day/night mode, buzzer and alarm counters
- Loads a still image from `GET /capture`
- Displays the MJPEG stream from `GET :81/stream`
- Sends buzzer commands through `GET /buzzer`
- Reads ESP32 remote logs from `GET /logs`
- Talks to the laptop monitor API at `GET /api/status`
- Activates/deactivates the vision monitor through `GET /api/system/active`
- Shows the latest saved alarm image from `GET /api/alarm/latest.jpg`

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\Julius\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' --no-daemon :app:assembleDebug
```

The debug APK is written to:

```text
android_app/app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Flash and start the ESP32 firmware from the repository.
2. Keep the phone on the same Wi-Fi as the ESP32.
3. Install `app-debug.apk`.
4. Open the app and enter the ESP32 IP address.
5. Start the PC monitor with `python -m vision_alarm monitor`.
6. In the emulator use `10.0.2.2` as the laptop monitor host.
7. On a real phone use the laptop's LAN IP as the monitor host.
8. Tap `Speichern`, then use dashboard, alarm app, capture, stream, buzzer and logs.

The app uses cleartext HTTP because the ESP32 firmware serves local HTTP only.
