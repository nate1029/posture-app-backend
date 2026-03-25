import { FaceLandmarker, FilesetResolver } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3";

// DOM Setup
const startBtn = document.getElementById('start-btn');
const setupScreen = document.getElementById('setup-screen');
const appScreen = document.getElementById('app-screen');
const video = document.getElementById('webcam');
const canvas = document.getElementById('output_canvas');
const ctx = canvas.getContext('2d');

const btn3d = document.getElementById('btn-3d');
const btn2d = document.getElementById('btn-2d');
const v3d = document.getElementById('view-3d');
const v2d = document.getElementById('view-2d');

const btnExperiment = document.getElementById('btn-experiment');
const experimentScreen = document.getElementById('experiment-screen');
const btnBackSetup = document.getElementById('btn-back-setup');

// Engine State
let currentMode = '3D';
let faceLandmarker = null;
let lastVideoTime = -1;
let gyroPitch = 90;

let baselines = { locked: false, vr: [], size: [], gyro: 90 };

window.setMode = (mode) => {
    currentMode = mode;
    btn3d.classList.toggle('active', mode === '3D');
    btn2d.classList.toggle('active', mode === '2D');
    v3d.style.display = mode === '3D' ? 'block' : 'none';
    v2d.style.display = mode === '2D' ? 'block' : 'none';
};

function updateStatus(text, type, alerts = []) {
    const banner = document.getElementById('status-banner');
    banner.innerText = text;
    banner.className = `status-banner status-${type}`;
    
    const alertBox = document.getElementById('alerts-list');
    alertBox.innerHTML = alerts.map(a => `• ${a}`).join('<br>');
}

startBtn.addEventListener('click', async () => {
    document.getElementById("loading-msg").innerText = "Requesting Auth...";
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
            if (await DeviceOrientationEvent.requestPermission() === 'granted') enableGyro();
        } catch(e) { console.warn(e); }
    } else enableGyro(); 

    document.getElementById("loading-msg").innerText = "Activating Camera...";
    await startCamera();

    document.getElementById("loading-msg").innerText = "Downloading AI (Wait)...";
    setupScreen.classList.remove('active');
    appScreen.classList.add('active');
    
    await initMediaPipe();
    requestAnimationFrame(renderLoop);
});

if (btnExperiment) {
    btnExperiment.addEventListener('click', async () => {
        const idInput = document.getElementById('experiment-id-input');
        const errormsg = document.getElementById('experiment-error');
        
        if (idInput && idInput.value !== '6754') {
            errormsg.style.display = 'block';
            setTimeout(() => errormsg.style.display = 'none', 2000);
            return;
        }
        
        if(idInput) idInput.value = ''; // clear upon success

        setupScreen.classList.remove('active');
        experimentScreen.classList.add('active');

        if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
            try {
                if (await DeviceOrientationEvent.requestPermission() === 'granted') enableExperimentGyro();
            } catch(e) { console.warn(e); }
        } else enableExperimentGyro();
    });
}

if (btnBackSetup) {
    btnBackSetup.addEventListener('click', () => {
        experimentScreen.classList.remove('active');
        setupScreen.classList.add('active');
    });
}

let expGyroEnabled = false;
let pitchBuffer = [];
let poorStartTime = null;
let lastAlertTime = null;
let currentAx = 0, currentAy = 9.81, currentAz = 0;
const GRAVITY = 9.81;
const MOTION_SPIKE_THRESHOLD = 3.0;
const SUSTAINED_POOR_SECONDS = 120.0;

function enableExperimentGyro() {
    if (expGyroEnabled) return;
    expGyroEnabled = true;

    window.addEventListener('devicemotion', (e) => {
        if (!experimentScreen.classList.contains('active')) return;
        if (e.accelerationIncludingGravity) {
            currentAx = e.accelerationIncludingGravity.x || 0;
            currentAy = e.accelerationIncludingGravity.y || 9.81;
            currentAz = e.accelerationIncludingGravity.z || 0;
        }
    });
    
    window.addEventListener('deviceorientation', (e) => {
        if (!experimentScreen.classList.contains('active')) return;
        
        if (e.beta !== null) {
            const beta = e.beta; 
            const gamma = e.gamma; 
            const alpha = e.alpha; 
            
            document.getElementById('exp-metric-beta').innerText = `${beta.toFixed(1)}°`;
            document.getElementById('exp-metric-gamma').innerText = `${gamma !== null ? gamma.toFixed(1) : 0}°`;
            document.getElementById('exp-metric-alpha').innerText = `${alpha !== null ? alpha.toFixed(1) : 0}°`;
            
            // --- FULL NECKGUARD ALGORITHM IMPLEMENTATION ---

            // 1. Motion Spike Detection
            let magnitude = Math.sqrt(currentAx**2 + currentAy**2 + currentAz**2);
            let motion_noise = Math.abs(magnitude - GRAVITY);
            let in_motion = motion_noise > MOTION_SPIKE_THRESHOLD;
            document.getElementById('exp-metric-motion').innerText = `${motion_noise.toFixed(2)}`;

            // 2. Base Pitch (Tilt from upright vertical)
            let raw_pitch = 90 - beta;
            if (raw_pitch < 0) raw_pitch = Math.abs(raw_pitch);
            if (beta < 0) raw_pitch = 90 + Math.abs(beta);
            raw_pitch = Math.min(90, Math.max(0, raw_pitch));
            
            // 3. Temporal Smoothing (Rolling Average over 10 ticks)
            pitchBuffer.push(raw_pitch);
            if (pitchBuffer.length > 10) pitchBuffer.shift();
            let smooth_pitch = pitchBuffer.reduce((a, b) => a + b) / pitchBuffer.length;
            document.getElementById('exp-metric-tilt').innerText = `${smooth_pitch.toFixed(1)}°`;

            // 4. Map pitch to Neck Flexion
            const NECK_SCALE = 0.82;
            let neck_deg = smooth_pitch * NECK_SCALE;
            let neckMetricEl = document.getElementById('exp-metric-neck');
            if (neckMetricEl) neckMetricEl.innerText = `${neck_deg.toFixed(1)}°`;
            
            // 5. Compute Confidence
            let confidence = 1.0;
            if (smooth_pitch > 60) confidence *= Math.max(0.1, 1.0 - (smooth_pitch - 60) / 30.0);
            if (motion_noise > 1.0) confidence *= Math.max(0.2, 1.0 - motion_noise / 3.0);
            document.getElementById('exp-metric-conf').innerText = `${Math.round(confidence * 100)}%`;

            // 6. Classify Posture Status
            let expBanner = document.getElementById('exp-status-banner');
            let expFeedback = document.getElementById('exp-feedback');
            
            let is_active = smooth_pitch < 75.0;
            let status_enum = "UNKNOWN";

            if (in_motion || motion_noise > 4.0) {
                status_enum = "UNKNOWN";
            } else if (!is_active) {
                status_enum = "IDLE";
            } else if (neck_deg < 15.0) {
                status_enum = "GOOD";
            } else if (neck_deg < 35.0) {
                status_enum = "MODERATE";
            } else {
                status_enum = "POOR";
            }

            // 7. Sustained Poor Alert Engine
            let now = Date.now() / 1000.0;
            let timeInPoor = 0;
            if (status_enum === "POOR") {
                if (poorStartTime === null) poorStartTime = now;
                timeInPoor = Math.floor(now - poorStartTime);
                if (timeInPoor >= SUSTAINED_POOR_SECONDS) {
                    if (lastAlertTime === null || now - lastAlertTime > 300.0) {
                        lastAlertTime = now;
                        alert("POSTURE ALERT! You have had bad posture for 2 straight minutes."); // Native alert popup
                    }
                }
            } else {
                poorStartTime = null;
            }

            // 8. Update UI
            if (status_enum === "UNKNOWN") {
                expBanner.innerText = "MOTION DETECTED";
                expBanner.className = `status-banner status-loading`;
                expFeedback.innerText = "High motion noise. Walking? Skipping metric.";
            } else if (status_enum === "IDLE") {
                expBanner.innerText = "IDLE (PHONE FLAT)";
                expBanner.className = `status-banner status-loading`;
                expFeedback.innerText = "Phone is resting. Tracking paused.";
            } else if (status_enum === "GOOD") {
                expBanner.innerText = "GOOD";
                expBanner.className = `status-banner status-good`;
                expFeedback.innerText = `Great posture! Confidence: ${Math.round(confidence * 100)}%`;
            } else if (status_enum === "MODERATE") {
                expBanner.innerText = "MODERATE";
                expBanner.className = `status-banner status-moderate`;
                expFeedback.innerText = `Slight forward bend. Confidence: ${Math.round(confidence * 100)}%`;
            } else if (status_enum === "POOR") {
                expBanner.innerText = "POOR (BAD)";
                expBanner.className = `status-banner status-risk`;
                expFeedback.innerText = `Critical Strain. Sustained: ${timeInPoor}s / ${SUSTAINED_POOR_SECONDS}s`;
            }
        }
    });
}

function enableGyro() {
    window.addEventListener('deviceorientation', (e) => {
        if (e.beta !== null) {
            gyroPitch = e.beta; 
            document.getElementById('metric-gyro').innerText = `${gyroPitch.toFixed(1)}°`;
        }
    });
}

async function startCamera() {
    const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user", width: {ideal: 640}, height: {ideal: 480} }
    });
    video.srcObject = stream;
    return new Promise(r => { video.onloadeddata = () => { video.play(); r(); } });
}

async function initMediaPipe() {
    const vision = await FilesetResolver.forVisionTasks("https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm");
    faceLandmarker = await FaceLandmarker.createFromOptions(vision, {
        baseOptions: {
            modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`,
            delegate: "GPU"
        },
        outputFacialTransformationMatrixes: true,
        runningMode: "VIDEO",
        numFaces: 1
    });
    updateStatus("ENGINE READY", "good");
}

async function renderLoop() {
    if (!faceLandmarker) {
        requestAnimationFrame(renderLoop);
        return;
    }
    
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    
    if (video.currentTime !== lastVideoTime && video.videoWidth > 0) {
        lastVideoTime = video.currentTime;
        const results = faceLandmarker.detectForVideo(video, Date.now());

        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

        if (results.faceLandmarks && results.faceLandmarks.length > 0) {
            const lm = results.faceLandmarks[0];
            
            ctx.fillStyle = "rgba(0, 255, 255, 0.4)";
            for (let i=0; i<lm.length; i+=6) { 
                ctx.fillRect(lm[i].x * canvas.width, lm[i].y * canvas.height, 2, 2);
            }

            if (currentMode === '3D') process3DPhysics(results.facialTransformationMatrixes[0].data);
            else process2DHeuristics(lm);
        } else {
            updateStatus("NO FACE DETECTED", "loading");
        }
    }
    requestAnimationFrame(renderLoop);
}

// Exactly mathematically identical pitch calculation
function process3DPhysics(matrix) {
    if (!matrix) return;
    const facePitchRad = Math.atan2(matrix[6], matrix[10]);
    let facePitch = (facePitchRad * 180) / Math.PI;
    const phoneOffset = 90 - gyroPitch; 
    let trueNeckPitch = facePitch + phoneOffset;

    document.getElementById('metric-face').innerText = `${facePitch.toFixed(1)}°`;
    document.getElementById('metric-neck').innerText = `${trueNeckPitch.toFixed(1)}°`;

    if (trueNeckPitch > 32) updateStatus("HIGH RISK", "risk", ["Dropdown Neck Strain"]);
    else if (trueNeckPitch > 18) updateStatus("MODERATE", "moderate", ["Slight Forward Bend"]);
    else updateStatus("GOOD POSTURE", "good");
}

function process2DHeuristics(lm) {
    const h = canvas.height; const w = canvas.width;
    const vr = (lm[152].y * h - lm[1].y * h) / Math.max(1, lm[1].y * h - lm[10].y * h);
    const sizePx = Math.hypot((lm[263].x - lm[33].x) * w, (lm[263].y - lm[33].y) * h);

    if (!baselines.locked) {
        baselines.vr.push(vr); baselines.size.push(sizePx);
        let pct = Math.floor((baselines.vr.length/30)*100);
        document.getElementById('calib-status').innerText = `CALIBRATING BASELINE: ${pct}% - PLEASE SIT STRAIGHT`;
        updateStatus("CALIBRATING...", "loading");
        
        if (baselines.vr.length >= 30) {
            baselines.locked = true;
            baselines.vr_mean = baselines.vr.reduce((a,b)=>a+b)/30;
            baselines.size_mean = baselines.size.reduce((a,b)=>a+b)/30;
            baselines.gyro = gyroPitch; 
            document.getElementById('calib-status').innerText = "Baseline Locked Automatically.";
        }
        return;
    }
    
    let adjusted_VR = vr + ((gyroPitch - baselines.gyro) * 0.005); 
    let vrDelta = adjusted_VR - baselines.vr_mean;
    let zoomMult = sizePx / baselines.size_mean;

    document.getElementById('metric-vr').innerText = adjusted_VR.toFixed(2);
    document.getElementById('metric-vr-delta').innerText = `${vrDelta > 0 ? '+':''}${vrDelta.toFixed(2)}`;
    document.getElementById('metric-zoom').innerText = `${sizePx.toFixed(0)}px (${zoomMult.toFixed(2)}x)`;

    let alerts = [];
    if (vrDelta < -0.15) alerts.push("Looking Down (Harsh Drop)");
    else if (vrDelta < -0.06) alerts.push("Looking Down (Mild)");
    if (zoomMult > 1.15) alerts.push("Turtle Neck (Leaning into Screen)");

    if (alerts.length >= 2 || alerts.join('').includes('Harsh')) updateStatus("HIGH RISK", "risk", alerts);
    else if (alerts.length === 1) updateStatus("MODERATE", "moderate", alerts);
    else updateStatus("GOOD POSTURE", "good", []);
}
