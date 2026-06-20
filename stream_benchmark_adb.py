"""Benchmark an ESP32 MJPEG stream through an Android WLAN-debugging device.

The camera and Android phone share the hotspot. ADB carries only the received
bytes back to the PC, so all camera-to-phone Wi-Fi behaviour is measured on
the actual radio link. It requires Android's built-in toybox (present on stock
Android) and an already-authorized ADB WLAN-debugging connection.
"""

from __future__ import annotations

import argparse
import base64
import json
import math
import queue
import subprocess
import threading
import time
from dataclasses import asdict, dataclass


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
    resolution: str
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
    error: str | None


# Verified against esp32-camera included with Arduino ESP32 core 3.3.7.
PROFILES = (
    Profile("QVGA", 6, 18, 320, 240),
    Profile("VGA", 10, 14, 640, 480),
    Profile("SVGA", 11, 12, 800, 600),
    Profile("XGA", 12, 10, 1024, 768),
)


class AndroidHttp:
    def __init__(self, adb_serial: str, host: str) -> None:
        self.adb = ("adb", "-s", adb_serial)
        self.host = host

    def _command(self, request: bytes, port: int, quit_seconds: int) -> tuple[str, ...]:
        encoded = base64.b64encode(request).decode("ascii")
        remote = (
            f"printf {encoded} | toybox base64 -d | "
            f"toybox nc -q {quit_seconds} -w 5 {self.host} {port}"
        )
        # exec-out invokes the device command directly; unlike `adb shell`,
        # it must not receive an additional literal `shell` argument.
        return (*self.adb, "exec-out", "sh", "-c", remote)

    def request(
        self, path: str, port: int = 80, timeout: float = 12.0, quit_seconds: int | None = None
    ) -> bytes:
        request = (
            f"GET {path} HTTP/1.1\r\nHost: {self.host}\r\n"
            "Connection: close\r\n\r\n"
        ).encode("ascii")
        if quit_seconds is None:
            quit_seconds = max(3, math.ceil(timeout))
        completed = subprocess.run(
            self._command(request, port, quit_seconds), capture_output=True, timeout=timeout + 2
        )
        if completed.returncode:
            raise RuntimeError(completed.stderr.decode("utf-8", "replace").strip())
        return completed.stdout

    def status(self) -> dict:
        response = self.request("/status")
        separator = b"\r\n\r\n"
        if separator not in response:
            raise RuntimeError(f"invalid /status response: {response[:120]!r}")
        return json.loads(response.split(separator, 1)[1])

    def reset_camera(self) -> None:
        response = self.request("/camera_reset", timeout=30.0, quit_seconds=30)
        separator = b"\r\n\r\n"
        if separator not in response:
            raise RuntimeError(f"invalid /camera_reset response: {response[:120]!r}")
        payload = json.loads(response.split(separator, 1)[1])
        if not payload.get("camera_reset"):
            raise RuntimeError(f"camera reset was rejected: {payload}")
        time.sleep(2.0)

    def set_profile(self, profile: Profile) -> None:
        for variable, value in (("framesize", profile.framesize), ("quality", profile.quality)):
            response = self.request(f"/control?var={variable}&val={value}")
            if b" 200 " not in response[:40]:
                raise RuntimeError(f"could not set {variable}: {response[:120]!r}")
        time.sleep(1.0)

    def restore(self, framesize: int, quality: int) -> None:
        self.set_profile(Profile("restore", framesize, quality, 0, 0))

    def open_stream(self) -> subprocess.Popen[bytes]:
        request = (
            f"GET /stream HTTP/1.1\r\nHost: {self.host}\r\n"
            "Connection: keep-alive\r\n\r\n"
        ).encode("ascii")
        return subprocess.Popen(
            self._command(request, 81, 120), stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def measure(client: AndroidHttp, profile: Profile, duration: float, minimum_fps: float) -> Result:
    before = client.status()
    process = client.open_stream()
    if process.stdout is None:
        raise RuntimeError("could not open stream stdout")

    chunks: queue.Queue[bytes | None] = queue.Queue()

    def reader() -> None:
        try:
            while data := process.stdout.read(4096):
                chunks.put(data)
        finally:
            chunks.put(None)

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    deadline = time.monotonic() + duration
    buffer = bytearray()
    timestamps: list[float] = []
    sizes: list[int] = []
    error: str | None = None

    try:
        while time.monotonic() < deadline:
            try:
                data = chunks.get(timeout=8.0)
            except queue.Empty:
                error = "stream produced no bytes for 8 seconds"
                break
            if data is None:
                error = "stream process ended"
                break
            buffer.extend(data)
            while True:
                start = buffer.find(b"\xff\xd8")
                if start < 0:
                    if len(buffer) > 1:
                        del buffer[:-1]
                    break
                if start:
                    del buffer[:start]
                end = buffer.find(b"\xff\xd9", 2)
                if end < 0:
                    break
                sizes.append(end + 2)
                timestamps.append(time.monotonic())
                del buffer[: end + 2]
    finally:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()

    elapsed = timestamps[-1] - timestamps[0] if len(timestamps) > 1 else 0.0
    fps = (len(timestamps) - 1) / elapsed if elapsed else 0.0
    gaps = [(newer - older) * 1000 for older, newer in zip(timestamps, timestamps[1:])]
    p95_gap = percentile(gaps, 95)
    max_gap = max(gaps) if gaps else None
    stalls = sum(gap > 1000 for gap in gaps)
    try:
        after = client.status()
        after_rssi = after.get("wifi_rssi")
    except Exception as exc:  # benchmark result is still useful
        after_rssi = None
        error = error or f"status after test failed: {exc}"
    stable = (
        error is None
        and fps >= minimum_fps
        and stalls == 0
        and p95_gap is not None
        and p95_gap <= max(750.0, 2000.0 / minimum_fps)
    )
    return Result(
        profile=profile.name,
        resolution=f"{profile.width}x{profile.height}",
        quality=profile.quality,
        frames=len(timestamps),
        elapsed_seconds=elapsed,
        fps=fps,
        mean_bytes=sum(sizes) / len(sizes) if sizes else 0.0,
        p95_gap_ms=p95_gap,
        max_gap_ms=max_gap,
        stalls=stalls,
        reconnects=0,
        rssi_before=before.get("wifi_rssi"),
        rssi_after=after_rssi,
        stable=stable,
        error=error,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb-serial", required=True)
    parser.add_argument("--host", required=True)
    parser.add_argument("--duration", type=float, default=60.0)
    parser.add_argument("--minimum-fps", type=float, default=8.0)
    parser.add_argument(
        "--profiles",
        default="qvga,vga,svga,xga",
        help="comma-separated selection: qvga,vga,svga,xga",
    )
    parser.add_argument(
        "--no-camera-reset",
        action="store_false",
        dest="reset_camera_before_test",
        help="do not reset the camera before measuring (normally not recommended)",
    )
    args = parser.parse_args()
    if args.duration < 10 or args.minimum_fps <= 0:
        parser.error("duration must be at least 10 seconds and minimum FPS must be positive")
    profile_by_name = {profile.name.lower(): profile for profile in PROFILES}
    names = [name.strip().lower() for name in args.profiles.split(",") if name.strip()]
    if not names or any(name not in profile_by_name for name in names):
        parser.error("--profiles must contain only qvga,vga,svga,xga")
    selected_profiles = [profile_by_name[name] for name in names]

    client = AndroidHttp(args.adb_serial, args.host)
    if args.reset_camera_before_test:
        client.reset_camera()
    initial = client.status()
    original_framesize = initial.get("framesize")
    original_quality = initial.get("quality")
    if not isinstance(original_framesize, int) or not isinstance(original_quality, int):
        raise RuntimeError("camera status lacks framesize or quality")
    print(f"Initial status: {json.dumps(initial, ensure_ascii=False)}")

    results: list[Result] = []
    for profile in selected_profiles:
        print(f"Testing {profile.name} for {args.duration:.0f} seconds...", flush=True)
        try:
            client.set_profile(profile)
            result = measure(client, profile, args.duration, args.minimum_fps)
        except Exception as exc:
            result = Result(profile.name, f"{profile.width}x{profile.height}", profile.quality,
                            0, 0.0, 0.0, 0.0, None, None, 0, 0, None, None, False, str(exc))
        results.append(result)
        print(json.dumps(asdict(result), ensure_ascii=False), flush=True)

    stable = [result for result in results if result.stable]
    if stable:
        winner = max(stable, key=lambda item: int(item.resolution.split("x")[0]) * int(item.resolution.split("x")[1]))
        selected = next(profile for profile in selected_profiles if profile.name == winner.profile)
        client.set_profile(selected)
        print(f"WINNER {winner.profile}: {winner.fps:.2f} FPS", flush=True)
    else:
        client.restore(original_framesize, original_quality)
        print("NO_STABLE_PROFILE; original camera profile restored", flush=True)
    print(json.dumps([asdict(result) for result in results], ensure_ascii=False, indent=2))
    return 0 if stable else 1


if __name__ == "__main__":
    raise SystemExit(main())
