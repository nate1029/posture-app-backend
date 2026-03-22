import cv2
import mediapipe as mp
import numpy as np
import time
import math
from pathlib import Path
from urllib.request import urlretrieve
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

class EMAFilter:
    """Simple Exponential Moving Average filter to smooth out jitter."""
    def __init__(self, alpha=0.15):
        self.alpha = alpha
        self.val = None
        
    def __call__(self, x):
        if self.val is None:
            self.val = x
        else:
            self.val = self.alpha * x + (1.0 - self.alpha) * self.val
        return self.val

def calculate_tilt_angle(p1, p2, w, h):
    dx = (p2.x - p1.x) * w
    dy = (p2.y - p1.y) * h
    return math.degrees(math.atan2(dy, dx))

class ModelManager:
    FACE_URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
    POSE_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
    
    def __init__(self):
        models_dir = Path(__file__).resolve().parent / "models"
        models_dir.mkdir(parents=True, exist_ok=True)
        
        self.face_path = models_dir / "face_landmarker.task"
        if not self.face_path.exists():
            print("Downloading FaceLandmarker... (~30MB)")
            urlretrieve(self.FACE_URL, str(self.face_path))
            
        self.pose_path = models_dir / "pose_landmarker_lite.task"
        if not self.pose_path.exists():
            print("Downloading PoseLandmarker... (~5MB)")
            urlretrieve(self.POSE_URL, str(self.pose_path))

        face_opts = vision.FaceLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=str(self.face_path)),
            running_mode=vision.RunningMode.VIDEO,
            num_faces=1
        )
        self.face_detector = vision.FaceLandmarker.create_from_options(face_opts)

        pose_opts = vision.PoseLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=str(self.pose_path)),
            running_mode=vision.RunningMode.VIDEO,
            num_poses=1
        )
        self.pose_detector = vision.PoseLandmarker.create_from_options(pose_opts)
        
    def close(self):
        self.face_detector.close()
        self.pose_detector.close()


def main():
    print("Loading MediaPipe Tasks models...")
    models = ModelManager()
    cap = cv2.VideoCapture(0)
    
    # Autonomous Calibration State System
    baseline_acquired = False
    baselines = {}
    history = {k: [] for k in ['vertical_ratio', 'nose_shoulder', 'eye_tilt', 'eye_dist']}
    
    frames_present = 0      
    frames_missing = 0      
    
    CALIB_FRAMES_NEEDED = 45 
    MISSING_FRAMES_RESET = 90
    
    f_vr = EMAFilter()
    f_ns = EMAFilter()
    f_tilt = EMAFilter()
    f_dist = EMAFilter()

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break
            
        frame = cv2.flip(frame, 1)
        h, w, _ = frame.shape
        
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
        timestamp_ms = int(time.time() * 1000)
        
        # Run both task networks
        face_res = models.face_detector.detect_for_video(mp_image, timestamp_ms)
        pose_res = models.pose_detector.detect_for_video(mp_image, timestamp_ms)
        
        flm = face_res.face_landmarks[0] if face_res.face_landmarks else None
        plm = pose_res.pose_landmarks[0] if pose_res.pose_landmarks else None
        
        if flm and plm:
            frames_missing = 0 
            
            # Signal 1: Vertical Ratio
            forehead_y = flm[10].y * h
            nose_y = flm[1].y * h
            chin_y = flm[152].y * h
            vertical_ratio = (chin_y - nose_y) / max(1.0, (nose_y - forehead_y))
            smooth_vr = f_vr(vertical_ratio)
            
            # Signal 2: Nose-Shoulder Distance
            l_vis = plm[11].visibility if hasattr(plm[11], 'visibility') else 1.0
            r_vis = plm[12].visibility if hasattr(plm[12], 'visibility') else 1.0
            smooth_ns = None
            if l_vis > 0.4 and r_vis > 0.4:
                mid_shoulder_y = ((plm[11].y + plm[12].y) / 2.0) * h
                smooth_ns = f_ns(mid_shoulder_y - (plm[0].y * h))
            
            # Signal 3 & 4: Tilt and Zoom
            smooth_tilt = f_tilt(calculate_tilt_angle(flm[33], flm[263], w, h))
            smooth_dist = f_dist(math.hypot((flm[263].x - flm[33].x) * w, (flm[263].y - flm[33].y) * h))
            
            # ====================================================
            # AUTONOMOUS CALIBRATION
            # ====================================================
            if not baseline_acquired:
                frames_present += 1
                
                history['vertical_ratio'].append(smooth_vr)
                history['eye_tilt'].append(smooth_tilt)
                history['eye_dist'].append(smooth_dist)
                if smooth_ns is not None:
                    history['nose_shoulder'].append(smooth_ns)
                    
                progress = int((frames_present / float(CALIB_FRAMES_NEEDED)) * 100)
                cv2.putText(frame, f"AUTONOMOUS CALIBRATION: {progress}%", (30, 80), 
                            cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
                cv2.putText(frame, "Please sit in your normal upright position.", (30, 110), 
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 1)
                
                if frames_present >= CALIB_FRAMES_NEEDED:
                    baseline_acquired = True
                    baselines['vertical_ratio'] = np.mean(history['vertical_ratio'])
                    baselines['eye_tilt'] = np.mean(history['eye_tilt'])
                    baselines['eye_dist'] = np.mean(history['eye_dist'])
                    if len(history['nose_shoulder']) > 15:
                        baselines['nose_shoulder'] = np.mean(history['nose_shoulder'])
                    history = {k: [] for k in history} 
                    
            # ====================================================
            # POSTURE ANALYSIS
            # ====================================================
            else: 
                vr_delta = smooth_vr - baselines['vertical_ratio']
                tilt_delta = abs(smooth_tilt - baselines.get('eye_tilt', 0.0))
                size_ratio = smooth_dist / max(1.0, baselines['eye_dist'])
                
                ns_delta = 0
                if smooth_ns is not None and 'nose_shoulder' in baselines:
                    ns_delta = smooth_ns - baselines['nose_shoulder']
                
                alerts = []
                
                if abs(smooth_tilt) > 12.0:
                    alerts.append("Head Tilted Sideways")
                    
                if vr_delta < -0.15:
                    alerts.append("Looking Down (Neck Bent)")
                elif vr_delta < -0.08:
                    alerts.append("Looking Down slightly")
                    
                if ns_delta < -25:
                    alerts.append("Slouching (Dropped Neck)!")
                    
                if size_ratio > 1.15:
                    alerts.append("Leaning Too Close (Turtle Neck)")
                    
                overall_status = "GOOD"
                color = (50, 220, 60)
                if len(alerts) >= 2 or any("!" in a or "Neck Bent" in a for a in alerts):
                    overall_status = "RISK"
                    color = (30, 30, 240)
                elif len(alerts) == 1:
                    overall_status = "MODERATE"
                    color = (0, 220, 255)
                    
                cv2.rectangle(frame, (10, 10), (w - 10, 150), (20, 20, 20), -1)
                cv2.putText(frame, f"POSTURE: {overall_status}", (25, 50), cv2.FONT_HERSHEY_SIMPLEX, 1, color, 3)
                
                y_offset = 80
                if not alerts:
                    cv2.putText(frame, "Perfect posture. Tracking active.", (25, y_offset), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 1)
                else:
                    for a in alerts:
                        cv2.putText(frame, f"- {a}", (25, y_offset), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)
                        y_offset += 25
                        
                cv2.putText(frame, f"VR Delta: {vr_delta:+.2f}", (w - 180, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255,255,255), 1)
                cv2.putText(frame, f"NS Delta: {ns_delta:+.0f}px", (w - 180, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255,255,255), 1)
                cv2.putText(frame, f"Size: {size_ratio:.2f}x", (w - 180, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255,255,255), 1)

        else:
            frames_missing += 1
            if frames_missing > MISSING_FRAMES_RESET:
                if baseline_acquired:
                    print("User left camera. Resetting baselines for next session.")
                baseline_acquired = False
                frames_present = 0
                history = {k: [] for k in history}
                f_vr = EMAFilter()  
                f_ns = EMAFilter()
                f_tilt = EMAFilter()
                f_dist = EMAFilter()
                
            cv2.putText(frame, "No person detected. Waiting...", (30, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (100, 100, 100), 2)
            
        cv2.imshow('Autonomous Posture Tracker', frame)
        if cv2.waitKey(1) & 0xFF in (ord('q'), 27):
            break
            
    models.close()
    cap.release()
    cv2.destroyAllWindows()

if __name__ == '__main__':
    main()
