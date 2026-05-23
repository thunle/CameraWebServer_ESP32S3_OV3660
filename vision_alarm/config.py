from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any


DEFAULT_CONFIG_PATH = Path(__file__).with_name("config.json")
EXAMPLE_CONFIG_PATH = Path(__file__).with_name("config.example.json")


@dataclass
class Resolution:
    width: int
    height: int

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "Resolution":
        return cls(width=int(data["width"]), height=int(data["height"]))


@dataclass
class ZoneDefinition:
    id: str
    name: str
    points: list[list[int]]

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ZoneDefinition":
        points = [[int(point[0]), int(point[1])] for point in data["points"]]
        return cls(id=str(data["id"]), name=str(data["name"]), points=points)


@dataclass
class AlarmSettings:
    pulse_ms: int = 1500
    cooldown_seconds: float = 5.0
    local_terminal_bell: bool = False
    image_dir: str = "vision_alarm/alarm_images"
    trigger_without_zones: bool = False

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AlarmSettings":
        return cls(
            pulse_ms=int(data.get("pulse_ms", 1500)),
            cooldown_seconds=float(data.get("cooldown_seconds", 5.0)),
            local_terminal_bell=bool(data.get("local_terminal_bell", False)),
            image_dir=str(data.get("image_dir", "vision_alarm/alarm_images")),
            trigger_without_zones=bool(data.get("trigger_without_zones", False)),
        )


@dataclass
class ByteTrackSettings:
    track_activation_threshold: float = 0.35
    lost_track_buffer: int = 30
    minimum_matching_threshold: float = 0.8
    frame_rate: int = 15

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ByteTrackSettings":
        return cls(
            track_activation_threshold=float(
                data.get("track_activation_threshold", 0.35)
            ),
            lost_track_buffer=int(data.get("lost_track_buffer", 30)),
            minimum_matching_threshold=float(
                data.get("minimum_matching_threshold", 0.8)
            ),
            frame_rate=int(data.get("frame_rate", 15)),
        )


@dataclass
class SensorFusionSettings:
    enabled: bool = True
    poll_interval_ms: int = 200
    radar_hold_ms: int = 2500
    require_daylight_for_vision: bool = True
    dark_alarm_on_radar: bool = False
    inactive_reset_seconds: float = 2.0
    fail_open_on_sensor_error: bool = True

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SensorFusionSettings":
        return cls(
            enabled=bool(data.get("enabled", True)),
            poll_interval_ms=int(data.get("poll_interval_ms", 200)),
            radar_hold_ms=int(data.get("radar_hold_ms", 2500)),
            require_daylight_for_vision=bool(
                data.get("require_daylight_for_vision", True)
            ),
            dark_alarm_on_radar=bool(data.get("dark_alarm_on_radar", False)),
            inactive_reset_seconds=float(data.get("inactive_reset_seconds", 2.0)),
            fail_open_on_sensor_error=bool(
                data.get("fail_open_on_sensor_error", True)
            ),
        )


@dataclass
class MonitorApiSettings:
    enabled: bool = True
    host: str = "0.0.0.0"
    port: int = 8765

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "MonitorApiSettings":
        return cls(
            enabled=bool(data.get("enabled", True)),
            host=str(data.get("host", "0.0.0.0")),
            port=int(data.get("port", 8765)),
        )


@dataclass
class AppConfig:
    esp32_host: str = "192.168.178.50"
    stream_port: int = 81
    http_port: int = 80
    source_mode: str = "snapshot"
    snapshot_interval_ms: int = 250
    model: str = "yolo26n.pt"
    confidence: float = 0.4
    iou_threshold: float = 0.45
    image_size: int = 416
    device: str = ""
    show_window: bool = True
    window_name: str = "ESP32 Vision Alarm"
    zone_resolution: Resolution | None = None
    zones: list[ZoneDefinition] = field(default_factory=list)
    alarm: AlarmSettings = field(default_factory=AlarmSettings)
    bytetrack: ByteTrackSettings = field(default_factory=ByteTrackSettings)
    sensor_fusion: SensorFusionSettings = field(default_factory=SensorFusionSettings)
    monitor_api: MonitorApiSettings = field(default_factory=MonitorApiSettings)

    @property
    def stream_url(self) -> str:
        return f"http://{self.esp32_host}:{self.stream_port}/stream"

    @property
    def capture_url(self) -> str:
        return f"http://{self.esp32_host}:{self.http_port}/capture"

    @property
    def buzzer_url(self) -> str:
        return f"http://{self.esp32_host}:{self.http_port}/buzzer"

    @property
    def sensors_url(self) -> str:
        return f"http://{self.esp32_host}:{self.http_port}/sensors"

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def save(self, path: str | Path) -> None:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(self.to_dict(), indent=2, sort_keys=True),
            encoding="utf-8",
        )

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AppConfig":
        zone_resolution = data.get("zone_resolution")
        zones = [ZoneDefinition.from_dict(item) for item in data.get("zones", [])]
        return cls(
            esp32_host=str(data.get("esp32_host", "192.168.178.50")),
            stream_port=int(data.get("stream_port", 81)),
            http_port=int(data.get("http_port", 80)),
            source_mode=str(data.get("source_mode", "snapshot")),
            snapshot_interval_ms=int(data.get("snapshot_interval_ms", 250)),
            model=str(data.get("model", "yolo26n.pt")),
            confidence=float(data.get("confidence", 0.4)),
            iou_threshold=float(data.get("iou_threshold", 0.45)),
            image_size=int(data.get("image_size", 416)),
            device=str(data.get("device", "")),
            show_window=bool(data.get("show_window", True)),
            window_name=str(data.get("window_name", "ESP32 Vision Alarm")),
            zone_resolution=Resolution.from_dict(zone_resolution)
            if zone_resolution
            else None,
            zones=zones,
            alarm=AlarmSettings.from_dict(data.get("alarm", {})),
            bytetrack=ByteTrackSettings.from_dict(data.get("bytetrack", {})),
            sensor_fusion=SensorFusionSettings.from_dict(
                data.get("sensor_fusion", {})
            ),
            monitor_api=MonitorApiSettings.from_dict(data.get("monitor_api", {})),
        )

    @classmethod
    def load(cls, path: str | Path) -> "AppConfig":
        source = Path(path)
        if not source.exists():
            return cls()

        return cls.from_dict(json.loads(source.read_text(encoding="utf-8")))
