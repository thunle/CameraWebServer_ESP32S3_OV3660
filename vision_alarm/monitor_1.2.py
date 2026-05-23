from __future__ import annotations

import inspect
import time
from dataclasses import dataclass

import cv2
import numpy as np
import supervision as sv
from ultralytics import YOLO

from .config import AppConfig, ByteTrackSettings, ZoneDefinition
from .esp32 import Esp32CameraClient, LatestFrameStream
from .zones import color_for_index, draw_polygon_overlay, scale_points


def erstelle_bytetrack(einstellungen: ByteTrackSettings) -> sv.ByteTrack:
    parameter = inspect.signature(sv.ByteTrack).parameters
    argumente: dict[str, object] = {}

    if "track_activation_threshold" in parameter:
        argumente["track_activation_threshold"] = (
            einstellungen.track_activation_threshold
        )
    elif "track_thresh" in parameter:
        argumente["track_thresh"] = einstellungen.track_activation_threshold

    if "lost_track_buffer" in parameter:
        argumente["lost_track_buffer"] = einstellungen.lost_track_buffer
    elif "track_buffer" in parameter:
        argumente["track_buffer"] = einstellungen.lost_track_buffer

    if "minimum_matching_threshold" in parameter:
        argumente["minimum_matching_threshold"] = (
            einstellungen.minimum_matching_threshold
        )
    elif "match_thresh" in parameter:
        argumente["match_thresh"] = einstellungen.minimum_matching_threshold

    if "frame_rate" in parameter:
        argumente["frame_rate"] = einstellungen.frame_rate

    return sv.ByteTrack(**argumente)


def erstelle_polygon_zone(
    polygon_punkte: np.ndarray, bild_aufloesung: tuple[int, int]
) -> sv.PolygonZone:
    parameter = inspect.signature(sv.PolygonZone).parameters
    argumente: dict[str, object] = {"polygon": polygon_punkte}
    if "frame_resolution_wh" in parameter:
        argumente["frame_resolution_wh"] = bild_aufloesung
    if "triggering_anchors" in parameter and hasattr(sv, "Position"):
        argumente["triggering_anchors"] = (sv.Position.BOTTOM_CENTER,)
    return sv.PolygonZone(**argumente)


def tracker_ids_aus_detektionen(detektionen: sv.Detections) -> set[int]:
    tracker_ids = getattr(detektionen, "tracker_id", None)
    if tracker_ids is None:
        return set()

    gefundene_ids: set[int] = set()
    for roh_id in np.asarray(tracker_ids).reshape(-1).tolist():
        if roh_id is None:
            continue
        try:
            tracker_id = int(roh_id)
        except (TypeError, ValueError):
            continue
        if tracker_id >= 0:
            gefundene_ids.add(tracker_id)
    return gefundene_ids


@dataclass
class ZwischengespeicherteZone:
    definition: ZoneDefinition
    polygon: np.ndarray
    zone: sv.PolygonZone


@dataclass
class FrameZeiten:
    quell_ms: float = 0.0
    inferenz_ms: float = 0.0
    tracking_ms: float = 0.0
    zeichnen_ms: float = 0.0
    gesamt_ms: float = 0.0


class AlarmSteuerung:
    def __init__(self, kamera_client: Esp32CameraClient, config: AppConfig) -> None:
        self.kamera_client = kamera_client
        self.config = config
        self.letzte_ausloesung = 0.0
        self.letzte_meldung = ""
        self.letzter_fehler = ""

    def ausloesen(self, zonen_name: str, tracker_ids: list[int]) -> bool:
        jetzt = time.monotonic()
        if jetzt - self.letzte_ausloesung < self.config.alarm.cooldown_seconds:
            return False

        self.letzte_ausloesung = jetzt
        ids_text = ", ".join(str(tracker_id) for tracker_id in tracker_ids)
        self.letzte_meldung = f"ALARM: {zonen_name} betreten von Track(s) {ids_text}"
        self.letzter_fehler = ""

        print(f"[ALARM] {self.letzte_meldung}")
        if self.config.alarm.local_terminal_bell:
            print("\a", end="", flush=True)

        try:
            self.kamera_client.pulse_buzzer(self.config.alarm.pulse_ms)
        except Exception as fehler:
            self.letzter_fehler = str(fehler)
            print(f"[WARN] ESP32-Buzzer konnte nicht ausgelost werden: {fehler}")

        return True

    def banner_text(self) -> str:
        halte_zeit = max(2.0, self.config.alarm.pulse_ms / 1000.0)
        if time.monotonic() - self.letzte_ausloesung <= halte_zeit:
            return self.letzte_meldung
        return ""


class UeberwachungsMonitor:
    def __init__(self, config: AppConfig) -> None:
        self.config = config
        self.kamera_client = Esp32CameraClient(config)
        self.modell = YOLO(config.model)
        self.alarm = AlarmSteuerung(self.kamera_client, config)
        self._zonen_cache_aufloesung: tuple[int, int] | None = None
        self._zonen_cache: list[ZwischengespeicherteZone] = []
        self._fps = 0.0
        self._letztes_frame_zeit = 0.0
        self._zeiten = FrameZeiten()
        self.laufzeit_zuruecksetzen()

    def _zeit_aktualisieren(self, name: str, wert_ms: float) -> None:
        bisheriger_wert = getattr(self._zeiten, name)
        neuer_wert = wert_ms if bisheriger_wert == 0.0 else bisheriger_wert * 0.8 + wert_ms * 0.2
        setattr(self._zeiten, name, neuer_wert)

    def laufzeit_zuruecksetzen(self) -> None:
        self.tracker = erstelle_bytetrack(self.config.bytetrack)
        self.vorherige_zonen_mitglieder = {
            zone.id: set() for zone in self.config.zones
        }
        self._zonen_cache_aufloesung = None
        self._zonen_cache = []

    def ausfuehren(self) -> None:
        if not self.config.zones:
            raise RuntimeError("Keine Zonen konfiguriert. Zuerst draw-zones ausfuhren.")

        if self.config.source_mode == "snapshot":
            self.snapshot_modus_ausfuehren()
            return

        self.stream_modus_ausfuehren()

    def stream_modus_ausfuehren(self) -> None:
        stream_leser = LatestFrameStream(self.kamera_client)
        letzte_sequenz = 0
        letzte_warnung_zeit = 0.0

        print("[INFO] Source mode: stream")
        print(f"[INFO] Stream URL: {self.config.stream_url}")
        print(f"[INFO] Model: {self.config.model}")
        print("[INFO] Press Q or ESC in the preview window to stop.")

        self.laufzeit_zuruecksetzen()
        stream_leser.start()

        try:
            while True:
                letzte_sequenz, frame = stream_leser.read_latest(letzte_sequenz)
                if frame is None:
                    if (
                        stream_leser.last_error
                        and time.monotonic() - letzte_warnung_zeit > 1.0
                    ):
                        print(f"[WARN] {stream_leser.last_error}")
                        letzte_warnung_zeit = time.monotonic()
                    if self.config.show_window:
                        taste = cv2.waitKey(1) & 0xFF
                        if taste in (27, ord("q")):
                            break
                    time.sleep(0.005)
                    continue

                self._zeit_aktualisieren("quell_ms", stream_leser.source_read_ms)
                annotiertes_bild = self.frame_verarbeiten(frame)

                if self.config.show_window:
                    cv2.imshow(self.config.window_name, annotiertes_bild)
                    taste = cv2.waitKey(1) & 0xFF
                    if taste in (27, ord("q")):
                        break
        finally:
            stream_leser.stop()
            cv2.destroyAllWindows()

    def snapshot_modus_ausfuehren(self) -> None:
        intervall_sekunden = max(0.05, self.config.snapshot_interval_ms / 1000.0)

        print("[INFO] Source mode: snapshot")
        print(f"[INFO] Capture URL: {self.config.capture_url}")
        print(f"[INFO] Model: {self.config.model}")
        print("[INFO] Press Q or ESC in the preview window to stop.")

        self.laufzeit_zuruecksetzen()

        try:
            while True:
                schleifen_start = time.monotonic()
                quell_start = time.perf_counter()
                try:
                    frame = self.kamera_client.capture_frame()
                except Exception as fehler:
                    print(f"[WARN] Snapshot fehlgeschlagen: {fehler}")
                    time.sleep(1.0)
                    continue
                self._zeit_aktualisieren(
                    "quell_ms", (time.perf_counter() - quell_start) * 1000.0
                )

                annotiertes_bild = self.frame_verarbeiten(frame)

                if self.config.show_window:
                    cv2.imshow(self.config.window_name, annotiertes_bild)
                    taste = cv2.waitKey(1) & 0xFF
                    if taste in (27, ord("q")):
                        break

                vergangene_zeit = time.monotonic() - schleifen_start
                restzeit = intervall_sekunden - vergangene_zeit
                if restzeit > 0:
                    time.sleep(restzeit)
        finally:
            cv2.destroyAllWindows()

    def frame_verarbeiten(self, frame: np.ndarray) -> np.ndarray:
        gesamt_start = time.perf_counter()
        jetzt = time.monotonic()
        if self._letztes_frame_zeit:
            delta = jetzt - self._letztes_frame_zeit
            if delta > 0:
                aktuelle_fps = 1.0 / delta
                if self._fps == 0.0:
                    self._fps = aktuelle_fps
                else:
                    self._fps = self._fps * 0.9 + aktuelle_fps * 0.1
        self._letztes_frame_zeit = jetzt

        inferenz_start = time.perf_counter()
        detektionen = self.personen_erkennen(frame)
        self._zeit_aktualisieren(
            "inferenz_ms", (time.perf_counter() - inferenz_start) * 1000.0
        )

        tracking_start = time.perf_counter()
        getrackte_objekte = self.tracker.update_with_detections(detektionen)
        laufzeit_zonen = self.laufzeit_zonen(frame)

        verletzende_tracker_ids: set[int] = set()
        for zwischengespeicherte_zone in laufzeit_zonen:
            maske = zwischengespeicherte_zone.zone.trigger(getrackte_objekte)
            objekte_in_zone = getrackte_objekte[maske]
            aktuelle_ids = tracker_ids_aus_detektionen(objekte_in_zone)
            verletzende_tracker_ids.update(aktuelle_ids)

            vorherige_ids = self.vorherige_zonen_mitglieder.setdefault(
                zwischengespeicherte_zone.definition.id, set()
            )
            neu_eingetretene_ids = sorted(aktuelle_ids - vorherige_ids)
            self.vorherige_zonen_mitglieder[
                zwischengespeicherte_zone.definition.id
            ] = aktuelle_ids

            if neu_eingetretene_ids:
                self.alarm.ausloesen(
                    zwischengespeicherte_zone.definition.name,
                    neu_eingetretene_ids,
                )

        self._zeit_aktualisieren(
            "tracking_ms", (time.perf_counter() - tracking_start) * 1000.0
        )

        zeichnen_start = time.perf_counter()
        annotiertes_bild = self.frame_annotieren(
            frame,
            getrackte_objekte,
            laufzeit_zonen,
            verletzende_tracker_ids,
        )
        self._zeit_aktualisieren(
            "zeichnen_ms", (time.perf_counter() - zeichnen_start) * 1000.0
        )
        self._zeit_aktualisieren(
            "gesamt_ms", (time.perf_counter() - gesamt_start) * 1000.0
        )

        return annotiertes_bild

    def personen_erkennen(self, frame: np.ndarray) -> sv.Detections:
        vorhersage_argumente = {
            "source": frame,
            "conf": self.config.confidence,
            "iou": self.config.iou_threshold,
            "classes": [0],
            "imgsz": self.config.image_size,
            "verbose": False,
        }
        if self.config.device:
            vorhersage_argumente["device"] = self.config.device

        ergebnis = self.modell.predict(**vorhersage_argumente)[0]
        detektionen = sv.Detections.from_ultralytics(ergebnis)
        if len(detektionen) == 0:
            return detektionen

        if getattr(detektionen, "class_id", None) is None:
            return detektionen

        return detektionen[detektionen.class_id == 0]

    def laufzeit_zonen(self, frame: np.ndarray) -> list[ZwischengespeicherteZone]:
        aktuelle_aufloesung = (int(frame.shape[1]), int(frame.shape[0]))
        if self._zonen_cache_aufloesung == aktuelle_aufloesung:
            return self._zonen_cache

        neue_zonen: list[ZwischengespeicherteZone] = []
        for zone in self.config.zones:
            skaliertes_polygon = scale_points(
                zone.points,
                self.config.zone_resolution,
                aktuelle_aufloesung[0],
                aktuelle_aufloesung[1],
            )
            neue_zonen.append(
                ZwischengespeicherteZone(
                    definition=zone,
                    polygon=skaliertes_polygon,
                    zone=erstelle_polygon_zone(
                        skaliertes_polygon, aktuelle_aufloesung
                    ),
                )
            )

        self._zonen_cache_aufloesung = aktuelle_aufloesung
        self._zonen_cache = neue_zonen
        return neue_zonen

    def frame_annotieren(
        self,
        frame: np.ndarray,
        getrackte_objekte: sv.Detections,
        laufzeit_zonen: list[ZwischengespeicherteZone],
        verletzende_tracker_ids: set[int],
    ) -> np.ndarray:
        annotiertes_bild = frame.copy()

        for index, zwischengespeicherte_zone in enumerate(laufzeit_zonen):
            aktuelle_mitglieder = self.vorherige_zonen_mitglieder.get(
                zwischengespeicherte_zone.definition.id, set()
            )
            annotiertes_bild = draw_polygon_overlay(
                annotiertes_bild,
                zwischengespeicherte_zone.polygon,
                color_for_index(index),
                f"{zwischengespeicherte_zone.definition.name}: {len(aktuelle_mitglieder)}",
                is_alert=bool(aktuelle_mitglieder),
            )

        tracker_ids = getattr(getrackte_objekte, "tracker_id", None)
        confidences = getattr(getrackte_objekte, "confidence", None)

        for index, box in enumerate(getrackte_objekte.xyxy):
            x1, y1, x2, y2 = [int(wert) for wert in box]
            tracker_id = None
            if tracker_ids is not None:
                rohe_id = tracker_ids[index]
                if rohe_id is not None:
                    tracker_id = int(rohe_id)

            sicherheit = 0.0
            if confidences is not None:
                sicherheit = float(confidences[index])

            ist_verletzend = (
                tracker_id is not None and tracker_id in verletzende_tracker_ids
            )
            farbe = (0, 0, 255) if ist_verletzend else (0, 220, 0)

            cv2.rectangle(
                annotiertes_bild, (x1, y1), (x2, y2), farbe, 2, cv2.LINE_AA
            )
            beschriftung = (
                f"person #{tracker_id} {sicherheit:.2f}"
                if tracker_id is not None
                else f"person {sicherheit:.2f}"
            )
            cv2.putText(
                annotiertes_bild,
                beschriftung,
                (x1, max(24, y1 - 10)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                farbe,
                2,
                cv2.LINE_AA,
            )

        return annotiertes_bild


VisionMonitor = UeberwachungsMonitor
