"""Measure ESP32 camera MJPEG profiles and recommend the highest stable one.

Example:
    python stream_benchmark.py --host 192.168.178.53 --duration 60

The script changes only the temporary sensor frame size and JPEG quality.  It
does not write any persistent device setting.  Run it from a computer on the
same 2.4 GHz network as the camera, with no other /stream client connected.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
import time
from dataclasses import asdict, dataclass
from typing import Iterable

import requests


@dataclass(frozen=True)
class Profile:
    name: str
    framesize: int
    quality: int
    width: int
    height: int


@dataclass
class Result:
    profile: str
    width: int
    height: int
    quality: int
    frames: int
    elapsed_seconds: float
    fps: float
    mean_bytes: float
    p95_gap_ms: float | None
    max_gap_ms: float | None
    stalls: int
    reconnects: int
    rssi_before: int | None
    rssi_after: int | None
    stable: bool
    error: str | None = None


# ESP32 Arduino core 3.3.7 framesize_t values. These are the values expected
# by /control?var=framesize&val=..., not pixel dimensions. Core 3.x added
# 128x128 and 320x320 entries, so the older commonly copied IDs are wrong.
PROFILES = {
    "qvga": Profile("QVGA", 6, 18, 320, 240),
    "vga": Profile("VGA", 10, 14, 640, 480),
    "svga": Profile("SVGA", 11, 12, 800, 600),
}


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * percent / 100.0) - 1)
    return ordered[index]


def get_status(session: requests.Session, base_url: str) -> dict:
    response = session.get(f"{base_url}/status", timeout=(3, 5))
    response.raise_for_status()
    return response.json()


def reset_camera(session: requests.Session, base_url: str) -> None:
    response = session.get(f"{base_url}/camera_reset", timeout=(3, 20))
    response.raise_for_status()
    payload = response.json()
    if not payload.get("camera_reset"):
        raise requests.RequestException(f"camera reset was rejected: {payload}")
    # /camera_reset also recreates the stream server. Avoid racing its socket.
    time.sleep(2.0)


def set_profile(session: requests.Session, base_url: str, profile: Profile) -> None:
    set_sensor_values(
        session,
        base_url,
        (("framesize", profile.framesize), ("quality", profile.quality)),
    )
    # The sensor applies register changes asynchronously. Let the old camera
    # buffers drain before opening the next one-client MJPEG session.
    time.sleep(1.0)


def set_sensor_values(
    session: requests.Session, base_url: str, values: Iterable[tuple[str, int]]
) -> None:
    for variable, value in values:
        response = session.get(
            f"{base_url}/control",
            params={"var": variable, "val": value},
            timeout=(3, 5),
        )
        response.raise_for_status()


def jpeg_frames(chunks: Iterable[bytes]) -> Iterable[bytes]:
    """Extract JPEG images from multipart MJPEG without trusting boundary text."""
    buffer = bytearray()
    for chunk in chunks:
        if not chunk:
            continue
        buffer.extend(chunk)
        while True:
            start = buffer.find(b"\xff\xd8")
            if start < 0:
                # Retain one byte so an SOI marker spanning chunks is not lost.
                del buffer[:-1]
                break
            if start:
                del buffer[:start]
            end = buffer.find(b"\xff\xd9", 2)
            if end < 0:
                break
            yield bytes(buffer[: end + 2])
            del buffer[: end + 2]


def measure_profile(
    session: requests.Session,
    base_url: str,
    stream_url: str,
    profile: Profile,
    duration: float,
    minimum_fps: float,
) -> Result:
    before = get_status(session, base_url)
    rssi_before = before.get("wifi_rssi")
    timestamps: list[float] = []
    sizes: list[int] = []
    reconnects = 0
    error: str | None = None
    deadline = time.monotonic() + duration

    try:
        while time.monotonic() < deadline:
            try:
                with session.get(stream_url, stream=True, timeout=(3, 8)) as response:
                    response.raise_for_status()
                    for frame in jpeg_frames(response.iter_content(chunk_size=4096)):
                        now = time.monotonic()
                        if now > deadline:
                            break
                        timestamps.append(now)
                        sizes.append(len(frame))
                    if time.monotonic() < deadline:
                        reconnects += 1
            except requests.RequestException as exc:
                reconnects += 1
                error = str(exc)
                if time.monotonic() < deadline:
                    time.sleep(0.25)
    except KeyboardInterrupt:
        error = "interrupted"

    elapsed = max(0.0, (timestamps[-1] - timestamps[0]) if len(timestamps) > 1 else 0.0)
    fps = (len(timestamps) - 1) / elapsed if elapsed > 0 else 0.0
    gaps_ms = [(later - earlier) * 1000.0 for earlier, later in zip(timestamps, timestamps[1:])]
    p95_gap = percentile(gaps_ms, 95)
    max_gap = max(gaps_ms) if gaps_ms else None
    stalls = sum(gap > 1000.0 for gap in gaps_ms)
    try:
        after = get_status(session, base_url)
        rssi_after = after.get("wifi_rssi")
    except requests.RequestException as exc:
        rssi_after = None
        error = error or f"status after benchmark failed: {exc}"

    stable = (
        error is None
        and fps >= minimum_fps
        and reconnects == 0
        and stalls == 0
        and p95_gap is not None
        and p95_gap <= max(750.0, 2000.0 / minimum_fps)
    )
    return Result(
        profile=profile.name,
        width=profile.width,
        height=profile.height,
        quality=profile.quality,
        frames=len(timestamps),
        elapsed_seconds=elapsed,
        fps=fps,
        mean_bytes=statistics.fmean(sizes) if sizes else 0.0,
        p95_gap_ms=p95_gap,
        max_gap_ms=max_gap,
        stalls=stalls,
        reconnects=reconnects,
        rssi_before=rssi_before,
        rssi_after=rssi_after,
        stable=stable,
        error=error,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="ESP32 IP address or hostname")
    parser.add_argument("--http-port", type=int, default=80)
    parser.add_argument("--stream-port", type=int, default=81)
    parser.add_argument("--duration", type=float, default=60.0, help="seconds per profile")
    parser.add_argument("--minimum-fps", type=float, default=8.0)
    parser.add_argument(
        "--profiles",
        default="qvga,vga,svga",
        help="comma-separated selection: qvga,vga,svga",
    )
    parser.add_argument(
        "--no-camera-reset",
        action="store_false",
        dest="reset_camera_before_test",
        help="do not reset the camera before measuring (normally not recommended)",
    )
    parser.add_argument("--json", action="store_true", help="also emit machine-readable results")
    args = parser.parse_args()

    if args.duration < 10 or args.minimum_fps <= 0:
        parser.error("--duration must be at least 10 and --minimum-fps must be positive")

    names = [name.strip().lower() for name in args.profiles.split(",") if name.strip()]
    unknown = [name for name in names if name not in PROFILES]
    if unknown or not names:
        parser.error(f"unknown profile(s): {', '.join(unknown) or 'none'}")

    base_url = f"http://{args.host}:{args.http_port}"
    stream_url = f"http://{args.host}:{args.stream_port}/stream"
    session = requests.Session()
    results: list[Result] = []
    try:
        status = get_status(session, base_url)
    except requests.RequestException as exc:
        print(f"ESP32 status is unreachable at {base_url}: {exc}", file=sys.stderr)
        return 2

    if args.reset_camera_before_test:
        try:
            reset_camera(session, base_url)
            status = get_status(session, base_url)
        except requests.RequestException as exc:
            print(f"Could not reset the ESP32 camera: {exc}", file=sys.stderr)
            return 2

    original_sensor_values = (
        ("framesize", status.get("framesize")),
        ("quality", status.get("quality")),
    )
    if not all(isinstance(value, int) for _, value in original_sensor_values):
        print("ESP32 status lacks valid framesize/quality values.", file=sys.stderr)
        return 2

    print(
        f"ESP32 {args.host}: RSSI {status.get('wifi_rssi')} dBm, "
        f"camera_ready={status.get('camera_ready')}, duration={args.duration:.0f}s/profile"
    )
    for name in names:
        profile = PROFILES[name]
        print(f"\nTesting {profile.name} {profile.width}x{profile.height}, JPEG quality {profile.quality} ...")
        try:
            set_profile(session, base_url, profile)
            result = measure_profile(
                session, base_url, stream_url, profile, args.duration, args.minimum_fps
            )
        except requests.RequestException as exc:
            result = Result(
                profile=profile.name,
                width=profile.width,
                height=profile.height,
                quality=profile.quality,
                frames=0,
                elapsed_seconds=0.0,
                fps=0.0,
                mean_bytes=0.0,
                p95_gap_ms=None,
                max_gap_ms=None,
                stalls=0,
                reconnects=0,
                rssi_before=None,
                rssi_after=None,
                stable=False,
                error=str(exc),
            )
        results.append(result)
        state = "STABLE" if result.stable else "not stable"
        print(
            f"  {state}: {result.fps:.2f} FPS, {result.mean_bytes / 1024:.1f} KiB/frame, "
            f"p95 gap={result.p95_gap_ms!s} ms, max gap={result.max_gap_ms!s} ms, "
            f"reconnects={result.reconnects}, RSSI={result.rssi_before}->{result.rssi_after}"
        )
        if result.error:
            print(f"  error: {result.error}")

    stable = [result for result in results if result.stable]
    if stable:
        winner = max(stable, key=lambda result: result.width * result.height)
        selected_profile = next(profile for profile in PROFILES.values() if profile.name == winner.profile)
        try:
            set_profile(session, base_url, selected_profile)
        except requests.RequestException as exc:
            print(f"Could not apply the recommended profile: {exc}", file=sys.stderr)
            return 2
        print(
            f"\nRecommendation: {winner.profile} ({winner.width}x{winner.height}) at "
            f"quality {winner.quality}, {winner.fps:.2f} effective FPS; it is now active."
        )
    else:
        try:
            set_sensor_values(session, base_url, original_sensor_values)
        except requests.RequestException as exc:
            print(f"Could not restore the initial camera profile: {exc}", file=sys.stderr)
            return 2
        print(
            "\nNo profile met the stability threshold; the initial camera profile was restored. "
            "Improve RSSI to at least -70 dBm (preferably -60 dBm) and repeat the benchmark."
        )

    if args.json:
        print(json.dumps([asdict(result) for result in results], indent=2))
    return 0 if stable else 1


if __name__ == "__main__":
    raise SystemExit(main())
