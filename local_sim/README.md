# Local Neck Posture Simulator

This is a desktop test harness for the MVP posture logic.

It uses:
- Webcam face landmarks/head pose (MediaPipe + OpenCV)
- A **virtual phone pitch** controlled from keyboard
- The same fusion concept as Android MVP: `relative = headPitch - phonePitch`
- EMA smoothing, reliability gating, and posture classification

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r local_sim/requirements.txt
```

## Run

```bash
python local_sim/webcam_sim.py
```

On first run, the script downloads a face-landmarker model into `local_sim/models/`.

## Controls

- `W`: increase virtual phone pitch
- `S`: decrease virtual phone pitch
- `C`: calibrate baseline for 10 seconds (hold neutral posture)
- `R`: reset calibration
- `Q` or `Esc`: quit

## Why this helps before Android testing

- Lets you validate fusion behavior and thresholds quickly.
- Lets you test edge case: face seems tilted because phone is lowered, then compensate by changing virtual phone pitch.
- Lets you iterate classifier thresholds and hysteresis without deploying to device.
