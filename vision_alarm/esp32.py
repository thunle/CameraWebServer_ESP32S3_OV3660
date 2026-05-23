from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Any

import cv2
import numpy as np
import requests

from .config import AppConfig


@dataclass(frozen=True)
class SensorState:
    radar_active: bool
    daylight: bool
    light_raw: int
    light_day_threshold: int
    radar_motion_count: int
    dark_alarm_count: int
    last_motion_ms: int
    sensor_age_ms: int
    buzzer_active: bool
    uptime_ms: int

    @property
    def motion_recent(self) -> bool:
        if self.last_motion_ms <= 0:
            return False
        return self.uptime_ms - self.last_motion_ms >= 0

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SensorState":
        return cls(
            radar_active=bool(data.get("radar_active", False)),
            daylight=bool(data.get("daylight", False)),
            light_raw=int(data.get("light_raw", 0)),
            light_day_threshold=int(data.get("light_day_threshold", 0)),
            radar_motion_count=int(data.get("radar_motion_count", 0)),
            dark_alarm_count=int(data.get("dark_alarm_count", 0)),
            last_motion_ms=int(data.get("last_motion_ms", 0)),
            sensor_age_ms=int(data.get("sensor_age_ms", 0)),
            buzzer_active=bool(data.get("buzzer_active", False)),
            uptime_ms=int(data.get("uptime_ms", 0)),
        )


class Esp32CameraClient:
    def __init__(self, config: AppConfig) -> None:
        self.config = config

    def capture_frame(self) -> np.ndarray:
        response = requests.get(self.config.capture_url, timeout=10)
        response.raise_for_status()

        frame = cv2.imdecode(
            np.frombuffer(response.content, dtype=np.uint8), cv2.IMREAD_COLOR
        )
        if frame is None:
            raise RuntimeError(
                f"Could not decode JPEG from {self.config.capture_url}."
            )
        return frame

    def read_sensors(self) -> SensorState:
        response = requests.get(self.config.sensors_url, timeout=2)
        response.raise_for_status()
        return SensorState.from_dict(response.json())

    def open_stream(self) -> Any:
        backends = []
        ffmpeg_backend = getattr(cv2, "CAP_FFMPEG", None)
        if ffmpeg_backend is not None:
            backends.append(ffmpeg_backend)
        backends.append(None)

        for backend in backends:
            if backend is None:
                capture = cv2.VideoCapture(self.config.stream_url)
            else:
                capture = cv2.VideoCapture(self.config.stream_url, backend)

            capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            if capture.isOpened():
                return capture

            capture.release()

        return cv2.VideoCapture(self.config.stream_url)

    def pulse_buzzer(self, duration_ms: int) -> None:
        params = {"state": "on"}
        if duration_ms > 0:
            params["duration_ms"] = int(duration_ms)

        response = requests.get(self.config.buzzer_url, params=params, timeout=2)
        response.raise_for_status()

    def stop_buzzer(self) -> None:
        response = requests.get(
            self.config.buzzer_url, params={"state": "off"}, timeout=2
        )
        response.raise_for_status()


class LatestFrameStream:
    def __init__(self, camera: Esp32CameraClient) -> None:
        self.camera = camera
        self._lock = threading.Lock()
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        self._capture: Any = None
        self._latest_frame: np.ndarray | None = None
        self._latest_seq = 0
        self.last_error = ""
        self.source_read_ms = 0.0

    def start(self) -> None:
        if self._thread is not None:
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self) -> None:
        consecutive_failures = 0

        while not self._stop_event.is_set():
            if self._capture is None or not self._capture.isOpened():
                self._capture = self.camera.open_stream()
                if not self._capture.isOpened():
                    self.last_error = f"Could not open {self.camera.config.stream_url}"
                    time.sleep(1.0)
                    continue
                consecutive_failures = 0

            read_started = time.perf_counter()
            ok, frame = self._capture.read()
            read_ms = (time.perf_counter() - read_started) * 1000.0
            self.source_read_ms = (
                read_ms
                if self.source_read_ms == 0.0
                else self.source_read_ms * 0.8 + read_ms * 0.2
            )
            if not ok or frame is None:
                consecutive_failures += 1
                if consecutive_failures >= 10:
                    self.last_error = "Stream stalled. Reconnecting..."
                    self._capture.release()
                    self._capture = None
                    time.sleep(0.25)
                continue

            consecutive_failures = 0
            self.last_error = ""
            with self._lock:
                self._latest_frame = frame
                self._latest_seq += 1

    def read_latest(self, last_seq: int) -> tuple[int, np.ndarray | None]:
        with self._lock:
            if self._latest_frame is None or self._latest_seq == last_seq:
                return last_seq, None
            return self._latest_seq, self._latest_frame.copy()

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)
            self._thread = None
        if self._capture is not None:
            self._capture.release()
            self._capture = None
        with self._lock:
            self._latest_frame = None
            self._latest_seq = 0
