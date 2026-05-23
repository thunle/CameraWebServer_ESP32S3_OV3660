from __future__ import annotations

import argparse
from pathlib import Path

from .config import DEFAULT_CONFIG_PATH, AppConfig, Resolution
from .esp32 import Esp32CameraClient
from .monitor import VisionMonitor
from .zones import ZoneEditor


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Analyze the ESP32-S3 camera stream with Ultralytics YOLO26, "
            "Supervision and ByteTrack."
        )
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="Path to the JSON config file.",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    draw_parser = subparsers.add_parser(
        "draw-zones",
        help="Capture a frame from the ESP32 and draw No-Go zones.",
    )
    add_common_runtime_args(draw_parser)

    monitor_parser = subparsers.add_parser(
        "monitor",
        help="Start person detection, tracking and zone alarms.",
    )
    add_common_runtime_args(monitor_parser)
    monitor_parser.add_argument(
        "--no-window",
        action="store_true",
        help="Run without the OpenCV preview window.",
    )

    return parser


def add_common_runtime_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--esp32-host", help="ESP32 IP or hostname.")
    parser.add_argument("--stream-port", type=int, help="MJPEG stream port.")
    parser.add_argument("--http-port", type=int, help="HTTP control port.")
    parser.add_argument(
        "--source-mode",
        choices=("snapshot", "stream"),
        help="Use repeated /capture snapshots or the MJPEG /stream.",
    )
    parser.add_argument(
        "--snapshot-interval-ms",
        type=int,
        help="Delay between /capture requests in snapshot mode.",
    )
    parser.add_argument("--model", help="Ultralytics model name or path.")
    parser.add_argument("--confidence", type=float, help="Detection confidence.")
    parser.add_argument("--iou-threshold", type=float, help="NMS IOU threshold.")
    parser.add_argument("--image-size", type=int, help="Inference image size.")
    parser.add_argument("--device", help="Inference device, e.g. cpu or 0.")
    parser.add_argument("--pulse-ms", type=int, help="ESP32 buzzer pulse length.")
    parser.add_argument(
        "--cooldown",
        type=float,
        help="Minimum seconds between repeated alarm pulses.",
    )
    parser.add_argument(
        "--terminal-bell",
        action="store_true",
        help="Also emit a local terminal bell when an alarm fires.",
    )
    parser.add_argument(
        "--alarm-on-any-person",
        action="store_true",
        help="Test mode: trigger an alarm image for any detected person, without No-Go zones.",
    )
    parser.add_argument(
        "--no-sensor-fusion",
        action="store_true",
        help="Analyze continuously without radar/light gating.",
    )
    parser.add_argument(
        "--sensor-poll-ms",
        type=int,
        help="ESP32 /sensors polling interval.",
    )
    parser.add_argument(
        "--radar-hold-ms",
        type=int,
        help="Keep vision active this long after the last radar motion.",
    )
    parser.add_argument(
        "--allow-vision-at-night",
        action="store_true",
        help="Run camera analysis even when the light sensor reports darkness.",
    )
    parser.add_argument(
        "--pc-dark-radar-alarm",
        action="store_true",
        help="Let the PC monitor pulse the buzzer for radar motion in darkness.",
    )


def apply_overrides(config: AppConfig, args: argparse.Namespace) -> AppConfig:
    if args.esp32_host:
        config.esp32_host = args.esp32_host
    if args.stream_port:
        config.stream_port = args.stream_port
    if args.http_port:
        config.http_port = args.http_port
    if args.source_mode:
        config.source_mode = args.source_mode
    if args.snapshot_interval_ms is not None:
        config.snapshot_interval_ms = args.snapshot_interval_ms
    if args.model:
        config.model = args.model
    if args.confidence is not None:
        config.confidence = args.confidence
    if args.iou_threshold is not None:
        config.iou_threshold = args.iou_threshold
    if args.image_size is not None:
        config.image_size = args.image_size
    if args.device is not None:
        config.device = args.device
    if args.pulse_ms is not None:
        config.alarm.pulse_ms = args.pulse_ms
    if args.cooldown is not None:
        config.alarm.cooldown_seconds = args.cooldown
    if getattr(args, "terminal_bell", False):
        config.alarm.local_terminal_bell = True
    if getattr(args, "alarm_on_any_person", False):
        config.alarm.trigger_without_zones = True
    if getattr(args, "no_window", False):
        config.show_window = False
    if getattr(args, "no_sensor_fusion", False):
        config.sensor_fusion.enabled = False
    if getattr(args, "sensor_poll_ms", None) is not None:
        config.sensor_fusion.poll_interval_ms = args.sensor_poll_ms
    if getattr(args, "radar_hold_ms", None) is not None:
        config.sensor_fusion.radar_hold_ms = args.radar_hold_ms
    if getattr(args, "allow_vision_at_night", False):
        config.sensor_fusion.require_daylight_for_vision = False
    if getattr(args, "pc_dark_radar_alarm", False):
        config.sensor_fusion.dark_alarm_on_radar = True
    return config


def run_draw_zones(config: AppConfig, config_path: Path) -> int:
    client = Esp32CameraClient(config)
    frame = client.capture_frame()

    editor = ZoneEditor(
        frame=frame,
        existing_zones=config.zones,
        source_resolution=config.zone_resolution,
    )
    zones = editor.run()
    if zones is None:
        print("[INFO] Zone editing cancelled.")
        return 0

    config.zones = zones
    config.zone_resolution = Resolution(width=frame.shape[1], height=frame.shape[0])
    config.save(config_path)
    print(f"[INFO] Saved {len(zones)} zone(s) to {config_path}.")
    return 0


def run_monitor(config: AppConfig) -> int:
    monitor = VisionMonitor(config)
    monitor.run()
    return 0


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    config_path = Path(args.config)
    config = apply_overrides(AppConfig.load(config_path), args)

    if args.command == "draw-zones":
        return run_draw_zones(config, config_path)
    if args.command == "monitor":
        return run_monitor(config)

    parser.error(f"Unsupported command: {args.command}")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
