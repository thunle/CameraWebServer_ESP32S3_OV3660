from __future__ import annotations

import threading
import time
from typing import Any

import cv2
import numpy as np
import requests

from .config import AppConfig


class Esp32KameraClient:
    def __init__(self, config: AppConfig) -> None:
        self.config = config

    def frame_aufnehmen(self) -> np.ndarray:
        antwort = requests.get(self.config.capture_url, timeout=10)
        antwort.raise_for_status()

        frame = cv2.imdecode(
            np.frombuffer(antwort.content, dtype=np.uint8), cv2.IMREAD_COLOR
        )
        if frame is None:
            raise RuntimeError(
                f"JPEG von {self.config.capture_url} konnte nicht dekodiert werden."
            )
        return frame

    def stream_oeffnen(self) -> Any:
        backends = []
        ffmpeg_backend = getattr(cv2, "CAP_FFMPEG", None)
        if ffmpeg_backend is not None:
            backends.append(ffmpeg_backend)
        backends.append(None)

        for backend in backends:
            if backend is None:
                video_capture = cv2.VideoCapture(self.config.stream_url)
            else:
                video_capture = cv2.VideoCapture(self.config.stream_url, backend)

            video_capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            if video_capture.isOpened():
                return video_capture

            video_capture.release()

        return cv2.VideoCapture(self.config.stream_url)

    def buzzer_impuls(self, dauer_ms: int) -> None:
        parameter = {"state": "on"}
        if dauer_ms > 0:
            parameter["duration_ms"] = int(dauer_ms)

        antwort = requests.get(self.config.buzzer_url, params=parameter, timeout=2)
        antwort.raise_for_status()

    def buzzer_stoppen(self) -> None:
        antwort = requests.get(
            self.config.buzzer_url, params={"state": "off"}, timeout=2
        )
        antwort.raise_for_status()


class NeuestesFrameStream:
    def __init__(self, kamera_client: Esp32KameraClient) -> None:
        self.kamera_client = kamera_client
        self._sperre = threading.Lock()
        self._stop_ereignis = threading.Event()
        self._thread: threading.Thread | None = None
        self._capture: Any = None
        self._neuestes_frame: np.ndarray | None = None
        self._neueste_sequenz = 0
        self.letzter_fehler = ""
        self.quell_lese_ms = 0.0

    def start(self) -> None:
        if self._thread is not None:
            return
        self._thread = threading.Thread(target=self._lauf, daemon=True)
        self._thread.start()

    def _lauf(self) -> None:
        aufeinanderfolgende_fehler = 0

        while not self._stop_ereignis.is_set():
            if self._capture is None or not self._capture.isOpened():
                self._capture = self.kamera_client.stream_oeffnen()
                if not self._capture.isOpened():
                    self.letzter_fehler = (
                        f"{self.kamera_client.config.stream_url} konnte nicht geoffnet werden"
                    )
                    time.sleep(1.0)
                    continue
                aufeinanderfolgende_fehler = 0

            lese_start = time.perf_counter()
            erfolgreich, frame = self._capture.read()
            lese_ms = (time.perf_counter() - lese_start) * 1000.0
            self.quell_lese_ms = (
                lese_ms
                if self.quell_lese_ms == 0.0
                else self.quell_lese_ms * 0.8 + lese_ms * 0.2
            )

            if not erfolgreich or frame is None:
                aufeinanderfolgende_fehler += 1
                if aufeinanderfolgende_fehler >= 10:
                    self.letzter_fehler = "Stream hangt. Verbindung wird neu aufgebaut..."
                    self._capture.release()
                    self._capture = None
                    time.sleep(0.25)
                continue

            aufeinanderfolgende_fehler = 0
            self.letzter_fehler = ""
            with self._sperre:
                self._neuestes_frame = frame
                self._neueste_sequenz += 1

    def neuestes_lesen(
        self, letzte_sequenz: int
    ) -> tuple[int, np.ndarray | None]:
        with self._sperre:
            if (
                self._neuestes_frame is None
                or self._neueste_sequenz == letzte_sequenz
            ):
                return letzte_sequenz, None
            return self._neueste_sequenz, self._neuestes_frame.copy()

    def stoppen(self) -> None:
        self._stop_ereignis.set()
        if self._thread is not None:
            self._thread.join(timeout=2.0)
        if self._capture is not None:
            self._capture.release()


Esp32CameraClient = Esp32KameraClient
LatestFrameStream = NeuestesFrameStream
