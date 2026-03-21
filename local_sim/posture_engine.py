"""Posture engine — shared backend logic for Android port.

State names and thresholds match clinical documentation:
  GOOD       : 0–15° forward flexion
  MODERATE   : 15–30° (caution zone)
  RISK       : > 30° (neck load increases 3–5×)
  UNRELIABLE : signal quality too low to classify
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class PostureState(str, Enum):
    GOOD       = "GOOD"
    MODERATE   = "MODERATE"
    RISK       = "RISK"
    UNRELIABLE = "UNRELIABLE"


@dataclass
class PostureReading:
    relative_flexion_deg:  float
    smoothed_relative_deg: float
    head_pitch_deg:        float
    head_yaw_deg:          float
    head_roll_deg:         float
    phone_pitch_deg:       float
    confidence:            float
    state:                 PostureState
    timestamp_s:           float


class PostureClassifier:
    """Single-axis hysteresis classifier.

    Default thresholds (doc-based):
      moderate_threshold_deg = 15.0
      risk_threshold_deg     = 30.0
      hold_seconds           = 4.0   (dwell time before state commits)
    """

    def __init__(
        self,
        moderate_threshold_deg: float = 15.0,
        risk_threshold_deg:     float = 30.0,
        hold_seconds:           float = 4.0,
    ) -> None:
        self.moderate_threshold_deg = moderate_threshold_deg
        self.risk_threshold_deg     = risk_threshold_deg
        self.hold_seconds           = hold_seconds
        self._current_state  = PostureState.GOOD
        self._pending_state  = PostureState.GOOD
        self._pending_since  = 0.0

    def classify(
        self,
        delta_deg:   float,
        reliable:    bool,
        timestamp_s: float,
    ) -> PostureState:
        if not reliable:
            self._current_state = PostureState.UNRELIABLE
            self._pending_state = PostureState.UNRELIABLE
            self._pending_since = timestamp_s
            return self._current_state

        if delta_deg >= self.risk_threshold_deg:
            target = PostureState.RISK
        elif delta_deg >= self.moderate_threshold_deg:
            target = PostureState.MODERATE
        else:
            target = PostureState.GOOD

        if target == self._current_state:
            self._pending_state = target
            self._pending_since = timestamp_s
            return self._current_state

        if target != self._pending_state:
            self._pending_state = target
            self._pending_since = timestamp_s
            return self._current_state

        if (timestamp_s - self._pending_since) >= self.hold_seconds:
            self._current_state = target
        return self._current_state


class PostureFusionEngine:
    """Android-ready fusion engine.

    Fuses camera head-pose with phone IMU orientation:
      neck_pitch = head_pitch_cam + phone_pitch_world
    Uses EMA smoothing (alpha) over raw relative angle.
    """

    def __init__(
        self,
        alpha:           float = 0.25,
        min_confidence:  float = 0.25,
        max_motion_score: float = 1.6,
        classifier: PostureClassifier | None = None,
    ) -> None:
        self.alpha            = alpha
        self.min_confidence   = min_confidence
        self.max_motion_score = max_motion_score
        self.classifier       = classifier or PostureClassifier()
        self._baseline:       float | None = None
        self._smoothed:       float = 0.0
        self._initialized:    bool  = False

    def calibrate(self, baseline_relative_deg: float) -> None:
        self._baseline = baseline_relative_deg

    def clear_calibration(self) -> None:
        self._baseline = None

    def is_calibrated(self) -> bool:
        return self._baseline is not None

    def fuse(
        self,
        *,
        head_pitch_deg:      float,
        head_yaw_deg:        float,
        head_roll_deg:       float,
        head_confidence:     float,
        phone_pitch_deg:     float,
        motion_score:        float,
        orientation_reliable: bool,
        timestamp_s:         float,
    ) -> PostureReading:
        relative = head_pitch_deg + phone_pitch_deg   # fusion formula
        if not self._initialized:
            self._smoothed    = relative
            self._initialized = True
        else:
            self._smoothed = self.alpha * relative + (1.0 - self.alpha) * self._smoothed

        baseline = self._baseline if self._baseline is not None else self._smoothed
        delta    = max(0.0, self._smoothed - baseline)

        reliable = (
            orientation_reliable
            and head_confidence   >= self.min_confidence
            and motion_score      <= self.max_motion_score
        )
        confidence = max(0.0, min(1.0, head_confidence - motion_score / 4.0))
        state = self.classifier.classify(
            delta_deg=delta,
            reliable=reliable,
            timestamp_s=timestamp_s,
        )

        return PostureReading(
            relative_flexion_deg  = delta,
            smoothed_relative_deg = self._smoothed,
            head_pitch_deg        = head_pitch_deg,
            head_yaw_deg          = head_yaw_deg,
            head_roll_deg         = head_roll_deg,
            phone_pitch_deg       = phone_pitch_deg,
            confidence            = confidence,
            state                 = state,
            timestamp_s           = timestamp_s,
        )
