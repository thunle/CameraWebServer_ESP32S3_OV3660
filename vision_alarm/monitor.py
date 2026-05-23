from __future__ import annotations

import inspect
import json
import threading
import time
from dataclasses import dataclass
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import cv2
import numpy as np
import supervision as sv
from ultralytics import YOLO

from .config import AppConfig, ByteTrackSettings, ZoneDefinition
from .esp32 import Esp32CameraClient, LatestFrameStream, SensorState
from .zones import color_for_index, draw_polygon_overlay, scale_points


def create_bytetrack(settings: ByteTrackSettings) -> sv.ByteTrack:
    parameters = inspect.signature(sv.ByteTrack).parameters
    kwargs: dict[str, object] = {}

    if "track_activation_threshold" in parameters:
        kwargs["track_activation_threshold"] = settings.track_activation_threshold
    elif "track_thresh" in parameters:
        kwargs["track_thresh"] = settings.track_activation_threshold

    if "lost_track_buffer" in parameters:
        kwargs["lost_track_buffer"] = settings.lost_track_buffer
    elif "track_buffer" in parameters:
        kwargs["track_buffer"] = settings.lost_track_buffer

    if "minimum_matching_threshold" in parameters:
        kwargs["minimum_matching_threshold"] = settings.minimum_matching_threshold
    elif "match_thresh" in parameters:
        kwargs["match_thresh"] = settings.minimum_matching_threshold

    if "frame_rate" in parameters:
        kwargs["frame_rate"] = settings.frame_rate

    return sv.ByteTrack(**kwargs)


def create_polygon_zone(
    polygon: np.ndarray, frame_resolution: tuple[int, int]
) -> sv.PolygonZone:
    parameters = inspect.signature(sv.PolygonZone).parameters
    kwargs: dict[str, object] = {"polygon": polygon}
    if "frame_resolution_wh" in parameters:
        kwargs["frame_resolution_wh"] = frame_resolution
    if "triggering_anchors" in parameters and hasattr(sv, "Position"):
        kwargs["triggering_anchors"] = (sv.Position.BOTTOM_CENTER,)
    return sv.PolygonZone(**kwargs)


def tracker_ids_from_detections(detections: sv.Detections) -> set[int]:
    tracker_ids = getattr(detections, "tracker_id", None)
    if tracker_ids is None:
        return set()

    ids: set[int] = set()
    for track_id in np.asarray(tracker_ids).reshape(-1).tolist():
        if track_id is None:
            continue
        try:
            parsed_id = int(track_id)
        except (TypeError, ValueError):
            continue
        if parsed_id >= 0:
            ids.add(parsed_id)
    return ids


@dataclass
class CachedZone:
    definition: ZoneDefinition
    polygon: np.ndarray
    zone: sv.PolygonZone


@dataclass
class FrameTimings:
    source_ms: float = 0.0
    infer_ms: float = 0.0
    track_ms: float = 0.0
    draw_ms: float = 0.0
    total_ms: float = 0.0


@dataclass
class AlarmEvent:
    id: int
    timestamp: str
    message: str
    zone_name: str
    tracker_ids: list[int]
    image_path: str

    def to_dict(self, include_image_url: bool = True) -> dict[str, object]:
        data: dict[str, object] = {
            "id": self.id,
            "timestamp": self.timestamp,
            "message": self.message,
            "zone_name": self.zone_name,
            "tracker_ids": self.tracker_ids,
            "image_path": self.image_path,
        }
        if include_image_url:
            data["image_url"] = "/api/alarm/latest.jpg"
        return data


class AlarmController:
    def __init__(self, camera: Esp32CameraClient, config: AppConfig) -> None:
        self.camera = camera
        self.config = config
        self.last_trigger_at = 0.0
        self.last_message = ""
        self.last_error = ""
        self._lock = threading.Lock()
        self._next_event_id = 1
        self.events: list[AlarmEvent] = []
        self.image_dir = Path(config.alarm.image_dir)
        self.image_dir.mkdir(parents=True, exist_ok=True)
        self._load_latest_saved_alarm()

    def _load_latest_saved_alarm(self) -> None:
        images = sorted(
            self.image_dir.glob("alarm_*.jpg"),
            key=lambda path: path.stat().st_mtime,
            reverse=True,
        )
        if not images:
            return

        latest = images[0]
        timestamp = datetime.fromtimestamp(latest.stat().st_mtime).isoformat(
            timespec="seconds"
        )
        self.events.append(
            AlarmEvent(
                id=self._next_event_id,
                timestamp=timestamp,
                message="Previously saved alarm image",
                zone_name="saved alarm",
                tracker_ids=[],
                image_path=str(latest),
            )
        )
        self._next_event_id += 1

    def trigger(
        self, zone_name: str, tracker_ids: list[int], alarm_frame: np.ndarray | None = None
    ) -> bool:
        now = time.monotonic()
        if now - self.last_trigger_at < self.config.alarm.cooldown_seconds:
            return False

        self.last_trigger_at = now
        joined_ids = ", ".join(str(track_id) for track_id in tracker_ids)
        self.last_message = f"ALARM: {zone_name} entered by track(s) {joined_ids}"
        self.last_error = ""
        image_path = ""

        print(f"[ALARM] {self.last_message}")
        if self.config.alarm.local_terminal_bell:
            print("\a", end="", flush=True)

        if alarm_frame is not None:
            image_path = self._save_alarm_image(alarm_frame)

        self._record_event(zone_name, tracker_ids, image_path)

        try:
            self.camera.pulse_buzzer(self.config.alarm.pulse_ms)
        except Exception as exc:
            self.last_error = str(exc)
            print(f"[WARN] Could not trigger ESP32 buzzer: {exc}")

        return True

    def _save_alarm_image(self, frame: np.ndarray) -> str:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        path = self.image_dir / f"alarm_{timestamp}.jpg"
        cv2.imwrite(str(path), frame, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
        return str(path)

    def _record_event(
        self, zone_name: str, tracker_ids: list[int], image_path: str
    ) -> None:
        with self._lock:
            event = AlarmEvent(
                id=self._next_event_id,
                timestamp=datetime.now().isoformat(timespec="seconds"),
                message=self.last_message,
                zone_name=zone_name,
                tracker_ids=tracker_ids[:],
                image_path=image_path,
            )
            self._next_event_id += 1
            self.events.append(event)
            self.events = self.events[-50:]

    def latest_event(self) -> AlarmEvent | None:
        with self._lock:
            return self.events[-1] if self.events else None

    def event_count(self) -> int:
        with self._lock:
            return len(self.events)

    def banner_text(self) -> str:
        hold_seconds = max(2.0, self.config.alarm.pulse_ms / 1000.0)
        if time.monotonic() - self.last_trigger_at <= hold_seconds:
            return self.last_message
        return ""


class MonitorApiServer:
    def __init__(self, monitor: "VisionMonitor") -> None:
        self.monitor = monitor
        self.server: ThreadingHTTPServer | None = None
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        config = self.monitor.config.monitor_api
        if not config.enabled:
            return
        handler = self._make_handler()
        self.server = ThreadingHTTPServer((config.host, config.port), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        print(f"[INFO] Monitor API listening on http://{config.host}:{config.port}")

    def stop(self) -> None:
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
            self.server = None
        if self.thread is not None:
            self.thread.join(timeout=2.0)
            self.thread = None

    def _make_handler(self) -> type[BaseHTTPRequestHandler]:
        monitor = self.monitor

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, _format: str, *_args: object) -> None:
                return

            def do_GET(self) -> None:
                parsed = urlparse(self.path)
                if parsed.path == "/api/status":
                    self._send_json(monitor.api_status())
                    return
                if parsed.path == "/api/alarm/latest.jpg":
                    self._send_latest_image()
                    return
                if parsed.path == "/api/system/active":
                    query = parse_qs(parsed.query)
                    value = query.get("value", [""])[0].lower()
                    if value in {"1", "true", "on", "yes"}:
                        monitor.set_active(True)
                    elif value in {"0", "false", "off", "no"}:
                        monitor.set_active(False)
                    self._send_json(monitor.api_status())
                    return
                if parsed.path == "/api/alarm/test":
                    self._send_json(monitor.create_test_alarm())
                    return
                self.send_error(404)

            def _send_json(self, data: dict[str, object]) -> None:
                payload = json.dumps(data).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def _send_latest_image(self) -> None:
                event = monitor.alarm.latest_event()
                if event is None or not event.image_path:
                    self.send_error(404)
                    return
                path = Path(event.image_path)
                if not path.exists():
                    self.send_error(404)
                    return
                payload = path.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

        return Handler


class VisionMonitor:
    def __init__(self, config: AppConfig) -> None:
        self.config = config
        self.camera = Esp32CameraClient(config)
        self.model = YOLO(config.model)
        self.alarm = AlarmController(self.camera, config)
        self.api_server = MonitorApiServer(self)
        self.active = True
        self._state_lock = threading.Lock()
        self._zone_cache_resolution: tuple[int, int] | None = None
        self._zone_cache: list[CachedZone] = []
        self._fps = 0.0
        self._last_frame_at = 0.0
        self._timings = FrameTimings()
        self._last_sensor_poll_at = 0.0
        self._last_sensor_state: SensorState | None = None
        self._sensor_status_text = "sensor fusion: waiting"
        self._last_sensor_warning_at = 0.0
        self._vision_was_allowed = False
        self._vision_inactive_since: float | None = None
        self.reset_runtime_state()

    def _update_timing(self, name: str, value_ms: float) -> None:
        current = getattr(self._timings, name)
        updated = value_ms if current == 0.0 else current * 0.8 + value_ms * 0.2
        setattr(self._timings, name, updated)

    def reset_runtime_state(self) -> None:
        self.tracker = create_bytetrack(self.config.bytetrack)
        self.previous_zone_members = {
            zone.id: set() for zone in self.config.zones
        }
        self._zone_cache_resolution = None
        self._zone_cache = []

    def run(self) -> None:
        if not self.config.zones and not self.config.alarm.trigger_without_zones:
            raise RuntimeError("No zones configured. Run draw-zones first.")

        self.api_server.start()
        try:
            if self.config.source_mode == "snapshot":
                self.run_snapshot_mode()
                return

            self.run_stream_mode()
        finally:
            self.api_server.stop()

    def set_active(self, active: bool) -> None:
        with self._state_lock:
            self.active = active
        print(f"[INFO] Monitor {'active' if active else 'inactive'} via API.")

    def is_active(self) -> bool:
        with self._state_lock:
            return self.active

    def api_status(self) -> dict[str, object]:
        latest = self.alarm.latest_event()
        return {
            "active": self.is_active(),
            "alarm_count": self.alarm.event_count(),
            "last_message": self.alarm.last_message,
            "last_error": self.alarm.last_error,
            "latest_alarm": latest.to_dict() if latest else None,
        }

    def create_test_alarm(self) -> dict[str, object]:
        frame = self.camera.capture_frame()
        annotated = frame.copy()
        cv2.rectangle(annotated, (10, 10), (annotated.shape[1] - 10, 70), (0, 0, 220), -1)
        cv2.putText(
            annotated,
            "TEST ALARM",
            (24, 52),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.1,
            (255, 255, 255),
            3,
            cv2.LINE_AA,
        )
        self.alarm.trigger("App test alarm", [0], annotated)
        return self.api_status()

    def run_stream_mode(self) -> None:
        reader = LatestFrameStream(self.camera)
        last_seq = 0
        last_warning_at = 0.0
        reader_running = False

        print(f"[INFO] Source mode: stream")
        print(f"[INFO] Stream URL: {self.config.stream_url}")
        print(f"[INFO] Model: {self.config.model}")
        if self.config.sensor_fusion.enabled:
            print("[INFO] Sensor fusion enabled: radar gates vision analysis.")
        print("[INFO] Press Q or ESC in the preview window to stop.")

        self.reset_runtime_state()

        try:
            while True:
                if not self.is_active():
                    if reader_running:
                        reader.stop()
                        reader_running = False
                        last_seq = 0
                    time.sleep(0.1)
                    continue

                if not self._vision_should_run():
                    if reader_running:
                        reader.stop()
                        reader_running = False
                        last_seq = 0
                    if self.config.show_window:
                        key = cv2.waitKey(1) & 0xFF
                        if key in (27, ord("q")):
                            break
                    time.sleep(self._sensor_sleep_seconds())
                    continue

                if not reader_running:
                    reader.start()
                    reader_running = True

                last_seq, frame = reader.read_latest(last_seq)
                if frame is None:
                    if reader.last_error and time.monotonic() - last_warning_at > 1.0:
                        print(f"[WARN] {reader.last_error}")
                        last_warning_at = time.monotonic()
                    if self.config.show_window:
                        key = cv2.waitKey(1) & 0xFF
                        if key in (27, ord("q")):
                            break
                    time.sleep(0.005)
                    continue

                self._update_timing("source_ms", reader.source_read_ms)
                annotated = self.process_frame(frame)

                if self.config.show_window:
                    cv2.imshow(self.config.window_name, annotated)
                    key = cv2.waitKey(1) & 0xFF
                    if key in (27, ord("q")):
                        break
        finally:
            reader.stop()
            cv2.destroyAllWindows()

    def run_snapshot_mode(self) -> None:
        interval_seconds = max(0.05, self.config.snapshot_interval_ms / 1000.0)

        print(f"[INFO] Source mode: snapshot")
        print(f"[INFO] Capture URL: {self.config.capture_url}")
        print(f"[INFO] Model: {self.config.model}")
        if self.config.sensor_fusion.enabled:
            print("[INFO] Sensor fusion enabled: radar gates snapshot capture.")
        print("[INFO] Press Q or ESC in the preview window to stop.")

        self.reset_runtime_state()

        try:
            while True:
                if not self.is_active():
                    time.sleep(0.1)
                    continue

                if not self._vision_should_run():
                    if self.config.show_window:
                        key = cv2.waitKey(1) & 0xFF
                        if key in (27, ord("q")):
                            break
                    time.sleep(self._sensor_sleep_seconds())
                    continue

                loop_started = time.monotonic()
                source_started = time.perf_counter()
                try:
                    frame = self.camera.capture_frame()
                except Exception as exc:
                    print(f"[WARN] Snapshot failed: {exc}")
                    time.sleep(1.0)
                    continue
                self._update_timing(
                    "source_ms", (time.perf_counter() - source_started) * 1000.0
                )

                annotated = self.process_frame(frame)

                if self.config.show_window:
                    cv2.imshow(self.config.window_name, annotated)
                    key = cv2.waitKey(1) & 0xFF
                    if key in (27, ord("q")):
                        break

                elapsed = time.monotonic() - loop_started
                remaining = interval_seconds - elapsed
                if remaining > 0:
                    time.sleep(remaining)
        finally:
            cv2.destroyAllWindows()

    def _sensor_sleep_seconds(self) -> float:
        return max(0.05, self.config.sensor_fusion.poll_interval_ms / 1000.0)

    def _sensor_motion_recent(self, state: SensorState) -> bool:
        if state.radar_active:
            return True
        if state.last_motion_ms <= 0:
            return False
        age_ms = state.uptime_ms - state.last_motion_ms
        return 0 <= age_ms <= self.config.sensor_fusion.radar_hold_ms

    def _read_sensor_state(self) -> SensorState | None:
        if not self.config.sensor_fusion.enabled:
            self._sensor_status_text = "sensor fusion: disabled"
            return None

        now = time.monotonic()
        poll_interval = self._sensor_sleep_seconds()
        if (
            self._last_sensor_state is not None
            and now - self._last_sensor_poll_at < poll_interval
        ):
            return self._last_sensor_state

        self._last_sensor_poll_at = now
        try:
            self._last_sensor_state = self.camera.read_sensors()
        except Exception as exc:
            self._sensor_status_text = f"sensor fusion: unavailable ({exc})"
            if now - self._last_sensor_warning_at > 3.0:
                print(f"[WARN] Could not read ESP32 sensors: {exc}")
                self._last_sensor_warning_at = now
            return None

        state = self._last_sensor_state
        motion = self._sensor_motion_recent(state)
        mode = "day" if state.daylight else "dark"
        self._sensor_status_text = (
            f"radar={'on' if motion else 'off'} {mode} "
            f"light={state.light_raw}/{state.light_day_threshold}"
        )
        return state

    def _vision_should_run(self) -> bool:
        if not self.config.sensor_fusion.enabled:
            return True

        state = self._read_sensor_state()
        if state is None:
            return self.config.sensor_fusion.fail_open_on_sensor_error

        motion = self._sensor_motion_recent(state)
        daylight_ok = state.daylight or not self.config.sensor_fusion.require_daylight_for_vision
        allowed = motion and daylight_ok

        if motion and not state.daylight and self.config.sensor_fusion.dark_alarm_on_radar:
            self.alarm.trigger("Dark radar mode", [state.radar_motion_count])

        now = time.monotonic()
        if allowed and not self._vision_was_allowed:
            print(f"[INFO] Radar motion in daylight: starting vision analysis.")
            self.reset_runtime_state()
            self._last_frame_at = 0.0
            self._vision_inactive_since = None
        elif not allowed and self._vision_was_allowed:
            self._vision_inactive_since = now
            print("[INFO] Radar inactive or too dark: pausing vision analysis.")
        elif not allowed and self._vision_inactive_since is not None:
            if now - self._vision_inactive_since >= self.config.sensor_fusion.inactive_reset_seconds:
                self.reset_runtime_state()
                self._vision_inactive_since = None

        self._vision_was_allowed = allowed
        return allowed

    def process_frame(self, frame: np.ndarray) -> np.ndarray:
        total_started = time.perf_counter()
        now = time.monotonic()
        if self._last_frame_at:
            delta = now - self._last_frame_at
            if delta > 0:
                instant_fps = 1.0 / delta
                if self._fps == 0.0:
                    self._fps = instant_fps
                else:
                    self._fps = self._fps * 0.9 + instant_fps * 0.1
        self._last_frame_at = now

        infer_started = time.perf_counter()
        detections = self.detect_people(frame)
        self._update_timing("infer_ms", (time.perf_counter() - infer_started) * 1000.0)

        track_started = time.perf_counter()
        tracked = self.tracker.update_with_detections(detections)
        cached_zones = self.runtime_zones(frame)

        violating_track_ids: set[int] = set()
        alarm_events: list[tuple[str, list[int]]] = []
        if self.config.alarm.trigger_without_zones:
            current_ids = tracker_ids_from_detections(tracked)
            if len(tracked) > 0 and not current_ids:
                current_ids = set(range(1, len(tracked) + 1))
            if len(tracked) > 0:
                violating_track_ids.update(current_ids)
                alarm_events.append(("Any person test", sorted(current_ids)))
        else:
            for cached_zone in cached_zones:
                mask = cached_zone.zone.trigger(tracked)
                inside = tracked[mask]
                current_ids = tracker_ids_from_detections(inside)
                violating_track_ids.update(current_ids)

                previous_ids = self.previous_zone_members.setdefault(
                    cached_zone.definition.id, set()
                )
                entered_ids = sorted(current_ids - previous_ids)
                self.previous_zone_members[cached_zone.definition.id] = current_ids

                if entered_ids:
                    alarm_events.append((cached_zone.definition.name, entered_ids))

        self._update_timing("track_ms", (time.perf_counter() - track_started) * 1000.0)

        draw_started = time.perf_counter()
        annotated = self.annotate_frame(frame, tracked, cached_zones, violating_track_ids)
        self._update_timing("draw_ms", (time.perf_counter() - draw_started) * 1000.0)
        self._update_timing("total_ms", (time.perf_counter() - total_started) * 1000.0)

        for zone_name, entered_ids in alarm_events:
            self.alarm.trigger(zone_name, entered_ids, annotated)

        return annotated

    def detect_people(self, frame: np.ndarray) -> sv.Detections:
        predict_kwargs = {
            "source": frame,
            "conf": self.config.confidence,
            "iou": self.config.iou_threshold,
            "classes": [0],
            "imgsz": self.config.image_size,
            "verbose": False,
        }
        if self.config.device:
            predict_kwargs["device"] = self.config.device

        result = self.model.predict(**predict_kwargs)[0]
        detections = sv.Detections.from_ultralytics(result)
        if len(detections) == 0:
            return detections

        if getattr(detections, "class_id", None) is None:
            return detections

        return detections[detections.class_id == 0]

    def runtime_zones(self, frame: np.ndarray) -> list[CachedZone]:
        resolution = (int(frame.shape[1]), int(frame.shape[0]))
        if self._zone_cache_resolution == resolution:
            return self._zone_cache

        cached: list[CachedZone] = []
        for zone in self.config.zones:
            polygon = scale_points(
                zone.points,
                self.config.zone_resolution,
                resolution[0],
                resolution[1],
            )
            cached.append(
                CachedZone(
                    definition=zone,
                    polygon=polygon,
                    zone=create_polygon_zone(polygon, resolution),
                )
            )

        self._zone_cache_resolution = resolution
        self._zone_cache = cached
        return cached

    def annotate_frame(
        self,
        frame: np.ndarray,
        tracked: sv.Detections,
        cached_zones: list[CachedZone],
        violating_track_ids: set[int],
    ) -> np.ndarray:
        annotated = frame.copy()

        for index, cached_zone in enumerate(cached_zones):
            current_members = self.previous_zone_members.get(
                cached_zone.definition.id, set()
            )
            annotated = draw_polygon_overlay(
                annotated,
                cached_zone.polygon,
                color_for_index(index),
                f"{cached_zone.definition.name}: {len(current_members)}",
                is_alert=bool(current_members),
            )

        tracker_ids = getattr(tracked, "tracker_id", None)
        confidences = getattr(tracked, "confidence", None)

        for index, box in enumerate(tracked.xyxy):
            x1, y1, x2, y2 = [int(value) for value in box]
            tracker_id = None
            if tracker_ids is not None:
                raw_id = tracker_ids[index]
                if raw_id is not None:
                    tracker_id = int(raw_id)

            confidence = 0.0
            if confidences is not None:
                confidence = float(confidences[index])

            is_violating = tracker_id is not None and tracker_id in violating_track_ids
            color = (0, 0, 255) if is_violating else (0, 220, 0)

            cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 2, cv2.LINE_AA)
            label = (
                f"person #{tracker_id} {confidence:.2f}"
                if tracker_id is not None
                else f"person {confidence:.2f}"
            )
            cv2.putText(
                annotated,
                label,
                (x1, max(24, y1 - 10)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                color,
                2,
                cv2.LINE_AA,
            )

        banner = self.alarm.banner_text()
        if banner:
            self._draw_banner(annotated, banner, (0, 0, 180), (255, 255, 255), 8)

        status_top = annotated.shape[0] - 72
        self._draw_banner(
            annotated,
            self._sensor_status_text,
            (20, 20, 20),
            (220, 220, 220),
            max(8, status_top),
        )
        timing_text = (
            f"fps={self._fps:.1f} src={self._timings.source_ms:.0f}ms "
            f"infer={self._timings.infer_ms:.0f}ms total={self._timings.total_ms:.0f}ms"
        )
        self._draw_banner(
            annotated,
            timing_text,
            (20, 20, 20),
            (220, 220, 220),
            max(44, status_top + 36),
        )

        return annotated

    def _draw_banner(
        self,
        frame: np.ndarray,
        text: str,
        background_color: tuple[int, int, int],
        text_color: tuple[int, int, int],
        top: int,
    ) -> None:
        height = 32
        cv2.rectangle(
            frame,
            (8, top),
            (frame.shape[1] - 8, top + height),
            background_color,
            -1,
        )
        cv2.putText(
            frame,
            text,
            (16, top + 22),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            text_color,
            2,
            cv2.LINE_AA,
        )
