# PostureGuard: Android App Architecture & Working Model

## 1. Product Overview
PostureGuard is an advanced, privacy-first Android application designed to detect and correct "Text Neck" and forward head posture. Because modern Android policies (Android 9+) strictly forbid silent background camera access, the app uses an innovative dual-engine architecture: an always-on **Hardware Sensor Engine**, and a periodic **3D Camera Micro-Widget**.

## 2. Core User Journey
1. **Onboarding**: The user installs the app and grants two key permissions: `BODY_SENSORS` (for the accelerometer/gyroscope) and `SYSTEM_ALERT_WINDOW` (Draw over other apps).
2. **Background Tracking**: The app runs silently in the background using virtually zero battery. It relies purely on the phone's physical tilt and proximity to guess the user's posture.
3. **The Nudge (Sensor Based)**: If the user holds their phone at a severe downward angle (Text Neck) or pulls it less than 5cm from their face for more than 2 sustained minutes, the phone gives a gentle vibration or push notification to correct their posture.
4. **The Audit (Camera Based)**: After 30 minutes of continuous screen-on time, a tiny, friendly "Posture Bubble" appears on the edge of the screen. This grants the app foreground status, allowing it to secretly and securely initialize the front camera for exactly 2 seconds. It runs a deep 3D physical audit of their true neck angle, flashes Green/Red for feedback, and then disappears.

---

## 3. The Dual-Engine Architecture

### Engine A: The "NeckGuard" Sensor Heuristic (Always-On)
This engine runs continuously in the background using minimal battery. Since users instinctively hold their phones perpendicular to their line of sight, the phone's physical pitch strongly correlates to the neck's flexion.

* **Sensor Fusion (Complementary Filter):** It mathematically fuses the Gyroscope (for fast, smooth movement tracking) and the Accelerometer (for absolute gravity vectors).
* **The Physics Mapping:** It applies a biological multiplier derived from spine research (`neck_flexion = phone_pitch * 0.82`).
* **Blindspot Solution (Proximity Sensor):** If the user is holding the phone upright (good angle) but is squishing their face 3 inches away from the screen, the proximity sensor overrides the tilt data and flags the posture as `POOR` (Forward Head Posture).
* **Motion & Idle Rejection:** If the user is walking (detected via acceleration spikes) or the phone is laid flat on a desk (> 75° pitch), the engine pauses tracking to avoid false alarms.

### Engine B: The 3D MediaPipe Physics (Periodic Audio/Visual)
This is the heavy-duty machine learning camera pipeline. It is too battery-intensive to run 24/7, and operating system privacy rules prevent it from running silently.

* **The Workaround:** It utilizes the `SYSTEM_ALERT_WINDOW` permission to draw a floating chat-head style widget over other apps (e.g., while the user is scrolling Instagram). 
* **The Execution:** Because a widget is actively visible on the screen, Android considers the app to be in the "Foreground", legally granting it camera access for 2 seconds.
* **The Math:** It uses MediaPipe's Facial Transformation Matrix to calculate the pitch of the user's face relative to the camera lens. It then offsets this by the phone's current Gyroscope angle to calculate the **True Universal Neck Angle**, independently of how the phone is being held.

---

## 4. Posture Classification Thresholds
Both engines funnel their data into a unified classification system:
* 🟢 **GOOD:** Neck flexion **< 15°**. (Safe, no strain).
* 🟡 **MODERATE:** Neck flexion **15° – 35°**. (Slight bend, fatigue over long periods).
* 🔴 **POOR (BAD):** Neck flexion **> 35°**. (Critical strain, ~40 lbs of pressure on the cervical spine).

## 5. Alert Protocol
The app respects the user's attention. Instead of buzzing every 5 seconds they look down:
1. It runs a silent "Sustained Poor Posture" timer.
2. If the user stays in the **POOR** zone continuously for **120 seconds** without readjusting, it fires a native system alert.
3. If the user corrects their posture (moves into GOOD or MODERATE) even for a second, the 120-second timer resets.
