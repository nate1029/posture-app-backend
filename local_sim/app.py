import os
import time
import math
import cv2
import traceback
from flask import Flask, jsonify, send_from_directory
from webcam_sim import FaceLandmarkDetector, HeadPoseEstimator, _ransac_posture_score

app = Flask(__name__, static_folder="static")

detector = None
estimator = None

def get_detector():
    global detector
    if detector is None:
        detector = FaceLandmarkDetector()
    return detector

def get_estimator():
    global estimator
    if estimator is None:
        estimator = HeadPoseEstimator()
    return estimator

def perform_posture_check(timeout_s=10.0, settle_s=1.5):
    det = get_detector()
    est = get_estimator()
    
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        return {"status": "error", "message": "Camera not accessible"}
    
    start_time = time.time()
    scores = []
    
    try:
        while True:
            now_s = time.time()
            elapsed = now_s - start_time
            
            # If we've passed the settle time and got enough valid frames
            if elapsed > settle_s and len(scores) >= 5:
                avg_score = sum(scores) / len(scores)
                if avg_score >= 0.7:
                    state = "GOOD"
                elif avg_score >= 0.4:
                    state = "MODERATE"
                else:
                    state = "RISK"
                return {"status": "success", "state": state, "score": avg_score}
                
            # If timeout is reached and we don't have enough frames
            if elapsed > timeout_s:
                return {"status": "not_found", "message": "Posture not found"}
                
            ok, frame = cap.read()
            if not ok:
                time.sleep(0.01)
                continue
                
            # Let the camera exposure auto-adjust for the first 'settle_s' seconds
            if elapsed < settle_s:
                continue

            frame = cv2.flip(frame, 1)
            h, w = frame.shape[:2]
            
            lms = det.detect(frame, int(now_s * 1000))
            if lms is not None:
                raw_pose, _, _ = est.estimate(lms, w, h)
                score = _ransac_posture_score(raw_pose.inlier_ratio)
                scores.append(score)
            
            time.sleep(0.01)
    finally:
        cap.release()

@app.route("/")
def index():
    return send_from_directory("static", "index.html")

@app.route("/check_posture")
def check_posture():
    try:
        result = perform_posture_check(10.0)
        return jsonify(result)
    except Exception as e:
        traceback.print_exc()
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == "__main__":
    print("Initializing models...")
    get_detector()
    get_estimator()
    print("Starting Flask server...")
    app.run(host="0.0.0.0", port=5000, use_reloader=False)
