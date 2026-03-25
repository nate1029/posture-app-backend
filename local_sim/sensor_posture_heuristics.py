import math
import random
import time

def calculate_tilt_angle(accel_x, accel_y, accel_z):
    """
    Calculates the phone's tilt angle (pitch) relative to the vertical plane.
    Assumes standard Android coordinate system:
    - Y is vertical (up/down)
    - Z is out of the screen (front/back)
    
    When phone is upright (portrait): Y is ~9.81, Z is ~0
    When phone is flat on table: Y is ~0, Z is ~9.81
    """
    magnitude = math.sqrt(accel_x**2 + accel_y**2 + accel_z**2)
    if magnitude == 0:
        return 0

    # Normalize vectors
    norm_y = accel_y / magnitude
    norm_z = accel_z / magnitude

    # Calculate angle in degrees
    # atan2(z, y) returns 0 when perfectly upright, and 90 when flat.
    tilt_rad = math.atan2(norm_z, norm_y)
    tilt_deg = math.degrees(tilt_rad)

    return abs(tilt_deg)


def evaluate_posture(accel_x, accel_y, accel_z, proximity_cm, screen_on_time_minutes):
    """
    Evaluates neck posture using phone hardware sensors.
    
    Parameters:
    - accel_x, accel_y, accel_z: Accelerometer readings (m/s^2)
    - proximity_cm: Distance from screen to a physical object (cm)
    - screen_on_time_minutes: How long the user has been continuously looking at the screen
    """
    
    # 1. Calculate the crucial metric: Phone Tilt Angle
    tilt_angle = calculate_tilt_angle(accel_x, accel_y, accel_z)
    
    # 2. Base Heuristics Setup
    # The assumption: People instinctively hold their phones perpendicular to their line of sight.
    # Therefore, the angle of the phone strongly correlates with the angle of the neck and head.
    status = "Good"
    severity = 0 # 0=Great, 1=Warning, 2=Bad, 3=Critical
    feedback = "Posture looks healthy."

    # 3. Analyze the Tilt Angle
    if tilt_angle < 20: # 0 to 20 degrees
        status = "Excellent"
        feedback = "Phone is eye-level. Great job!"
        severity = 0
        
    elif 20 <= tilt_angle < 45: 
        status = "Fair"
        feedback = "You are looking down slightly. Try raising the phone."
        severity = 1
        
    elif 45 <= tilt_angle < 70:
        status = "Poor"
        feedback = "Text Neck detected! You are hunching over. Raise your phone."
        severity = 2
        
    else: # 70 to 90+ degrees (Phone is almost flat)
        status = "Severe"
        feedback = "Dangerous neck angle! Your spine is under extreme pressure."
        severity = 3

    # 4. Apply Sensor Multipliers (Proximity & Time)
    
    # Proximity Check: If the phone is less than 5cm away, they are likely straining their neck 
    # forward to read, regardless of the tilt angle.
    if proximity_cm < 5.0 and severity < 3:
        status = f"{status} (Too close to face!)"
        feedback = "Screen is too close! This causes forward head posture."
        severity += 1
        
    # Time Penalty: Being in a "Fair" posture for 2 minutes is fine. 
    # Being in a "Fair" posture for 40 minutes becomes "Bad".
    if screen_on_time_minutes > 30 and severity > 0:
        feedback += f" Also, you've been in this position for {int(screen_on_time_minutes)} mins. Take a break!"
        # We increase the severity purely due to time-under-tension fatigue
        if severity < 3:
            severity += 1

    return {
        "calculated_tilt_degrees": round(tilt_angle, 2),
        "posture_status": status,
        "severity": severity,
        "feedback_message": feedback,
    }


def simulate_sensor_stream():
    """ Runs a quick simulation of different user holding positions """
    
    print("🚀 Starting Phone Sensor Posture Heuristic Simulation...\n")
    
    scenarios = [
        {"name": "Walking, holding phone eye-level", "ax": 0, "ay": 9.5, "az": 1.0, "prox": 30.0, "mins": 5},
        {"name": "Sitting on couch, slight hunch", "ax": 0, "ay": 7.0, "az": 7.0, "prox": 25.0, "mins": 15},
        {"name": "Laying in bed, holding phone directly overhead", "ax": 0, "ay": 9.8, "az": 0.0, "prox": 20.0, "mins": 45}, # Assuming they hold it upside down or flat above
        {"name": "Severe Text Neck, looking straight down at phone", "ax": 0, "ay": 1.0, "az": 9.5, "prox": 15.0, "mins": 35},
        {"name": "Squinting closely at phone", "ax": 0, "ay": 8.0, "az": 5.0, "prox": 3.0, "mins": 10},
    ]
    
    for scene in scenarios:
        print(f"--- Scenario: {scene['name']} ---")
        result = evaluate_posture(scene['ax'], scene['ay'], scene['az'], scene['prox'], scene['mins'])
        
        print(f"Angle: {result['calculated_tilt_degrees']}°")
        print(f"Status: {result['posture_status']}")
        print(f"Advice: {result['feedback_message']}")
        print(f"Severity: {result['severity']}")
        print("-" * 40 + "\n")
        time.sleep(1)


if __name__ == "__main__":
    simulate_sensor_stream()
