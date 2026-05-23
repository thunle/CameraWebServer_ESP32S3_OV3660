from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import cv2
import numpy as np

from .config import Resolution, ZoneDefinition


ZONE_COLORS = [
    (0, 165, 255),
    (255, 180, 0),
    (255, 0, 180),
    (0, 220, 0),
    (220, 80, 80),
]


def scale_points(
    points: Sequence[Sequence[int]],
    source_resolution: Resolution | None,
    target_width: int,
    target_height: int,
) -> np.ndarray:
    if (
        source_resolution is None
        or source_resolution.width <= 0
        or source_resolution.height <= 0
    ):
        return np.asarray(points, dtype=np.int32)

    scale_x = target_width / source_resolution.width
    scale_y = target_height / source_resolution.height
    scaled = [
        [int(round(point[0] * scale_x)), int(round(point[1] * scale_y))]
        for point in points
    ]
    return np.asarray(scaled, dtype=np.int32)


def color_for_index(index: int) -> tuple[int, int, int]:
    return ZONE_COLORS[index % len(ZONE_COLORS)]


def centroid(points: np.ndarray) -> tuple[int, int]:
    x, y = np.mean(points, axis=0)
    return int(x), int(y)


def draw_polygon_overlay(
    frame: np.ndarray,
    polygon: np.ndarray,
    color: tuple[int, int, int],
    label: str,
    is_alert: bool = False,
) -> np.ndarray:
    overlay = frame.copy()
    fill_color = (0, 0, 255) if is_alert else color
    cv2.fillPoly(overlay, [polygon], fill_color)
    cv2.addWeighted(overlay, 0.22, frame, 0.78, 0, frame)
    cv2.polylines(frame, [polygon], True, fill_color, 2, cv2.LINE_AA)

    label_pos = centroid(polygon)
    cv2.putText(
        frame,
        label,
        label_pos,
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        fill_color,
        2,
        cv2.LINE_AA,
    )
    return frame


@dataclass
class ZoneEditor:
    frame: np.ndarray
    existing_zones: list[ZoneDefinition]
    source_resolution: Resolution | None
    window_name: str = "No-Go Zone Editor"

    def __post_init__(self) -> None:
        self.current_points: list[list[int]] = []
        self.status_message = (
            "Left click: add point | Right click or Enter: finish zone"
        )
        self.frame_resolution = Resolution(
            width=int(self.frame.shape[1]), height=int(self.frame.shape[0])
        )
        self.zones: list[ZoneDefinition] = []
        for zone in self.existing_zones:
            scaled = scale_points(
                zone.points,
                self.source_resolution,
                self.frame_resolution.width,
                self.frame_resolution.height,
            )
            self.zones.append(
                ZoneDefinition(
                    id=zone.id,
                    name=zone.name,
                    points=scaled.astype(int).tolist(),
                )
            )

    def run(self) -> list[ZoneDefinition] | None:
        cv2.namedWindow(self.window_name, cv2.WINDOW_NORMAL)
        cv2.setMouseCallback(self.window_name, self._on_mouse_event)

        try:
            while True:
                cv2.imshow(self.window_name, self._render())
                key = cv2.waitKey(20) & 0xFF

                if key in (13, ord("n")):
                    self._finish_zone()
                elif key == ord("u"):
                    self._undo()
                elif key == ord("r"):
                    self.current_points.clear()
                    self.zones.clear()
                    self.status_message = "All zones cleared."
                elif key == ord("s"):
                    if self.current_points:
                        self._finish_zone()
                    return self.zones
                elif key in (27, ord("q")):
                    return None
        finally:
            cv2.destroyWindow(self.window_name)

    def _on_mouse_event(
        self, event: int, x: int, y: int, _flags: int, _userdata: object
    ) -> None:
        if event == cv2.EVENT_LBUTTONDOWN:
            self.current_points.append([int(x), int(y)])
            self.status_message = f"Point {len(self.current_points)} added."
        elif event == cv2.EVENT_RBUTTONDOWN:
            self._finish_zone()

    def _finish_zone(self) -> None:
        if len(self.current_points) < 3:
            self.status_message = "A zone needs at least 3 points."
            return

        zone_index = len(self.zones) + 1
        self.zones.append(
            ZoneDefinition(
                id=f"zone-{zone_index}",
                name=f"No-Go Zone {zone_index}",
                points=[point[:] for point in self.current_points],
            )
        )
        self.current_points.clear()
        self.status_message = f"Saved zone {zone_index}."

    def _undo(self) -> None:
        if self.current_points:
            self.current_points.pop()
            self.status_message = "Removed last point."
            return

        if self.zones:
            removed = self.zones.pop()
            self.status_message = f"Removed {removed.name}."
            return

        self.status_message = "Nothing to undo."

    def _render(self) -> np.ndarray:
        canvas = self.frame.copy()

        for index, zone in enumerate(self.zones):
            polygon = np.asarray(zone.points, dtype=np.int32)
            canvas = draw_polygon_overlay(
                canvas,
                polygon,
                color_for_index(index),
                zone.name,
            )

        if self.current_points:
            current = np.asarray(self.current_points, dtype=np.int32)
            for point in self.current_points:
                cv2.circle(
                    canvas,
                    (int(point[0]), int(point[1])),
                    4,
                    (255, 255, 255),
                    -1,
                    cv2.LINE_AA,
                )

            if len(current) >= 2:
                cv2.polylines(
                    canvas,
                    [current],
                    False,
                    (255, 255, 255),
                    2,
                    cv2.LINE_AA,
                )

        lines = [
            "Draw zones on the captured ESP32 frame",
            "S: save | Q: quit | U: undo | R: reset all",
            self.status_message,
        ]

        y = 28
        for line in lines:
            cv2.putText(
                canvas,
                line,
                (16, y),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.65,
                (255, 255, 255),
                2,
                cv2.LINE_AA,
            )
            y += 28

        return canvas
