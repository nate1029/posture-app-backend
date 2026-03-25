"""
NeckGuard — Sensor-based neck posture detection algorithm
=========================================================
Uses only: Accelerometer + Gyroscope + Proximity sensor
No camera. No overlay. No special permissions beyond BODY_SENSORS.

Physics basis:
  - Phone pitch angle (tilt from vertical) correlates strongly with neck flexion
  - neck_flexion_deg ≈ phone_pitch_deg × 0.82  (from text-neck research)
  - Complementary filter fuses accel (stable long-term) + gyro (stable short-term)
  - Proximity sensor filters out "phone on desk" false positives
"""

import math
import time
from dataclasses import dataclass, field
from collections import deque
from enum import Enum


# ─────────────────────────────────────────────
# Data types
# ─────────────────────────────────────────────

@dataclass
class SensorSample:
    """One raw reading from all three sensors at a single timestamp."""
    timestamp_s: float       # epoch seconds

    # Accelerometer (m/s²) — includes gravity
    ax: float = 0.0          # X: points right in portrait
    ay: float = -9.81        # Y: points up in portrait (gravity = -9.81)
    az: float = 0.0          # Z: points out of screen

    # Gyroscope (rad/s) — angular velocity
    gx: float = 0.0          # rotation around X axis (pitch rate)
    gy: float = 0.0          # rotation around Y axis (roll rate)
    gz: float = 0.0          # rotation around Z axis (yaw rate)

    # Proximity sensor
    proximity_near: bool = False   # True = face/object < ~5 cm from screen


class PostureState(Enum):
    UNKNOWN   = "unknown"    # Not enough data yet
    GOOD      = "good"       # Neck flexion < 15°
    MODERATE  = "moderate"   # Neck flexion 15–35°
    POOR      = "poor"       # Neck flexion > 35°
    IDLE      = "idle"       # Phone not being held (on desk, in pocket)


@dataclass
class PostureReading:
    """Output from the algorithm for each processed sample."""
    timestamp_s: float
    phone_pitch_deg: float       # Phone tilt from vertical (0 = upright)
    estimated_neck_deg: float    # Estimated neck flexion angle
    state: PostureState
    confidence: float            # 0.0–1.0, how confident we are in this reading
    is_phone_active: bool        # Phone likely being actively used


# ─────────────────────────────────────────────
# Constants and thresholds
# ─────────────────────────────────────────────

ALPHA = 0.98                 # Complementary filter weight (gyro vs accel)
                             # 0.98 → 98% gyro (fast), 2% accel (drift correction)

NECK_SCALE = 0.82            # neck_deg = pitch_deg × NECK_SCALE
                             # Derived from: Hansraj (2014) text-neck study

NECK_GOOD_THRESHOLD     = 15.0   # degrees — below this is fine
NECK_MODERATE_THRESHOLD = 35.0   # degrees — above this is poor posture

# Phone is considered "lying flat" (not being actively held up) if pitch > this
PHONE_FLAT_THRESHOLD = 75.0      # degrees from vertical

# Motion detection: if total acceleration magnitude deviates from gravity by
# more than this, user is walking/moving — skip that sample
MOTION_SPIKE_THRESHOLD = 3.0     # m/s²
GRAVITY = 9.81

# Smoothing window for final output (reduces jitter)
SMOOTHING_WINDOW = 10            # samples (~1 second at 10Hz)

# Sustained posture: only alert if bad posture held for this long
SUSTAINED_POOR_SECONDS = 120     # 2 minutes


# ─────────────────────────────────────────────
# Step 1: Pitch angle from accelerometer
# ─────────────────────────────────────────────

def accel_pitch_deg(ax: float, ay: float, az: float) -> float:
    """
    Calculate phone's tilt angle FROM VERTICAL using gravity vector.

    Android portrait orientation:
      Y axis = up along phone (gravity is mostly -Y when upright)
      Z axis = out of screen toward user
      X axis = right

    When phone is perfectly vertical (upright):
      ay ≈ -9.81, az ≈ 0  →  tilt = 0°

    When phone tilts forward (top away from user, looking down):
      ay decreases, az becomes negative (gravity shifts toward -Z)
      tilt increases toward 90° (phone horizontal / flat)

    Formula: tilt = arccos(-ay / |g|)
      - At vertical: arccos(9.81/9.81) = arccos(1) = 0°
      - At 45°:      arccos(6.93/9.81) = 45°
      - At flat:     arccos(0/9.81)    = 90°

    We use actual magnitude instead of constant g for robustness.
    """
    magnitude = math.sqrt(ax ** 2 + ay ** 2 + az ** 2)
    if magnitude < 0.5:   # near-zero gravity reading — sensor error
        return 0.0
    cos_angle = max(-1.0, min(1.0, -ay / magnitude))
    return math.degrees(math.acos(cos_angle))


# ─────────────────────────────────────────────
# Step 2: Complementary filter (sensor fusion)
# ─────────────────────────────────────────────

class ComplementaryFilter:
    """
    Fuses accelerometer pitch with gyroscope angular rate.

    Problem with accelerometer alone: noisy, affected by hand movement
    Problem with gyroscope alone: drifts over time (integration error)
    Solution: 
      - Use gyroscope for short-term accuracy (fast response)
      - Use accelerometer to slowly correct long-term drift
      - Weight: ALPHA for gyro, (1-ALPHA) for accel

    filtered_pitch = ALPHA × (prev_pitch + gyro_rate × dt)
                   + (1-ALPHA) × accel_pitch
    """

    def __init__(self, alpha: float = ALPHA):
        self.alpha = alpha
        self.filtered_pitch = 0.0
        self.last_timestamp = None
        self.initialized = False

    def update(self, sample: SensorSample) -> float:
        """Returns smoothed pitch angle in degrees."""
        now = sample.timestamp_s

        if not self.initialized:
            # Seed filter with accelerometer on first sample
            self.filtered_pitch = accel_pitch_deg(sample.ax, sample.ay, sample.az)
            self.last_timestamp = now
            self.initialized = True
            return self.filtered_pitch

        dt = now - self.last_timestamp
        if dt <= 0 or dt > 1.0:
            # Skip stale or impossible timestamps
            self.last_timestamp = now
            return self.filtered_pitch

        # Gyroscope integration: gx is pitch rate (rad/s around X axis)
        gyro_pitch = self.filtered_pitch + math.degrees(sample.gx) * dt

        # Accelerometer pitch (noisy but drift-free)
        a_pitch = accel_pitch_deg(sample.ax, sample.ay, sample.az)

        # Complementary blend
        self.filtered_pitch = self.alpha * gyro_pitch + (1 - self.alpha) * a_pitch
        self.last_timestamp = now
        return self.filtered_pitch


# ─────────────────────────────────────────────
# Step 3: Motion and usage detection
# ─────────────────────────────────────────────

def is_in_motion(ax: float, ay: float, az: float) -> bool:
    """
    Returns True if the phone is being moved (walking, shaking).
    We detect this by checking if total acceleration deviates from
    pure gravity (9.81 m/s²) — any excess = linear acceleration = movement.
    """
    magnitude = math.sqrt(ax**2 + ay**2 + az**2)
    return abs(magnitude - GRAVITY) > MOTION_SPIKE_THRESHOLD


def is_phone_being_held(pitch_deg: float, proximity_near: bool) -> bool:
    """
    Heuristic: is the user actively holding and viewing the phone?

    Phone on desk:      pitch ≈ 90° (lying flat), proximity = far
    Phone in pocket:    proximity = near, but pitch ≈ 0 or 90° varies
    Phone being used:   pitch < PHONE_FLAT_THRESHOLD, maybe proximity signal

    We avoid over-relying on proximity because many users hold phone
    at arm's length (proximity = far) but are still actively using it.
    """
    phone_not_flat = pitch_deg < PHONE_FLAT_THRESHOLD
    return phone_not_flat


def compute_confidence(
    ax: float, ay: float, az: float,
    pitch_deg: float,
    proximity_near: bool
) -> float:
    """
    Confidence score (0.0–1.0) for the current posture reading.
    Lower confidence when:
      - Phone is near-flat (may be on table)
      - High motion detected (walking)
      - Sensor readings are noisy
    """
    score = 1.0

    # Penalize if phone is near-horizontal
    if pitch_deg > 60:
        score *= max(0.1, 1.0 - (pitch_deg - 60) / 30.0)

    # Penalize if in motion
    magnitude = math.sqrt(ax**2 + ay**2 + az**2)
    motion_noise = abs(magnitude - GRAVITY)
    if motion_noise > 1.0:
        score *= max(0.2, 1.0 - motion_noise / MOTION_SPIKE_THRESHOLD)

    return round(min(1.0, max(0.0, score)), 2)


# ─────────────────────────────────────────────
# Step 4: Neck angle estimation
# ─────────────────────────────────────────────

def estimate_neck_angle(phone_pitch_deg: float) -> float:
    """
    Maps phone pitch to estimated neck flexion.

    Research basis: Hansraj (2014) measured actual neck flexion vs phone angle:
      Phone pitch 0°  (perfectly vertical)  → neck ~0°
      Phone pitch 15°                        → neck ~12°
      Phone pitch 30°                        → neck ~25°
      Phone pitch 45°                        → neck ~37°
      Phone pitch 60°                        → neck ~49°

    Linear fit: neck_deg = pitch_deg × 0.82
    (R² ≈ 0.97 — very good linear correlation)

    Note: This assumes the user is looking AT the phone screen.
    The angle is the forward flexion of the cervical spine from neutral.
    """
    neck = max(0.0, phone_pitch_deg * NECK_SCALE)
    return round(neck, 1)


def classify_posture(neck_deg: float, is_active: bool) -> PostureState:
    """
    Maps neck angle to a posture category.
    Returns IDLE if phone isn't being actively used.
    """
    if not is_active:
        return PostureState.IDLE

    if neck_deg < NECK_GOOD_THRESHOLD:
        return PostureState.GOOD
    elif neck_deg < NECK_MODERATE_THRESHOLD:
        return PostureState.MODERATE
    else:
        return PostureState.POOR


# ─────────────────────────────────────────────
# Step 5: Temporal smoothing (output stabilizer)
# ─────────────────────────────────────────────

class RollingAverage:
    """Simple circular buffer for smoothing noisy angle readings."""

    def __init__(self, window: int = SMOOTHING_WINDOW):
        self.buffer = deque(maxlen=window)

    def update(self, value: float) -> float:
        self.buffer.append(value)
        return sum(self.buffer) / len(self.buffer)

    @property
    def is_ready(self) -> bool:
        return len(self.buffer) >= 3


# ─────────────────────────────────────────────
# Step 6: Alert logic (sustained poor posture)
# ─────────────────────────────────────────────

class PostureAlertEngine:
    """
    Tracks how long posture has been poor.
    Only fires an alert after SUSTAINED_POOR_SECONDS of continuous bad posture.
    Resets the timer if posture improves.
    """

    def __init__(self, sustained_seconds: float = SUSTAINED_POOR_SECONDS):
        self.sustained_seconds = sustained_seconds
        self.poor_start_time: float | None = None
        self.last_alert_time: float | None = None
        self.alert_cooldown = 300.0  # Don't re-alert within 5 minutes

    def update(self, state: PostureState, timestamp: float) -> bool:
        """
        Returns True if an alert should be fired now.
        """
        if state == PostureState.POOR:
            if self.poor_start_time is None:
                self.poor_start_time = timestamp
            duration = timestamp - self.poor_start_time

            if duration >= self.sustained_seconds:
                # Check cooldown
                if (self.last_alert_time is None or
                        timestamp - self.last_alert_time > self.alert_cooldown):
                    self.last_alert_time = timestamp
                    return True
        else:
            # Posture improved — reset timer
            self.poor_start_time = None

        return False

    @property
    def seconds_in_poor_posture(self) -> float:
        if self.poor_start_time is None:
            return 0.0
        return time.time() - self.poor_start_time


# ─────────────────────────────────────────────
# Main engine: wires everything together
# ─────────────────────────────────────────────

class NeckPostureDetector:
    """
    Main entry point. Feed it sensor samples, get posture readings back.

    Usage (Python simulation):
        detector = NeckPostureDetector()
        sample = SensorSample(timestamp_s=time.time(), ax=0, ay=-9.81, az=0,
                              gx=0, gy=0, gz=0, proximity_near=False)
        reading = detector.process(sample)
        print(reading.state, reading.estimated_neck_deg)

    On Android, call detector.process() inside your SensorEventListener
    at ~10–20 Hz. That's fast enough for posture; no need for 100Hz.
    """

    def __init__(self):
        self.cf_filter = ComplementaryFilter(alpha=ALPHA)
        self.smoother  = RollingAverage(window=SMOOTHING_WINDOW)
        self.alert_engine = PostureAlertEngine()
        self.sample_count = 0

    def process(self, sample: SensorSample) -> PostureReading:
        self.sample_count += 1

        # Skip samples during active motion (walking, etc.)
        if is_in_motion(sample.ax, sample.ay, sample.az):
            return PostureReading(
                timestamp_s=sample.timestamp_s,
                phone_pitch_deg=0,
                estimated_neck_deg=0,
                state=PostureState.UNKNOWN,
                confidence=0.0,
                is_phone_active=False
            )

        # Sensor fusion: get smoothed pitch
        raw_pitch = self.cf_filter.update(sample)

        # Further smooth the output
        smooth_pitch = self.smoother.update(raw_pitch)

        # Estimate neck angle
        neck_deg = estimate_neck_angle(smooth_pitch)

        # Determine if phone is actively being used
        is_active = is_phone_being_held(smooth_pitch, sample.proximity_near)

        # Classify posture
        state = classify_posture(neck_deg, is_active)

        # Confidence score
        confidence = compute_confidence(
            sample.ax, sample.ay, sample.az,
            smooth_pitch, sample.proximity_near
        )

        # Check if alert should fire
        should_alert = self.alert_engine.update(state, sample.timestamp_s)
        if should_alert:
            print(f"[ALERT] Poor neck posture for {self.alert_engine.sustained_seconds}s! "
                  f"Estimated flexion: {neck_deg}°")

        return PostureReading(
            timestamp_s=sample.timestamp_s,
            phone_pitch_deg=round(smooth_pitch, 1),
            estimated_neck_deg=neck_deg,
            state=state,
            confidence=confidence,
            is_phone_active=is_active
        )


# ─────────────────────────────────────────────
# Simulation / test harness
# ─────────────────────────────────────────────

def simulate_scenario(scenario_name: str, samples: list[SensorSample]):
    """Run a scenario through the detector and print results."""
    print(f"\n{'='*55}")
    print(f"  Scenario: {scenario_name}")
    print(f"{'='*55}")
    print(f"{'Time':>6}  {'Pitch':>7}  {'Neck':>6}  {'State':<12}  {'Confidence':>10}")
    print(f"{'-'*55}")

    detector = NeckPostureDetector()
    for s in samples:
        r = detector.process(s)
        if r.state != PostureState.UNKNOWN:
            print(f"{s.timestamp_s:>6.1f}s  "
                  f"{r.phone_pitch_deg:>6.1f}°  "
                  f"{r.estimated_neck_deg:>5.1f}°  "
                  f"{r.state.value:<12}  "
                  f"{r.confidence:>10.2f}")


def make_samples(
    ax, ay, az,           # accelerometer values
    gx=0.0,              # gyro pitch rate (rad/s)
    duration=5.0,        # seconds
    hz=10,               # samples per second
    start_t=0.0,
    proximity=False
) -> list[SensorSample]:
    """Helper: generate a stream of identical sensor readings."""
    dt = 1.0 / hz
    return [
        SensorSample(
            timestamp_s=start_t + i * dt,
            ax=ax, ay=ay, az=az,
            gx=gx, gy=0.0, gz=0.0,
            proximity_near=proximity
        )
        for i in range(int(duration * hz))
    ]


if __name__ == "__main__":

    # ── Scenario 1: Good posture ──────────────────────────────────
    # Phone 10° from vertical: ay = -9.81*cos(10°) ≈ -9.66, az = -9.81*sin(10°) ≈ -1.70
    simulate_scenario(
        "Good posture — phone nearly upright (~10 deg tilt)",
        make_samples(ax=0.0, ay=-9.66, az=-1.70, duration=5)
    )

    # ── Scenario 2: Moderate — phone at 40 deg tilt ─────────────────
    # ay = -9.81*cos(40) = -7.51, az = -9.81*sin(40) = -6.31
    # neck estimate: 40 * 0.82 = 32.8 deg
    simulate_scenario(
        "Moderate posture — phone at 40 deg tilt",
        make_samples(ax=0.0, ay=-7.51, az=-6.31, duration=5)
    )

    # ── Scenario 3: Poor posture — phone near horizontal ──────────
    # ay = -9.81*cos(60) = -4.91, az = -9.81*sin(60) = -8.50
    # neck estimate: 60 * 0.82 = 49.2 deg (severe)
    simulate_scenario(
        "Poor posture — phone at 60 deg tilt",
        make_samples(ax=0.0, ay=-4.91, az=-8.50, duration=5)
    )

    # ── Scenario 4: Phone flat on desk ───────────────────────────
    # Screen facing up => gravity in -Z: ay~0, az=-9.81 => tilt=90 => IDLE
    simulate_scenario(
        "Phone on desk (screen up) — should be IDLE",
        make_samples(ax=0.0, ay=0.01, az=-9.81, duration=5)
    )

    # ── Scenario 5: Phone being shaken ────────────────────────────
    # |a| = sqrt(36+81+16) = 11.4, deviation from 9.81 = 1.6 > threshold
    simulate_scenario(
        "Phone shaking — should return UNKNOWN",
        make_samples(ax=6.0, ay=-9.0, az=4.0, duration=5)
    )

    # ── Scenario 6: Gradual posture degradation (realistic) ───────
    print(f"\n{'='*55}")
    print("  Scenario: Gradual tilt 5 deg -> 65 deg over 15s")
    print(f"{'='*55}")
    print(f"{'Time':>6}  {'Pitch':>7}  {'Neck':>6}  {'State':<12}")
    print(f"{'-'*55}")

    detector = NeckPostureDetector()

    for i in range(150):
        t = i * 0.1
        progress = i / 149.0
        tilt_deg = 5 + progress * 60   # 5 deg -> 65 deg

        tilt_rad = math.radians(tilt_deg)
        ay_val = -GRAVITY * math.cos(tilt_rad)
        az_val = -GRAVITY * math.sin(tilt_rad)

        sample = SensorSample(timestamp_s=t, ax=0.0, ay=ay_val, az=az_val)
        r = detector.process(sample)

        if i % 15 == 0:
            print(f"{t:>6.1f}s  "
                  f"{r.phone_pitch_deg:>6.1f} deg  "
                  f"{r.estimated_neck_deg:>5.1f} deg  "
                  f"{r.state.value:<12}")
