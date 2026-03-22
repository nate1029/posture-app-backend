import { FaceLandmarker, FilesetResolver } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3";

// DOM Elements
const startBtn = document.getElementById('start-btn');
const setupScreen = document.getElementById('setup-screen');
const appScreen = document.getElementById('app-screen');
const video = document.getElementById('webcam');
const canvas = document.getElementById('output_canvas');
const ctx = canvas.getContext('2d');

const btn3d = document.getElementById('btn-3d');
const btn2d = document.getElementById('btn-2d');

// Engine State
let currentMode = '3D'; // '3D' or '2D'
let faceLandmarker = null;
let lastVideoTime = -1;
let gyroPitch = 90; // Default flat

// 2D Baselines
let baselines = {
    locked: false,
    vr: [],
    size: [],
    gyro: 90
};

window.setMode = (mode) => {
    currentMode = mode;
    btn3d.classList.toggle('active', mode === '3D');
    btn2d.classList.toggle('active', mode === '2D');
};

// -------------------------------------------------------------
// Initialize App & Permissions
// -------------------------------------------------------------
startBtn.addEventListener('click', async () => {
    document.getElementById("loading-msg").innerText = "Requesting Gyro...";

    // Request Gyro (Mandatory for iOS 13+)
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
            const permission = await DeviceOrientationEvent.requestPermission();
            if (permission === 'granted') {
                enableGyro();
            }
        } catch(e) { console.warn(e); }
    } else {
        enableGyro(); 
    }

    document.getElementById("loading-msg").innerText = "Requesting Camera...";
    await startCamera();

    document.getElementById("loading-msg").innerText = "Downloading AI Model (Wait!)...";
    setupScreen.classList.remove('active');
    appScreen.classList.add('active');
    
    await initMediaPipe();
    requestAnimationFrame(renderLoop);
});

function enableGyro() {
    window.addEventListener('deviceorientation', (e) => {
        if (e.beta !== null) {
            gyroPitch = e.beta; 
        }
    });
}

function putText(text, x, y, color = "#FFF", size = "20px", bold = false) {
    ctx.fillStyle = color;
    ctx.font = `${bold ? "bold " : ""}${size} 'Inter', sans-serif`;
    ctx.fillText(text, x, y);
}

function fillRectC(x, y, w, h, color) {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
}

async function startCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: "user", width: {ideal: 640}, height: {ideal: 480} }
        });
        video.srcObject = stream;
        
        return new Promise((resolve) => {
            video.onloadeddata = () => {
                video.play();
                resolve();
            }
        });
    } catch (err) {
        alert("CAMERA ERROR: " + err.message);
    }
}

async function initMediaPipe() {
    try {
        const vision = await FilesetResolver.forVisionTasks(
            "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm"
        );
        faceLandmarker = await FaceLandmarker.createFromOptions(vision, {
            baseOptions: {
                modelAssetPath: `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`,
                delegate: "GPU"
            },
            outputFacialTransformationMatrixes: true,
            runningMode: "VIDEO",
            numFaces: 1
        });
    } catch (e) {
        alert("AI MODEL ERROR: " + e.message);
    }
}

// -------------------------------------------------------------
// Master Render Loop
// -------------------------------------------------------------
async function renderLoop() {
    if (!faceLandmarker) {
        // Draw loading screen if camera is active but model downloading
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        ctx.fillStyle = "#111";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        putText("DOWNLOADING AI MODELS...", 40, 150, "#00FFAA", "24px", true);
        requestAnimationFrame(renderLoop);
        return;
    }
    
    // Make canvas exact size of video rendering for accurate drawing
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    
    let nowInMs = Date.now();
    if (video.currentTime !== lastVideoTime && video.videoWidth > 0) {
        lastVideoTime = video.currentTime;
        const results = faceLandmarker.detectForVideo(video, nowInMs);

        // Draw webcam frame raw
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

        // Draw HUD Background
        fillRectC(10, 10, canvas.width - 20, 150, "rgba(20,20,20,0.85)");
        
        if (results.faceLandmarks && results.faceLandmarks.length > 0) {
            const lm = results.faceLandmarks[0];
            
            ctx.fillStyle = "rgba(0, 255, 255, 0.4)";
            for (let i = 0; i < lm.length; i+=5) { 
                ctx.fillRect(lm[i].x * canvas.width, lm[i].y * canvas.height, 2, 2);
            }

            if (currentMode === '3D') {
                process3DPhysics(results.facialTransformationMatrixes[0].data);
            } else {
                process2DHeuristics(lm);
            }
        } else {
            putText("NO PERSON DETECTED", 30, 60, "#FF4040", "22px", true);
            putText(`📱 Phone Gyro: ${gyroPitch.toFixed(1)}°`, 30, 100, "#CCC", "18px");
        }
    }
    requestAnimationFrame(renderLoop);
}

// -------------------------------------------------------------
// 3D Math Engine (solvePnP equivalent internally)
// -------------------------------------------------------------
function process3DPhysics(matrix) {
    if (!matrix) return;
    
    // Math logic identically copied from the original Python script
    const facePitchRad = Math.atan2(matrix[6], matrix[10]);
    let facePitch = (facePitchRad * 180) / Math.PI;
    
    // Gyro offsets (Beta 90 = Upright, Beta < 90 = Phone tilted back).
    const phoneOffset = 90 - gyroPitch; 
    
    // FUSION Formula
    let trueNeckPitch = facePitch + phoneOffset;

    // UI drawing exactly like OpenCV script
    let statusText = "GOOD POSTURE";
    let color = "#50DC3C"; // Green

    if (trueNeckPitch > 32) {
        statusText = "HIGH RISK (Dropdown Neck)";
        color = "#DC1E1E"; // Red
    } else if (trueNeckPitch > 18) {
        statusText = "MODERATE STRAIN";
        color = "#FAC800"; // Yellow
    }

    putText(`POSTURE: ${statusText}`, 25, 45, color, "26px", true);
    
    putText(`📱 Phone Gyro: ${gyroPitch.toFixed(1)}°`, 25, 80, "#FFF", "16px");
    putText(`🎥 Camera Face: ${facePitch.toFixed(1)}°`, 25, 110, "#FFF", "16px");
    putText(`📐 True Neck Fused: ${trueNeckPitch.toFixed(1)}°`, 25, 140, "#00FFAA", "16px", true);
}

// -------------------------------------------------------------
// 2D Heuristics Engine (Distances & Ratios)
// -------------------------------------------------------------
function process2DHeuristics(lm) {
    const h = canvas.height;
    const w = canvas.width;
    
    const foreheadY = lm[10].y * h;
    const noseY = lm[1].y * h;
    const chinY = lm[152].y * h;
    
    const upperFace = Math.max(1, noseY - foreheadY);
    const lowerFace = chinY - noseY;
    let vr = lowerFace / upperFace;
    
    const dx = (lm[263].x - lm[33].x) * w;
    const dy = (lm[263].y - lm[33].y) * h;
    const sizePixels = Math.hypot(dx, dy);

    // Initial silent calibration (30 frames)
    if (!baselines.locked) {
        baselines.vr.push(vr);
        baselines.size.push(sizePixels);
        
        let progress = Math.floor((baselines.vr.length / 30) * 100);
        putText(`CALIBRATING BASELINE: ${progress}%`, 25, 60, "#00E0FF", "24px", true);
        putText(`Please hold your phone up and look straight.`, 25, 90, "#CCC", "16px");
        
        if (baselines.vr.length >= 30) {
            baselines.locked = true;
            baselines.vr_mean = baselines.vr.reduce((a,b)=>a+b) / 30;
            baselines.size_mean = baselines.size.reduce((a,b)=>a+b) / 30;
            baselines.gyro = gyroPitch; 
        }
        return;
    }
    
    // Mix Gyro Delta into 2D Ratio
    let gyroDelta = gyroPitch - baselines.gyro;
    let adjusted_VR = vr + (gyroDelta * 0.005); 
    
    let vrDelta = adjusted_VR - baselines.vr_mean;
    let zoomMultiplier = sizePixels / baselines.size_mean;

    let alerts = [];
    if (vrDelta < -0.15) alerts.push("Looking Down (Harsh Drop)");
    else if (vrDelta < -0.06) alerts.push("Looking Down (Mild Drop)");
    
    if (zoomMultiplier > 1.15) alerts.push("Leaning Into Screen");

    let statusText = "GOOD POSTURE";
    let color = "#50DC3C"; 

    if (alerts.length >= 2 || alerts.join('').includes('Harsh')) {
        statusText = "HIGH RISK";
        color = "#DC1E1E";
    } else if (alerts.length === 1) {
        statusText = "MODERATE STRAIN";
        color = "#FAC800";
    }

    putText(`POSTURE: ${statusText}`, 25, 45, color, "26px", true);
    
    let lineY = 80;
    if (alerts.length === 0) {
        putText("Perfect! You look great.", 25, lineY, "#CCC", "16px");
    } else {
        alerts.forEach(a => {
            putText(`- ${a}`, 25, lineY, color, "16px", true);
            lineY += 25;
        });
    }

    // Debug Data Panel Right Align
    let rw = canvas.width - 200;
    putText(`VR Ratio: ${adjusted_VR.toFixed(2)}`, rw, 80, "#FFF", "13px");
    putText(`VR Delta: ${vrDelta > 0 ? '+':''}${vrDelta.toFixed(2)}`, rw, 105, "#FFF", "13px");
    putText(`Z-Zoom: ${sizePixels.toFixed(0)}px`, rw, 130, "#FFF", "13px");
}
