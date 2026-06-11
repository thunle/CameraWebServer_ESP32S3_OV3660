from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import requests

from .config import AppConfig


class VisionMonitorController:
    def __init__(
        self,
        config: AppConfig,
        config_path: str | Path,
        worker_port: int | None = None,
    ) -> None:
        self.config = config
        self.config_path = Path(config_path)
        self.public_host = config.monitor_api.host
        self.public_port = config.monitor_api.port
        self.worker_port = worker_port or self._pick_worker_port(self.public_port + 1)
        self._lock = threading.Lock()
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None
        self._process: subprocess.Popen[str] | None = None
        self._worker_stdout = None
        self._worker_stderr = None
        self._last_error = ""

    def run(self) -> None:
        handler = self._make_handler()
        self._server = ThreadingHTTPServer((self.public_host, self.public_port), handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        print(f"[INFO] Controller listening on http://{self.public_host}:{self.public_port}")
        print(f"[INFO] Monitor worker port: http://127.0.0.1:{self.worker_port}")
        if not self.start_monitor():
            print(f"[WARN] Could not start monitor worker: {self._last_error}")
        try:
            while True:
                time.sleep(1.0)
        except KeyboardInterrupt:
            pass
        finally:
            self.stop_monitor()
            if self._server is not None:
                self._server.shutdown()
                self._server.server_close()
                self._server = None
            if self._thread is not None:
                self._thread.join(timeout=2.0)
                self._thread = None

    def start_monitor(self) -> bool:
        with self._lock:
            if self._process is not None and self._process.poll() is None:
                return True
            self._last_error = ""
            self._process = self._spawn_monitor()

        if self._wait_until_ready(timeout=45.0):
            return True

        self._last_error = "Monitor worker did not become ready in time."
        self.stop_monitor()
        return False

    def stop_monitor(self) -> None:
        with self._lock:
            process = self._process
            self._process = None
            worker_stdout = self._worker_stdout
            worker_stderr = self._worker_stderr
            self._worker_stdout = None
            self._worker_stderr = None

        if process is not None and process.poll() is None:
            try:
                process.terminate()
                process.wait(timeout=10.0)
            except Exception:
                try:
                    process.kill()
                except Exception:
                    pass
        if worker_stdout is not None:
            worker_stdout.close()
        if worker_stderr is not None:
            worker_stderr.close()
        self._last_error = ""

    def is_monitor_running(self) -> bool:
        with self._lock:
            return self._process is not None and self._process.poll() is None

    def api_status(self) -> dict[str, object]:
        if self.is_monitor_running():
            proxied = self._proxy_json("GET", "/api/status")
            if proxied is not None:
                proxied["controller_running"] = True
                proxied["worker_port"] = self.worker_port
                return proxied
        return {
            "active": self.is_monitor_running(),
            "alarm_count": 0,
            "last_message": "Monitor stopped" if not self.is_monitor_running() else "",
            "last_error": self._last_error,
            "controller_running": True,
            "worker_port": self.worker_port,
        }

    def _make_handler(self) -> type[BaseHTTPRequestHandler]:
        controller = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, _format: str, *_args: object) -> None:
                return

            def do_GET(self) -> None:
                parsed = urlparse(self.path)
                if parsed.path == "/api/status":
                    self._send_json(controller.api_status())
                    return
                if parsed.path == "/api/system/active":
                    value = parse_qs(parsed.query).get("value", [""])[0].lower()
                    if value in {"1", "true", "on", "yes"}:
                        controller.start_monitor()
                    elif value in {"0", "false", "off", "no"}:
                        controller.stop_monitor()
                    self._send_json(controller.api_status())
                    return
                if parsed.path in {"/api/zones", "/api/zones/frame.jpg"} and not controller.is_monitor_running():
                    if not controller.start_monitor():
                        self.send_error(503, controller._last_error or "Monitor worker is not running.")
                        return
                self._proxy()

            def do_PUT(self) -> None:
                parsed = urlparse(self.path)
                if parsed.path == "/api/zones" and not controller.is_monitor_running():
                    if not controller.start_monitor():
                        self.send_error(503, controller._last_error or "Monitor worker is not running.")
                        return
                self._proxy()

            def do_DELETE(self) -> None:
                self._proxy()

            def _proxy(self) -> None:
                if not controller.is_monitor_running():
                    self.send_error(503, "Monitor worker is not running.")
                    return
                response = controller._proxy_raw(
                    method=self.command,
                    path=self.path,
                    headers=self.headers,
                    body=self._read_body(),
                )
                if response is None:
                    self.send_error(502, "Could not reach monitor worker.")
                    return
                status, content_type, headers, body = response
                self.send_response(status)
                if content_type:
                    self.send_header("Content-Type", content_type)
                for key, value in headers.items():
                    if key.lower() in {"content-type", "content-length", "connection", "transfer-encoding"}:
                        continue
                    self.send_header(key, value)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def _read_body(self) -> bytes:
                length = int(self.headers.get("Content-Length", "0") or "0")
                return self.rfile.read(length) if length > 0 else b""

            def _send_json(self, payload: dict[str, object]) -> None:
                body = json.dumps(payload).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        return Handler

    def _spawn_monitor(self) -> subprocess.Popen[str]:
        worker_cwd = self._worker_cwd()
        self._worker_stdout = (worker_cwd / "monitor_worker.log").open("w", encoding="utf-8")
        self._worker_stderr = (worker_cwd / "monitor_worker.err.log").open("w", encoding="utf-8")
        command = [
            sys.executable,
            "-m",
            "vision_alarm",
            "--config",
            str(self.config_path),
            "monitor",
            "--monitor-api-host",
            "127.0.0.1",
            "--monitor-api-port",
            str(self.worker_port),
        ]
        creationflags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        return subprocess.Popen(
            command,
            cwd=str(worker_cwd),
            stdout=self._worker_stdout,
            stderr=self._worker_stderr,
            creationflags=creationflags,
            text=True,
        )

    def _worker_cwd(self) -> Path:
        resolved = self.config_path.resolve()
        if len(resolved.parents) >= 2:
            return resolved.parents[1]
        return resolved.parent

    def _wait_until_ready(self, timeout: float) -> bool:
        deadline = time.monotonic() + timeout
        url = f"http://127.0.0.1:{self.worker_port}/api/status"
        while time.monotonic() < deadline:
            process = self._process
            if process is None or process.poll() is not None:
                return False
            try:
                response = requests.get(url, timeout=1.0)
                if response.status_code < 500:
                    return True
            except requests.RequestException:
                time.sleep(0.5)
        return False

    def _proxy_json(self, method: str, path: str) -> dict[str, object] | None:
        try:
            response = requests.request(method, f"http://127.0.0.1:{self.worker_port}{path}", timeout=3.0)
            response.raise_for_status()
            return response.json()
        except Exception as exc:
            self._last_error = str(exc)
            return None

    def _proxy_raw(
        self,
        method: str,
        path: str,
        headers: object,
        body: bytes,
    ) -> tuple[int, str, dict[str, str], bytes] | None:
        try:
            response = requests.request(
                method,
                f"http://127.0.0.1:{self.worker_port}{path}",
                headers={key: value for key, value in headers.items() if key.lower() != "host"},
                data=body or None,
                timeout=30.0,
            )
            return response.status_code, response.headers.get("Content-Type", ""), dict(response.headers), response.content
        except Exception as exc:
            self._last_error = str(exc)
            return None

    def _pick_worker_port(self, start_port: int) -> int:
        port = max(1024, start_port)
        while port < 65535:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                try:
                    sock.bind(("127.0.0.1", port))
                    return port
                except OSError:
                    port += 1
        raise RuntimeError("No free monitor worker port found.")
