"""
Neck Posture Webcam Simulator
==============================
Fully self-contained — no Android dependencies.

Algorithm design (research-grade hybrid):

PRIMARY ESTIMATION  ── solvePnPRansac with 9 face landmarks
  - 9 landmarks give a much better-conditioned 3D-to-2D system than the
    original 6, especially for out-of-plane (pitch / yaw) angles.
  - RANSAC variant automatically rejects any partially-visible or noisy
    landmark so a partially occluded face won't corrupt the result.
  - Reprojection error (pixels): the distance between where the 3D model
    PREDICTS the 2D points and where MediaPipe ACTUALLY sees them.
    Low error → high quality pose estimate.

BACKUP / CROSS-VALIDATION  ── Face Ratio (DFR / CFR)
  - DFR = eye_distance / eyes-to-mouth_distance captured at calibration
    (the user's neutral-upright "Default Face Ratio").
  - CFR = same measure in every frame.
  - When head tilts forward the eye-distance shrinks relative to
    eyes-to-mouth distance → CFR > DFR.
  - Used as a SECONDARY confidence signal: if ratio change strongly
    disagrees with PnP pitch, confidence is reduced.
  - Also drives a cross-check display so you can see both estimates.

FUSION FORMULA  (from clinical documentation)
  Neck Pitch = Face Pitch (camera-relative) + Phone Pitch (gravity-relative)
  This is the correct compensation: phone held low makes the camera look
  upward, adding a false forward-tilt to the raw face angle.
  Virtual phone pitch is controlled by W/S keys in the simulator.

SMOOTHING  ── One Euro Filter (Casiez et al., CHI 2012)
  - Adapts its low-pass cutoff to the speed of the signal.
  - At rest: maximum smoothing (removes hand tremor, sensor noise).
  - During movement: low lag (the cutoff rises automatically).
  - Strictly superior to EMA / moving average for real-time angle tracking.
  - Separate filter instances for pitch, yaw, roll.

CLASSIFICATION  (from clinical documentation)
  Sagittal  (forward / back)  : 0–15° GOOD | 15–30° MODERATE | >30° RISK
  Transverse (left / right)   : 0–20° GOOD | 20–35° MODERATE | >35° RISK
  Final state = max severity of both axes, gated by reliability.

RELIABILITY GATE
  Signal marked UNRELIABLE if:
    • PnP reprojection error > 12 px
    • RANSAC inlier ratio < 60 %
    • No face detected

CALIBRATION
  10-second neutral-posture capture (press C).
  Records baseline neck_pitch, neck_yaw, and DFR.
  All deltas measured from this baseline, so absolute PnP offsets cancel.
"""

from __future__ import annotations

import math
import time
from dataclasses import dataclass, field
from pathlib import Path
from urllib.request import urlretrieve

import cv2
import mediapipe as mp
import numpy as np

# ─────────────────────────────────────────────────────────────────────────────
# 3-D head model and landmark index table
# ─────────────────────────────────────────────────────────────────────────────
# Landmark indices in MediaPipe's 468-point FaceMesh topology.
# Chosen for geometric spread and stability across head orientations.
_LM_IDS: list[int] = [
    1,    # nose tip          ← anchor / centroid
    152,  # chin              ← strong vertical extent
    33,   # left  eye outer   ← horizontal + vertical spread
    263,  # right eye outer
    61,   # left  mouth corner
    291,  # right mouth corner
    10,   # upper forehead    ← increases pitch sensitivity
    234,  # left  cheek/ear   ← increases yaw  sensitivity
    454,  # right cheek/ear
]

# Corresponding 3-D model points in mm (origin = nose tip, +X right, +Y up).
# Based on mean adult anthropometry from published head-pose literature.
_FACE_3D = np.array(
    [
        [  0.0,    0.0,    0.0],   # 1   nose tip
        [  0.0,  -63.6,  -12.5],   # 152 chin
        [-43.3,   32.7,  -26.0],   # 33  left  eye outer corner
        [ 43.3,   32.7,  -26.0],   # 263 right eye outer corner
        [-28.9,  -28.9,  -24.1],   # 61  left  mouth corner
        [ 28.9,  -28.9,  -24.1],   # 291 right mouth corner
        [  0.0,   56.0,  -25.0],   # 10  forehead
        [-65.0,    0.0,  -48.0],   # 234 left  cheek
        [ 65.0,    0.0,  -48.0],   # 454 right cheek
    ],
    dtype=np.float64,
)

# Landmark IDs used by the face-ratio estimator
_EYE_L, _EYE_R       = 33,  263   # outer eye corners
_MOUTH_L, _MOUTH_R   = 61,  291   # mouth corners
_NOSE                 = 1
_CHEEK_L, _CHEEK_R   = 234, 454   # ear-side cheeks


# ─────────────────────────────────────────────────────────────────────────────
# Data containers
# ─────────────────────────────────────────────────────────────────────────────
@dataclass
class HeadPose:
    pitch_deg:       float = 0.0
    yaw_deg:         float = 0.0
    roll_deg:        float = 0.0
    reprojection_err: float = 999.0   # mean reprojection error in pixels
    inlier_ratio:    float = 0.0      # fraction of RANSAC inliers
    confidence:      float = 0.0      # 0..1 overall quality


@dataclass
class NeckReading:
    neck_pitch_deg:  float = 0.0   # fused (PnP pitch + phone pitch)
    neck_yaw_deg:    float = 0.0   # PnP yaw (phone yaw not needed for MVP)
    pitch_delta_deg: float = 0.0   # delta from calibration baseline
    signed_pitch_delta_deg: float = 0.0
    yaw_delta_deg:   float = 0.0
    pitch_state:     str   = "UNRELIABLE"
    yaw_state:       str   = "UNRELIABLE"
    combined_state:  str   = "UNRELIABLE"
    confidence:      float = 0.0
    dfr_ratio:       float = 0.0   # DFR value (saved at calibration)
    cfr_ratio:       float = 0.0   # current frame face ratio
    yaw_symmetry:    float = 0.5   # 0.5 = symmetric / neutral
    assumed_angle_deg: float = 90.0
    assumed_angle_offset_deg: float = 0.0
    ransac_posture_score: float = 0.0
    ransac_posture_state: str = "UNRELIABLE"
    head_pose:       HeadPose = field(default_factory=HeadPose)


# ─────────────────────────────────────────────────────────────────────────────
# One Euro Filter  (Casiez, Roussel, Vogel — CHI 2012)
# ─────────────────────────────────────────────────────────────────────────────
class OneEuroFilter:
    """Adaptive low-pass filter.

    Two tuneable knobs:
      min_cutoff  – smoothing at rest (Hz). Lower → smoother but more lag.
      beta        – speed coefficient.  Higher → less lag on fast movement.
    """

    def __init__(
        self,
        min_cutoff: float = 1.0,
        beta: float = 0.05,
        d_cutoff: float = 1.0,
    ) -> None:
        self.min_cutoff = min_cutoff
        self.beta = beta
        self.d_cutoff = d_cutoff
        self._x:  float | None = None
        self._dx: float = 0.0
        self._t:  float | None = None

    def __call__(self, x: float, t: float) -> float:
        if self._t is None:
            self._x, self._t = x, t
            return x
        dt   = max(1e-9, t - self._t)
        freq = 1.0 / dt
        # Derivative (speed) estimate
        dx      = (x - self._x) * freq
        alpha_d = self._alpha(freq, self.d_cutoff)
        self._dx = alpha_d * dx + (1.0 - alpha_d) * self._dx
        # Signal filter — cutoff rises with speed
        cutoff  = self.min_cutoff + self.beta * abs(self._dx)
        alpha   = self._alpha(freq, cutoff)
        self._x = alpha * x + (1.0 - alpha) * self._x
        self._t = t
        return self._x

    @staticmethod
    def _alpha(freq: float, cutoff: float) -> float:
        tau = 1.0 / (2.0 * math.pi * cutoff)
        te  = 1.0 / freq
        return 1.0 / (1.0 + tau / te)

    def reset(self) -> None:
        self._x = None
        self._dx = 0.0
        self._t = None


# ─────────────────────────────────────────────────────────────────────────────
# MediaPipe Tasks face landmark detector
# ─────────────────────────────────────────────────────────────────────────────
class FaceLandmarkDetector:
    MODEL_URL = (
        "https://storage.googleapis.com/mediapipe-models/face_landmarker/"
        "face_landmarker/float16/latest/face_landmarker.task"
    )

    def __init__(self) -> None:
        from mediapipe.tasks import python as mp_python
        from mediapipe.tasks.python import vision

        model_path = Path(__file__).resolve().parent / "models" / "face_landmarker.task"
        model_path.parent.mkdir(parents=True, exist_ok=True)
        if not model_path.exists():
            print("Downloading face-landmarker model (one-time, ~30 MB)…")
            urlretrieve(self.MODEL_URL, str(model_path))
            print("Download complete.")

        base_opts = mp_python.BaseOptions(model_asset_path=str(model_path))
        options   = vision.FaceLandmarkerOptions(
            base_options=base_opts,
            running_mode=vision.RunningMode.VIDEO,
            num_faces=1,
            min_face_detection_confidence=0.5,
            min_face_presence_confidence=0.5,
            min_tracking_confidence=0.5,
        )
        self._detector = vision.FaceLandmarker.create_from_options(options)

    def detect(self, frame_bgr: np.ndarray, timestamp_ms: int):
        rgb      = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        result   = self._detector.detect_for_video(mp_image, timestamp_ms)
        return result.face_landmarks[0] if result.face_landmarks else None

    def close(self) -> None:
        self._detector.close()


# ─────────────────────────────────────────────────────────────────────────────
# Head pose estimator (PnP + face ratio)
# ─────────────────────────────────────────────────────────────────────────────
_DIST_COEFFS = np.zeros((4, 1))   # assume no lens distortion (works for webcams)


class HeadPoseEstimator:
    """Estimates head pitch, yaw, roll from face landmarks via solvePnPRansac.

    Also computes the face ratio (DFR/CFR) as a secondary quality signal.
    """

    def estimate(self, landmarks, img_w: int, img_h: int) -> tuple[HeadPose, float, float]:
        """Returns (HeadPose, current_face_ratio, yaw_symmetry_index)."""
        # ── 2-D image points ──────────────────────────────────────────────
        pts_2d = np.array(
            [[landmarks[i].x * img_w, landmarks[i].y * img_h] for i in _LM_IDS],
            dtype=np.float64,
        )

        # ── Camera intrinsics (approximate; assume square pixels, no distortion) ──
        focal  = float(img_w)
        cam_mx = np.array(
            [[focal, 0, img_w / 2.0],
             [0, focal, img_h / 2.0],
             [0,     0,          1.0]],
            dtype=np.float64,
        )

        # ── solvePnPRansac ────────────────────────────────────────────────
        ok, rvec, tvec, inliers = cv2.solvePnPRansac(
            _FACE_3D,
            pts_2d,
            cam_mx,
            _DIST_COEFFS,
            iterationsCount=150,
            reprojectionError=8.0,
            confidence=0.995,
            flags=cv2.SOLVEPNP_ITERATIVE,
        )
        if not ok:
            return HeadPose(), self._face_ratio(landmarks, img_w, img_h), 0.5

        # ── Reprojection error ────────────────────────────────────────────
        proj, _ = cv2.projectPoints(_FACE_3D, rvec, tvec, cam_mx, _DIST_COEFFS)
        repr_err = float(np.mean(np.linalg.norm(pts_2d - proj.reshape(-1, 2), axis=1)))

        # ── Rotation matrix → Euler angles ───────────────────────────────
        rmat, _ = cv2.Rodrigues(rvec)
        sy = math.sqrt(rmat[0, 0] ** 2 + rmat[1, 0] ** 2)
        if sy > 1e-6:
            pitch = math.degrees(math.atan2( rmat[2, 1], rmat[2, 2]))
            yaw   = math.degrees(math.atan2(-rmat[2, 0], sy))
            roll  = math.degrees(math.atan2( rmat[1, 0], rmat[0, 0]))
        else:
            pitch = math.degrees(math.atan2(-rmat[1, 2], rmat[1, 1]))
            yaw   = math.degrees(math.atan2(-rmat[2, 0], sy))
            roll  = 0.0

        # ── Confidence: penalise high reprojection error & low inlier count ──
        n_inliers    = len(inliers) if inliers is not None else 0
        inlier_ratio = n_inliers / len(_LM_IDS)
        # Reprojection error < 4 px → full score; degrades linearly to 0 at 12 px
        repr_score = max(0.0, 1.0 - (repr_err - 4.0) / 8.0)
        confidence = inlier_ratio * repr_score

        # ── Face ratio & yaw symmetry ─────────────────────────────────────
        face_ratio  = self._face_ratio(landmarks, img_w, img_h)
        yaw_sym     = self._yaw_symmetry(landmarks, img_w, img_h)

        return (
            HeadPose(
                pitch_deg=pitch,
                yaw_deg=yaw,
                roll_deg=roll,
                reprojection_err=repr_err,
                inlier_ratio=inlier_ratio,
                confidence=confidence,
            ),
            face_ratio,
            yaw_sym,
        )

    @staticmethod
    def _face_ratio(landmarks, img_w: int, img_h: int) -> float:
        """DFR / CFR: eye_distance / eyes-to-mouth distance (scale-invariant)."""
        def px(idx):
            lm = landmarks[idx]
            return lm.x * img_w, lm.y * img_h

        el = px(_EYE_L);   er = px(_EYE_R)
        ml = px(_MOUTH_L); mr = px(_MOUTH_R)
        eye_cx   = (el[0] + er[0]) / 2.0
        eye_cy   = (el[1] + er[1]) / 2.0
        mouth_cx = (ml[0] + mr[0]) / 2.0
        mouth_cy = (ml[1] + mr[1]) / 2.0
        eye_dist       = math.hypot(er[0] - el[0], er[1] - el[1])
        eyes_mouth_dist = math.hypot(mouth_cx - eye_cx, mouth_cy - eye_cy)
        return eye_dist / max(1.0, eyes_mouth_dist)

    @staticmethod
    def _yaw_symmetry(landmarks, img_w: int, img_h: int) -> float:
        """Fraction of face width to the left of the nose.

        0.5 → face centred (neutral yaw)
        > 0.5 → turned right
        < 0.5 → turned left
        """
        nose_x   = landmarks[_NOSE].x   * img_w
        cheek_lx = landmarks[_CHEEK_L].x * img_w
        cheek_rx = landmarks[_CHEEK_R].x * img_w
        width    = max(1.0, cheek_rx - cheek_lx)
        return (nose_x - cheek_lx) / width


# ─────────────────────────────────────────────────────────────────────────────
# Single-axis classifier with hysteresis
# ─────────────────────────────────────────────────────────────────────────────
class AxisClassifier:
    """Classifies a scalar angle into GOOD / MODERATE / RISK with hysteresis.

    A new state only becomes *current* if it has been consistently pending
    for `hold_s` seconds. This prevents rapid flicker at threshold boundaries.
    """

    def __init__(
        self,
        thresholds: tuple[float, float] = (15.0, 30.0),
        hold_s: float = 4.0,
    ) -> None:
        self.t_moderate, self.t_risk = thresholds
        self.hold_s = hold_s
        self._current: str = "GOOD"
        self._pending: str = "GOOD"
        self._pending_since: float = 0.0

    def classify(self, abs_angle: float, reliable: bool, t: float) -> str:
        if not reliable:
            self._current = self._pending = "UNRELIABLE"
            self._pending_since = t
            return self._current

        if abs_angle >= self.t_risk:
            target = "RISK"
        elif abs_angle >= self.t_moderate:
            target = "MODERATE"
        else:
            target = "GOOD"

        if target == self._current:
            # Stable — reset pending timer.
            self._pending = target
            self._pending_since = t
            return self._current

        if target != self._pending:
            # Direction changed — restart hold timer.
            self._pending = target
            self._pending_since = t
            return self._current

        # Same pending long enough → commit.
        if (t - self._pending_since) >= self.hold_s:
            self._current = target
        return self._current


def _severity(s: str) -> int:
    return {"GOOD": 0, "MODERATE": 1, "RISK": 2, "UNRELIABLE": 3}.get(s, 3)


def _combine(pitch_s: str, yaw_s: str) -> str:
    if "UNRELIABLE" in (pitch_s, yaw_s):
        return "UNRELIABLE"
    return pitch_s if _severity(pitch_s) >= _severity(yaw_s) else yaw_s


ANALYSIS_WARMUP_S = 1.5
CALIBRATION_DURATION_S = 10.0
BASE_NECK_ANGLE_DEG = 90.0
CLASSIFIER_HOLD_S = 0.75
RANSAC_RED_RATIO = 0.52
RANSAC_GREEN_RATIO = 0.86


def _clamp01(x: float) -> float:
    return max(0.0, min(1.0, x))


def _lerp_color(low: tuple[int, int, int], high: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    t = _clamp01(t)
    return tuple(int(a + (b - a) * t) for a, b in zip(low, high))


def _combine_states(*states: str) -> str:
    if any(state == "UNRELIABLE" for state in states):
        return "UNRELIABLE"
    return max(states, key=_severity)


def _ransac_posture_score(inlier_ratio: float) -> float:
    return _clamp01((inlier_ratio - RANSAC_RED_RATIO) / (RANSAC_GREEN_RATIO - RANSAC_RED_RATIO))


def _ransac_posture_state(inlier_ratio: float) -> str:
    score = _ransac_posture_score(inlier_ratio)
    if score >= 0.7:
        return "GOOD"
    if score >= 0.4:
        return "MODERATE"
    return "RISK"


def _assumed_neck_angle(
    neck_pitch: float,
    baseline_pitch: float | None,
    cfr_ratio: float,
    dfr_ratio: float | None,
) -> tuple[float, float]:
    reference_pitch = baseline_pitch if baseline_pitch is not None else 0.0
    pitch_offset = abs(neck_pitch - reference_pitch)
    signed_offset = neck_pitch - reference_pitch

    if dfr_ratio and cfr_ratio > 0.0:
        ratio_delta = (cfr_ratio - dfr_ratio) / max(dfr_ratio, 1e-6)
        if ratio_delta > 0.015:
            signed_offset = -pitch_offset
        elif ratio_delta < -0.015:
            signed_offset = pitch_offset

    assumed_angle = float(np.clip(BASE_NECK_ANGLE_DEG + signed_offset, 45.0, 135.0))
    return assumed_angle, signed_offset


# ─────────────────────────────────────────────────────────────────────────────
# Colour palette & on-screen overlay
# ─────────────────────────────────────────────────────────────────────────────
_C = {
    "GOOD":        (50, 220, 60),
    "MODERATE":    (0,  190, 255),
    "RISK":        (30,  30, 240),
    "UNRELIABLE":  (130, 130, 130),
    "ANALYZING":   (0,  220, 255),
    "NO_FACE":     (130, 130, 130),
    "WHITE":       (255, 255, 255),
    "YELLOW":      (0,   220, 255),
    "GRAY":        (155, 155, 155),
    "DIM":         (90,  90,  90),
}


def _put(img, text, y, color=None, scale=0.58, x=18, thickness=1):
    cv2.putText(
        img, text, (x, y),
        cv2.FONT_HERSHEY_SIMPLEX, scale,
        color if color is not None else _C["WHITE"],
        thickness, cv2.LINE_AA,
    )


def _draw_overlay(
    frame: np.ndarray,
    reading: NeckReading,
    phone_pitch: float,
    calibrated: bool,
    calibrating: bool,
    calib_elapsed: float,
    fps: float,
    analysis_ready: bool,
    analysis_left_s: float,
) -> None:
    h, w = frame.shape[:2]
    panel_w = 390
    panel_h = 130  # Height adjusted for just the RANSAC signal

    # Cleaner side panel with more compact, high-signal information.
    overlay = frame.copy()
    cv2.rectangle(overlay, (12, 12), (panel_w, 12 + panel_h), (16, 16, 16), -1)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    y = 42

    # ── RANSAC posture signal ─────────────────────────────────────────────
    _put(frame, "RANSAC posture signal", y, _C["DIM"], 0.56, x=28); y += 18
    bar_x, bar_y, bar_w, bar_h = 28, y, panel_w - 56, 18
    cv2.rectangle(frame, (bar_x, bar_y), (bar_x + bar_w, bar_y + bar_h), (55, 55, 55), -1)
    score = reading.ransac_posture_score
    bar_color = _C["GRAY"]
    if reading.combined_state != "NO_FACE":
        if score < 0.5:
            bar_color = _lerp_color(_C["RISK"], _C["YELLOW"], score / 0.5)
        else:
            bar_color = _lerp_color(_C["YELLOW"], _C["GOOD"], (score - 0.5) / 0.5)
    cv2.rectangle(frame, (bar_x, bar_y), (bar_x + int(bar_w * score), bar_y + bar_h), bar_color, -1)
    cv2.rectangle(frame, (bar_x, bar_y), (bar_x + bar_w, bar_y + bar_h), (90, 90, 90), 1)
    y += 32
    _put(frame, "Bowed / weak tracking", y, _C["GRAY"], 0.47, x=28)
    _put(frame, "Neutral / back", y, _C["GRAY"], 0.47, x=255); y += 20
    _put(
        frame,
        f"Inlier ratio {reading.head_pose.inlier_ratio:0.2f}   [{reading.ransac_posture_state}]",
        y,
        bar_color if reading.combined_state != "NO_FACE" else _C["GRAY"],
        0.54,
        x=28,
    )


# ─────────────────────────────────────────────────────────────────────────────
# Main loop
# ─────────────────────────────────────────────────────────────────────────────
def main() -> None:
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        raise RuntimeError("Could not open webcam.")

    detector  = FaceLandmarkDetector()
    estimator = HeadPoseEstimator()

    # Independent One Euro Filters for each angle channel.
    # min_cutoff=1.0 Hz → smooth at rest; beta=0.08 → low lag on movement.
    pitch_f = OneEuroFilter(min_cutoff=1.0, beta=0.08)
    yaw_f   = OneEuroFilter(min_cutoff=1.0, beta=0.08)
    roll_f  = OneEuroFilter(min_cutoff=1.0, beta=0.04)

    # Separate classifiers per axis with doc-based thresholds
    pitch_cls = AxisClassifier(thresholds=(15.0, 30.0), hold_s=CLASSIFIER_HOLD_S)
    yaw_cls   = AxisClassifier(thresholds=(20.0, 35.0), hold_s=CLASSIFIER_HOLD_S)

    virtual_phone_pitch: float = 0.0

    # Calibration state
    baseline_pitch: float | None = None
    baseline_yaw:   float | None = None
    dfr:            float | None = None   # default face ratio

    calibrating = False
    _calib_pitch: list[float] = []
    _calib_yaw:   list[float] = []
    _calib_ratio: list[float] = []
    calib_start_s = 0.0
    analysis_start_s: float | None = None

    stats: dict[str, int] = {}
    reading = NeckReading()
    prev_t  = time.time()

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame = cv2.flip(frame, 1)          # mirror so left/right feel natural
        h, w  = frame.shape[:2]

        now_s = time.time()
        dt    = max(1e-3, now_s - prev_t)
        prev_t = now_s
        fps   = 1.0 / dt

        lms = detector.detect(frame, int(now_s * 1000))

        cur_ratio = 0.0
        yaw_sym   = 0.5

        if lms is not None:
            if analysis_start_s is None:
                analysis_start_s = now_s
            raw_pose, cur_ratio, yaw_sym = estimator.estimate(lms, w, h)
            analysis_ready = (now_s - analysis_start_s) >= ANALYSIS_WARMUP_S

            # ── One Euro filtered angles ──────────────────────────────────
            fp = pitch_f(raw_pose.pitch_deg, now_s)
            fy = yaw_f(raw_pose.yaw_deg,   now_s)
            fr = roll_f(raw_pose.roll_deg,  now_s)

            # ── Fusion: Neck Pitch = Face Pitch (cam) + Phone Pitch ───────
            neck_pitch = fp + virtual_phone_pitch
            neck_yaw   = fy   # phone yaw compensation not needed for text-neck MVP

            # ── Calibration sample collection ─────────────────────────────
            if calibrating:
                _calib_pitch.append(neck_pitch)
                _calib_yaw.append(neck_yaw)
                _calib_ratio.append(cur_ratio)
                if (now_s - calib_start_s) >= CALIBRATION_DURATION_S:
                    baseline_pitch = float(np.mean(_calib_pitch))
                    baseline_yaw   = float(np.mean(_calib_yaw))
                    dfr            = float(np.mean(_calib_ratio))
                    calibrating    = False
                    _calib_pitch.clear(); _calib_yaw.clear(); _calib_ratio.clear()

            # ── Angle deltas from baseline ────────────────────────────────
            if baseline_pitch is not None:
                signed_pitch_delta = neck_pitch - baseline_pitch
                pitch_delta = abs(signed_pitch_delta)
                yaw_delta   = abs(neck_yaw   - baseline_yaw)
            else:
                # Without calibration: treat raw angles as deltas
                signed_pitch_delta = neck_pitch
                pitch_delta = abs(neck_pitch)
                yaw_delta   = abs(neck_yaw)

            assumed_angle, assumed_offset = _assumed_neck_angle(
                neck_pitch,
                baseline_pitch,
                cur_ratio,
                dfr,
            )
            ransac_state = _ransac_posture_state(raw_pose.inlier_ratio)
            ransac_score = _ransac_posture_score(raw_pose.inlier_ratio)

            # ── Reliability gate ──────────────────────────────────────────
            reliable = (
                analysis_ready
                and raw_pose.confidence      > 0.30
                and raw_pose.reprojection_err < 12.0
                and raw_pose.inlier_ratio     > 0.55
            )

            # ── Classify both axes ────────────────────────────────────────
            p_state = pitch_cls.classify(pitch_delta, reliable, now_s)
            y_state = yaw_cls.classify(yaw_delta,   reliable, now_s)
            combined = _combine_states(p_state, y_state, ransac_state)
            if analysis_ready:
                stats[combined] = stats.get(combined, 0) + 1

            reading = NeckReading(
                neck_pitch_deg  = neck_pitch,
                neck_yaw_deg    = neck_yaw,
                pitch_delta_deg = pitch_delta,
                signed_pitch_delta_deg = signed_pitch_delta,
                yaw_delta_deg   = yaw_delta,
                pitch_state     = p_state,
                yaw_state       = y_state,
                combined_state  = combined,
                confidence      = raw_pose.confidence,
                dfr_ratio       = dfr if dfr is not None else 0.0,
                cfr_ratio       = cur_ratio,
                yaw_symmetry    = yaw_sym,
                assumed_angle_deg = assumed_angle,
                assumed_angle_offset_deg = assumed_offset,
                ransac_posture_score = ransac_score,
                ransac_posture_state = ransac_state,
                head_pose       = HeadPose(
                    pitch_deg       = fp,
                    yaw_deg         = fy,
                    roll_deg        = fr,
                    reprojection_err = raw_pose.reprojection_err,
                    inlier_ratio    = raw_pose.inlier_ratio,
                    confidence      = raw_pose.confidence,
                ),
            )
        else:
            # Face lost — reset filters so stale state doesn't persist
            pitch_f.reset(); yaw_f.reset(); roll_f.reset()
            analysis_start_s = None
            reading = NeckReading(combined_state="NO_FACE")

        calib_elapsed = (now_s - calib_start_s) if calibrating else 0.0
        analysis_left_s = 0.0
        analysis_ready = False
        if analysis_start_s is not None:
            warmup_elapsed = now_s - analysis_start_s
            analysis_ready = warmup_elapsed >= ANALYSIS_WARMUP_S
            analysis_left_s = max(0.0, ANALYSIS_WARMUP_S - warmup_elapsed)
        _draw_overlay(
            frame, reading, virtual_phone_pitch,
            calibrated=(baseline_pitch is not None),
            calibrating=calibrating,
            calib_elapsed=calib_elapsed,
            fps=fps,
            analysis_ready=analysis_ready,
            analysis_left_s=analysis_left_s,
        )

        cv2.imshow("Neck Posture Simulator", frame)
        key = cv2.waitKey(1) & 0xFF

        if key in (ord("q"), 27):
            break
        elif key == ord("w"):
            virtual_phone_pitch = min(80.0, virtual_phone_pitch + 1.5)
        elif key == ord("s"):
            virtual_phone_pitch = max(-80.0, virtual_phone_pitch - 1.5)
        elif key == ord("c") and not calibrating:
            calibrating = True
            calib_start_s = now_s
            _calib_pitch.clear(); _calib_yaw.clear(); _calib_ratio.clear()
        elif key == ord("r"):
            baseline_pitch = baseline_yaw = dfr = None
            pitch_f.reset(); yaw_f.reset(); roll_f.reset()
            pitch_cls = AxisClassifier(thresholds=(15.0, 30.0), hold_s=CLASSIFIER_HOLD_S)
            yaw_cls   = AxisClassifier(thresholds=(20.0, 35.0), hold_s=CLASSIFIER_HOLD_S)
            analysis_start_s = None
            stats.clear()

    detector.close()
    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
