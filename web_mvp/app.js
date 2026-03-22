import { FaceLandmarker, FilesetResolver } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3";

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

let currentMode = '3D';
let faceLandmarker = null;
let lastVideoTime = -1;
let gyroPitch = 90;

let rollingWindowSize = [];

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

        ctx.clearRect(0, 0, canvas.width, canvas.height);

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
    
    // Z-DEPTH ABSOLUTE CALCULATION (Immune to baselines)
    // MediaPipe Z-depth: Negative = closer to camera.
    // If looking down: Forehead swings closer (-), Chin swings further (+). Forehead - Chin = Very Negative.
    const foreheadZ = lm[10].z;
    const chinZ = lm[152].z;
    
    // Trigonometric Gyro Correction on Depth
    // We adjust the Z depth by rotating the Z/Y plane mathematically against the phone's tilt.
    const gyroDeltaRad = (90 - gyroPitch) * (Math.PI / 180);
    const correctedForeheadZ = foreheadZ * Math.cos(gyroDeltaRad) - lm[10].y * Math.sin(gyroDeltaRad);
    const correctedChinZ = chinZ * Math.cos(gyroDeltaRad) - lm[152].y * Math.sin(gyroDeltaRad);
    
    // Absolute Pitch Differential (No calibrating needed)
    const absoluteZDiff = correctedForeheadZ - correctedChinZ; 

    // SCREEN ZOOM TRIPLE FLUSH (Rolling Minimum Baseline)
    // Tracks screen distance dynamically. Slowly resets if you sit back.
    const sizePx = Math.hypot((lm[263].x - lm[33].x) * w, (lm[263].y - lm[33].y) * h);
    rollingWindowSize.push(sizePx);
    if (rollingWindowSize.length > 30 * 15) rollingWindowSize.shift(); // 15 Second rolling memory
    const optimalSize = Math.min(...rollingWindowSize); 
    const zoomMult = sizePx / Math.max(1, optimalSize);

    // UI population
    document.getElementById('metric-vr').innerText = absoluteZDiff.toFixed(3);
    document.getElementById('metric-vr-delta').innerText = "ABSOLUTE (No Base)";
    document.getElementById('metric-zoom').innerText = `${sizePx.toFixed(0)}px (${zoomMult.toFixed(2)}x)`;
    document.getElementById('calib-status').innerText = `Self-Healing Z-Depth Active (No Boot Delay)`;

    let alerts = [];
    if (absoluteZDiff < -0.075) alerts.push("Looking Down (Harsh Drop)");
    else if (absoluteZDiff < -0.045) alerts.push("Looking Down (Mild)");
    
    if (zoomMult > 1.15) alerts.push("Turtle Neck (Leaning into Screen)");

    if (alerts.length >= 2 || alerts.join('').includes('Harsh')) updateStatus("HIGH RISK", "risk", alerts);
    else if (alerts.length === 1) updateStatus("MODERATE", "moderate", alerts);
    else updateStatus("GOOD POSTURE", "good", []);
}
