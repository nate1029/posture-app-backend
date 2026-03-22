// DOM Elements
const startBtn = document.getElementById('start-btn');
const setupScreen = document.getElementById('setup-screen');
const appScreen = document.getElementById('app-screen');
const video = document.getElementById('webcam');
const canvas = document.getElementById('output_canvas');
const ctx = canvas.getContext('2d');

const btn3d = document.getElementById('btn-3d');
const btn2d = document.getElementById('btn-2d');
const ui3d = document.getElementById('metrics-3d');
const ui2d = document.getElementById('metrics-2d');
const banner = document.getElementById('status-banner');

// Engine State
let currentMode = '3D'; // '3D' or '2D'
let faceLandmarker = null;
let lastVideoTime = -1;
let framesTracked = 0;
let gyroPitch = 90; // Default vertical

// 2D Baselines
let baselines = {
    locked: false,
    vr: [],
    size: [],
    gyro: 90
};

// Global toggle for UI
window.setMode = (mode) => {
    currentMode = mode;
    btn3d.classList.toggle('active', mode === '3D');
    btn2d.classList.toggle('active', mode === '2D');
    ui3d.style.display = mode === '3D' ? 'block' : 'none';
    ui2d.style.display = mode === '2D' ? 'block' : 'none';
};

window.calibrate2D = () => {
    baselines.locked = false;
    baselines.vr = [];
    baselines.size = [];
    setStatus("CALIBRATING...", "status-loading");
};

function setStatus(text, className) {
    banner.innerText = text;
    banner.className = `status-banner ${className}`;
}

// -------------------------------------------------------------
// Initialize App & Permissions
// -------------------------------------------------------------
startBtn.addEventListener('click', async () => {
    // 1. Request Gyro (Mandatory for iOS 13+)
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
            const permission = await DeviceOrientationEvent.requestPermission();
            if (permission === 'granted') {
                enableGyro();
            }
        } catch(e) { console.warn(e); }
    } else {
        enableGyro(); // Android / Non-Secure contexts
    }

    // 2. Hide Setup Screen
    setupScreen.classList.remove('active');
    appScreen.classList.add('active');

    // 3. Start Camera & AI
    await startCamera();
    await initMediaPipe();
    
    // 4. Start Render Loop
    requestAnimationFrame(renderLoop);
});

function enableGyro() {
    window.addEventListener('deviceorientation', (e) => {
        if (e.beta !== null) {
            // beta is 90 when standing up, 0 when flat.
            gyroPitch = e.beta; 
            document.getElementById('val-gyro').innerText = `${gyroPitch.toFixed(1)}°`;
        }
    });
}

async function startCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: "user", width: 640, height: 480 }
        });
        video.srcObject = stream;
        video.onloadeddata = () => {
             // Keep canvas size synced to video physical dimensions
             canvas.width = video.clientWidth;
             canvas.height = video.clientHeight;
        }
    } catch (err) {
        alert("Camera access denied or failed.");
    }
}

async function initMediaPipe() {
    setStatus("LOADING AI MODELS...", "status-loading");
    const vision = await FilesetResolver.forVisionTasks(
        "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm"
    );
    
    faceLandmarker = await FaceLandmarker.createFromOptions(vision, {
        baseOptions: {
            modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`,
            delegate: "GPU"
        },
        outputFaceBlendshapes: false,
        outputFacialTransformationMatrixes: true, // CRITICAL FOR 3D MATH!
        runningMode: "VIDEO",
        numFaces: 1
    });
    
    setStatus("ENGINE READY", "status-good");
}

// -------------------------------------------------------------
// Master Render Loop
// -------------------------------------------------------------
async function renderLoop() {
    if (!faceLandmarker) {
        requestAnimationFrame(renderLoop);
        return;
    }
    
    canvas.width = video.clientWidth;
    canvas.height = video.clientHeight;
    
    let nowInMs = Date.now();
    if (video.currentTime !== lastVideoTime) {
        lastVideoTime = video.currentTime;
        const results = faceLandmarker.detectForVideo(video, nowInMs);

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        if (results.faceLandmarks && results.faceLandmarks.length > 0) {
            const lm = results.faceLandmarks[0];
            
            // Draw a subtle wireframe outline around face points
            ctx.fillStyle = "rgba(0, 255, 255, 0.4)";
            for (let i = 0; i < lm.length; i+=10) { 
                ctx.fillRect(lm[i].x * canvas.width, lm[i].y * canvas.height, 2, 2);
            }

            if (currentMode === '3D') {
                process3DPhysics(results.facialTransformationMatrixes[0].data);
            } else {
                process2DHeuristics(lm);
            }
        } else {
            setStatus("NO FACE DETECTED", "status-loading");
        }
    }
    requestAnimationFrame(renderLoop);
}

// -------------------------------------------------------------
// 3D Math Engine (solvePnP equivalent internally)
// -------------------------------------------------------------
function process3DPhysics(matrix) {
    if (!matrix) return;
    
    // Extract Pitch from the 4x4 homogenous matrix (approximated Euler representation)
    // Looking down = Positive pitch.
    const facePitchRad = Math.atan2(matrix[6], matrix[10]);
    let facePitch = (facePitchRad * 180) / Math.PI;
    
    // Apple's device orientation `beta` is 90 when perfectly vertical.
    // If the phone is tilted slightly down towards you, it might be 60.
    // Offset = 90 - 60 = 30° backwards tilt.
    const phoneOffset = 90 - gyroPitch; 
    
    // FUSION Formula: True Neck Drop = Face drop + Phone drop
    let trueNeckPitch = facePitch + phoneOffset;
    
    document.getElementById('val-face-pitch').innerText = `${facePitch.toFixed(1)}°`;
    document.getElementById('val-true-pitch').innerText = `${trueNeckPitch.toFixed(1)}°`;

    if (trueNeckPitch > 35) {
        setStatus("HIGH RISK (Drop Neck)", "status-risk");
    } else if (trueNeckPitch > 20) {
        setStatus("MODERATE STRAIN", "status-moderate");
    } else {
        setStatus("GOOD POSTURE", "status-good");
    }
}

// -------------------------------------------------------------
// 2D Heuristics Engine (Distances & Ratios)
// -------------------------------------------------------------
function process2DHeuristics(lm) {
    // MediaPipe Landmarks:
    // Forehead: 10, Nose: 1, Chin: 152, L-Eye: 33, R-Eye: 263
    const h = canvas.height;
    const w = canvas.width;
    
    const foreheadY = lm[10].y * h;
    const noseY = lm[1].y * h;
    const chinY = lm[152].y * h;
    
    // 1. Vertical Ratio (Face Flexion Compression)
    const upperFace = noseY - foreheadY;
    const lowerFace = chinY - noseY;
    let vr = lowerFace / Math.max(1.0, upperFace);
    
    // 2. Face Z-Zoom (Leaning Close)
    const dx = (lm[263].x - lm[33].x) * w;
    const dy = (lm[263].y - lm[33].y) * h;
    const sizePixels = Math.hypot(dx, dy);

    // Baseline Gathering Phase
    if (!baselines.locked) {
        baselines.vr.push(vr);
        baselines.size.push(sizePixels);
        
        let progress = Math.floor((baselines.vr.length / 30) * 100);
        setStatus(`CALIBRATING: ${progress}%`, "status-loading");
        
        if (baselines.vr.length >= 30) {
            baselines.locked = true;
            baselines.vr_mean = baselines.vr.reduce((a,b)=>a+b) / 30;
            baselines.size_mean = baselines.size.reduce((a,b)=>a+b) / 30;
            baselines.gyro = gyroPitch; // Remember what angle the phone was at!
        }
        return;
    }
    
    // Active Analysis Mode
    // Mix the Gyro Delta into the 2D Vertical Ratio! 
    // If the phone tilted upwards (beta decreased), the ratio visually compressed. We must penalize it.
    let gyroDelta = gyroPitch - baselines.gyro;
    let adjusted_VR = vr + (gyroDelta * 0.006); // 0.006 is a standard correction constant for 2D foreshortening.
    
    let vrDelta = adjusted_VR - baselines.vr_mean;
    let zoomMultiplier = sizePixels / baselines.size_mean;

    document.getElementById('val-vr').innerText = `${adjusted_VR.toFixed(2)} (Δ ${vrDelta > 0 ? '+':''}${vrDelta.toFixed(2)})`;
    document.getElementById('val-size').innerText = `${sizePixels.toFixed(0)}px (${zoomMultiplier.toFixed(2)}x)`;

    let alerts = 0;
    if (vrDelta < -0.15) alerts += 2;      // Harsh drop
    else if (vrDelta < -0.06) alerts += 1; // Mild drop
    
    if (zoomMultiplier > 1.15) alerts += 1; // Turtle necking
    
    if (alerts >= 2) {
        setStatus("HIGH RISK (Slouch / Zoom)", "status-risk");
    } else if (alerts === 1) {
        setStatus("MODERATE STRAIN", "status-moderate");
    } else {
        setStatus("GOOD POSTURE", "status-good");
    }
}
