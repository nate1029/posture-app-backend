# MVP Test Matrix and Initial Tuning

This document captures the MVP validation matrix for hybrid posture scoring (`headPitch - phonePitch`) and the first tuning pass.

## Threshold Profile (v0)

- Good: delta <= 8 deg
- Warning: 8-15 deg sustained for >= 5s
- Bad: > 15 deg sustained for >= 5s
- Alert cooldown: 25s
- Motion gate: mark unreliable when linear-acceleration EMA > 1.6
- Orientation gate: mark unreliable when abs(phonePitch) >= 85 deg
- Head confidence gate: mark unreliable when confidence < 0.25

## Scenario Matrix

| ID | Scenario | Inputs | Expected | Result (v0) |
|---|---|---|---|---|
| S1 | Neutral posture, phone eye-level | head 4-8 deg, phone 2-6 deg | Mostly GOOD | PASS |
| S2 | Mild forward head | head 15-18 deg, phone 4-7 deg | WARNING after 5s | PASS |
| S3 | Strong forward head | head 25-35 deg, phone 5-10 deg | BAD after 5s | PASS |
| S4 | Phone lowered to chest, neck still neutral | head 18-24 deg, phone 14-20 deg | GOOD/WARNING-low | PASS |
| S5 | Portrait to landscape rotation | same posture, roll change up to 40 deg | No false BAD spike | PASS |
| S6 | Dim light with partial face | low confidence periods | UNRELIABLE, no alerts | PASS |
| S7 | Walking while viewing phone | motion EMA > 1.6 | UNRELIABLE | PASS |
| S8 | Reclined/lying usage | phone pitch near +-90 deg | UNRELIABLE | PASS |

## Tuning Notes

- Reduced false positives in S4 by using relative angle instead of raw head pitch.
- 5s hold time avoids rapid state oscillation at threshold boundaries.
- 25s cooldown prevents repetitive alerts during a sustained bad posture period.
- Next tuning step after your clinical documentation: calibrate warning/bad cutoffs per user segment.

## Execution Notes

- Pure logic tests are implemented in unit tests:
  - `app/src/test/java/com/didi/neckposture/domain/PostureClassifierTest.kt`
  - `app/src/test/java/com/didi/neckposture/fusion/PostureFusionEngineTest.kt`
- Full device validation still required on Android hardware (front camera + IMU).
