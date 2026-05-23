# ESP32 Vision Alarm

This companion app runs on your PC, not on the ESP32 itself.

It does five things:

1. Polls `/sensors` for radar motion and light level
2. Reads repeated snapshots from `/capture` by default, or the MJPEG stream from `/stream`
3. Detects people with Ultralytics YOLO26
4. Tracks them with `supervision.ByteTrack`
5. Pulses the ESP32 buzzer when a tracked person enters a saved No-Go zone

## Requirements

- Python 3.11 or newer on the PC
- The ESP32 firmware from this repository flashed and connected to Wi-Fi
- A reachable camera endpoint at `http://<esp32-ip>/capture`

## Install

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements-vision.txt
```

## Draw No-Go zones

The zone editor grabs one still image from `http://<esp32-ip>/capture` and opens
an OpenCV window.

```powershell
python -m vision_alarm draw-zones --esp32-host 192.168.178.50
```

Controls:

- Left click: add a polygon point
- Right click or `Enter`: finish the current zone
- `U`: undo
- `R`: clear all zones
- `S`: save to `vision_alarm/config.json`
- `Q` or `Esc`: quit without saving

## Start monitoring

```powershell
python -m vision_alarm monitor --esp32-host 192.168.178.50
```

Useful options:

```powershell
python -m vision_alarm monitor --esp32-host 192.168.178.50 --source-mode snapshot --image-size 320 --pulse-ms 2000
```

Sensor fusion is enabled by default. Radar motion starts the vision analysis,
daylight allows YOLO/ByteTrack processing, and radar motion in darkness is handled
by the ESP32 firmware as the simplified night alarm. For troubleshooting you can
disable the gate:

```powershell
python -m vision_alarm monitor --esp32-host 192.168.178.50 --no-sensor-fusion
```

## Notes

- The default model is `yolo26n.pt`. Change it with `--model` or in `vision_alarm/config.json`.
- The default source mode is `snapshot` because it is usually smoother on ESP32 cameras than MJPEG streaming.
- If your GPU is not used yet, start with `--image-size 320` or `--image-size 416` for lower latency.
- Zone coordinates are stored in image pixels. The app rescales them if the runtime resolution changes.
- The ESP32 buzzer endpoint now supports `duration_ms`, for example:

```text
http://<esp32-ip>/buzzer?state=on&duration_ms=1500
```

- The ESP32 exposes the current radar/light state as JSON:

```text
http://<esp32-ip>/sensors
```

- If your Ultralytics install does not know YOLO26 yet, update `ultralytics` first.
