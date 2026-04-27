import { useState } from "react";

const S = `
@import url('https://fonts.googleapis.com/css2?family=Fraunces:ital,wght@0,400;0,600;0,700;1,400&family=DM+Sans:wght@300;400;500;600&display=swap');

*{box-sizing:border-box;margin:0;padding:0;}
:root{
  --sage:#7A9E7E;
  --sage-light:#B5CEB8;
  --sage-pale:#E8F0E9;
  --earth:#C4A882;
  --earth-light:#EDD9BE;
  --earth-pale:#F7F0E6;
  --cream:#FAF8F3;
  --bark:#3D2E1E;
  --bark-soft:#5C4A35;
  --mist:#EEF2EE;
  --moss:#4A6741;
  --coral:#D4736A;
  --sky:#7BAFC4;
  --sky-pale:#DCF0F7;
  --white:#FFFFFF;
  --muted:#8A8A7A;
}

body{background:var(--cream);}
.app{font-family:'DM Sans',sans-serif;background:var(--cream);max-width:390px;margin:0 auto;height:100vh;overflow:hidden;display:flex;flex-direction:column;position:relative;}
.screen{flex:1;overflow-y:auto;overflow-x:hidden;padding-bottom:88px;}
.screen::-webkit-scrollbar{display:none;}

/* NAV */
.nav{position:absolute;bottom:0;left:0;right:0;background:var(--bark);padding:12px 16px 22px;display:flex;justify-content:space-around;z-index:99;border-radius:24px 24px 0 0;}
.ni{display:flex;flex-direction:column;align-items:center;gap:3px;cursor:pointer;opacity:.35;transition:all .2s;flex:1;}
.ni.active{opacity:1;}
.ni-icon{width:42px;height:42px;border-radius:13px;display:flex;align-items:center;justify-content:center;font-size:19px;transition:background .2s;}
.ni.active .ni-icon{background:var(--sage);}
.ni-lbl{font-size:9px;font-weight:600;color:rgba(255,255,255,.5);letter-spacing:.05em;text-transform:uppercase;}
.ni.active .ni-lbl{color:var(--sage-light);}

/* SHARED */
.topbar{display:flex;justify-content:space-between;align-items:center;}
.logobadge{background:var(--moss);color:var(--cream);font-family:'Fraunces',serif;font-weight:700;font-size:14px;letter-spacing:.02em;padding:6px 14px;border-radius:20px;}
.avatar{width:36px;height:36px;background:var(--earth-light);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:15px;cursor:pointer;border:2px solid var(--earth);}
.sec-lbl{font-size:10px;font-weight:600;letter-spacing:.1em;text-transform:uppercase;color:var(--muted);margin-bottom:10px;}

/* ════════════════════════════
   HOME
════════════════════════════ */
.home-hdr{
  background:linear-gradient(160deg,var(--moss) 0%,var(--sage) 60%,var(--sage-light) 100%);
  padding:52px 22px 20px;
  position:relative;overflow:hidden;flex-shrink:0;
}
.home-hdr::after{content:'';position:absolute;width:160px;height:160px;background:rgba(255,255,255,.08);border-radius:50%;top:-50px;right:-40px;}
.home-hdr::before{content:'';position:absolute;width:80px;height:80px;background:rgba(255,255,255,.06);border-radius:50%;bottom:10px;left:20px;}
.h-greeting{font-size:12px;color:rgba(255,255,255,.65);letter-spacing:.06em;text-transform:uppercase;margin-bottom:4px;margin-top:16px;}
.h-name{font-family:'Fraunces',serif;font-size:26px;font-weight:700;color:#fff;line-height:1.1;margin-bottom:2px;}
.h-name span{font-style:italic;}
.h-sub{font-size:12px;color:rgba(255,255,255,.6);margin-bottom:14px;}

/* SCORE ROW */
.score-row{background:rgba(255,255,255,.15);border-radius:16px;padding:14px 16px;backdrop-filter:blur(8px);display:flex;align-items:center;gap:14px;}
.score-num{font-family:'Fraunces',serif;font-size:48px;font-weight:700;color:#fff;line-height:1;}
.score-info{flex:1;}
.score-lbl{font-size:10px;color:rgba(255,255,255,.6);letter-spacing:.07em;text-transform:uppercase;margin-bottom:3px;}
.score-interp{font-size:14px;font-weight:600;color:#fff;margin-bottom:2px;}
.score-delta{font-size:11px;color:rgba(255,255,255,.6);}
.live-pill{display:flex;align-items:center;gap:5px;background:rgba(255,255,255,.2);border-radius:20px;padding:4px 10px;font-size:10px;font-weight:700;color:#fff;letter-spacing:.06em;margin-top:10px;width:fit-content;}
.pulse{width:7px;height:7px;border-radius:50%;background:#fff;animation:pulse 1.4s infinite;}
@keyframes pulse{0%,100%{transform:scale(1);opacity:1;}50%{transform:scale(1.5);opacity:.5;}}

/* HOME BODY */
.home-body{padding:12px 16px 0;display:flex;flex-direction:column;gap:10px;}

/* STREAK */
.streak-pill{background:var(--bark);border-radius:14px;padding:11px 16px;display:flex;justify-content:space-between;align-items:center;}
.s-left{display:flex;align-items:center;gap:7px;}
.s-txt{font-family:'Fraunces',serif;font-size:15px;font-weight:600;color:var(--earth-light);}
.s-right{font-size:11px;color:rgba(255,255,255,.4);}
.s-right span{color:rgba(255,255,255,.75);font-weight:600;}

/* NUDGE */
.nudge-home{background:var(--coral);border-radius:14px;padding:14px 16px;}
.nudge-top-row{display:flex;align-items:flex-start;gap:10px;margin-bottom:8px;}
.nudge-icon{font-size:22px;flex-shrink:0;margin-top:2px;}
.nudge-lbl{font-size:9px;font-weight:700;color:rgba(255,255,255,.55);letter-spacing:.1em;text-transform:uppercase;margin-bottom:3px;}
.nudge-txt{font-family:'Fraunces',serif;font-size:14px;font-weight:600;color:#fff;line-height:1.35;}
.nudge-tags{display:flex;gap:6px;margin-bottom:8px;}
.ntag{font-size:9px;font-weight:600;padding:3px 8px;border-radius:20px;background:rgba(255,255,255,.18);color:#fff;}
.nudge-why{background:rgba(0,0,0,.1);border-radius:10px;padding:8px 10px;}
.nw-lbl{font-size:8px;font-weight:700;color:rgba(255,255,255,.5);letter-spacing:.1em;text-transform:uppercase;margin-bottom:2px;}
.nw-txt{font-size:10px;color:rgba(255,255,255,.8);line-height:1.5;font-style:italic;}

/* TODAY'S EXERCISES */
.today-ex{background:var(--white);border-radius:14px;padding:14px 16px;border:1px solid var(--mist);}
.today-ex-hdr{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;}
.today-ex-title{font-family:'Fraunces',serif;font-size:15px;font-weight:700;color:var(--bark);}
.today-ex-sub{font-size:10px;color:var(--muted);margin-top:2px;}
.tex-item{display:flex;align-items:center;gap:10px;background:var(--mist);border-radius:10px;padding:10px 12px;cursor:pointer;transition:background .2s;margin-bottom:7px;}
.tex-item:last-child{margin-bottom:0;}
.tex-item:hover{background:var(--sage-pale);}
.tex-dot{width:9px;height:9px;border-radius:50%;flex-shrink:0;}
.tex-name{flex:1;font-size:12px;font-weight:600;color:var(--bark);}
.tex-name.done{text-decoration:line-through;color:var(--muted);}
.tex-cat{font-size:9px;color:var(--muted);font-weight:500;margin-right:4px;}
.tex-arr{font-size:11px;color:var(--muted);}

/* DETECTED PROBLEM BANNER */
.detect-banner{
  background:linear-gradient(135deg,var(--bark) 0%,var(--bark-soft) 100%);
  border-radius:14px;
  padding:14px 16px;
  display:flex;align-items:flex-start;gap:12px;
}
.detect-icon{font-size:26px;flex-shrink:0;}
.detect-info{flex:1;}
.detect-lbl{font-size:9px;font-weight:700;color:rgba(255,255,255,.45);letter-spacing:.1em;text-transform:uppercase;margin-bottom:4px;}
.detect-title{font-family:'Fraunces',serif;font-size:15px;font-weight:700;color:#fff;margin-bottom:3px;line-height:1.3;}
.detect-sub{font-size:11px;color:rgba(255,255,255,.5);line-height:1.4;}
.detect-badge{background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.2);border-radius:20px;padding:4px 10px;font-size:10px;font-weight:600;color:rgba(255,255,255,.7);margin-top:8px;display:inline-block;}

/* RECOMMENDED EXERCISES */
.rec-card{
  background:var(--white);
  border-radius:14px;
  border:1px solid var(--mist);
  overflow:hidden;
  cursor:pointer;
  transition:transform .18s;
}
.rec-card:active{transform:scale(.98);}
.rec-card-inner{display:flex;align-items:center;gap:12px;padding:14px 16px;}
.rec-num{
  width:38px;height:38px;border-radius:11px;
  display:flex;align-items:center;justify-content:center;
  font-family:'Fraunces',serif;font-size:16px;font-weight:700;
  flex-shrink:0;
}
.rec-info{flex:1;}
.rec-name{font-family:'Fraunces',serif;font-size:14px;font-weight:600;color:var(--bark);margin-bottom:3px;}
.rec-meta{display:flex;align-items:center;gap:8px;}
.rec-cat{font-size:10px;font-weight:600;padding:2px 8px;border-radius:20px;color:var(--bark-soft);}
.rec-reps{font-size:10px;color:var(--muted);}
.rec-arr{font-size:13px;color:var(--muted);flex-shrink:0;}

/* WHY RECOMMENDED STRIP */
.rec-why{
  background:var(--sage-pale);
  border-top:1px solid var(--mist);
  padding:8px 16px;
  display:flex;align-items:center;gap:6px;
}
.rec-why-txt{font-size:10px;color:var(--moss);font-style:italic;line-height:1.4;}

/* SECTION DIVIDER */
.sec-divider{display:flex;align-items:center;gap:10px;padding:4px 0;}
.sec-divider-line{flex:1;height:1px;background:var(--mist);}
.sec-divider-txt{font-size:10px;font-weight:600;letter-spacing:.1em;text-transform:uppercase;color:var(--muted);white-space:nowrap;}

/* ════════════════════════════
   EXERCISES TAB
════════════════════════════ */
.ex-hdr{background:linear-gradient(160deg,var(--bark) 0%,var(--bark-soft) 100%);padding:52px 22px 20px;}
.ex-hdr-title{font-family:'Fraunces',serif;font-size:24px;font-weight:700;color:#fff;margin:16px 0 4px;}
.ex-hdr-sub{font-size:12px;color:rgba(255,255,255,.45);margin-bottom:14px;}
.cat-tabs{display:flex;gap:7px;overflow-x:auto;padding-bottom:2px;}
.cat-tabs::-webkit-scrollbar{display:none;}
.cat-tab{white-space:nowrap;font-size:11px;font-weight:600;padding:6px 14px;border-radius:20px;cursor:pointer;border:1.5px solid;transition:all .2s;letter-spacing:.03em;}
.cat-tab.active{background:var(--sage);border-color:var(--sage);color:#fff;}
.cat-tab.inactive{background:transparent;border-color:rgba(255,255,255,.2);color:rgba(255,255,255,.5);}
.ex-list{padding:16px 16px 0;display:flex;flex-direction:column;gap:10px;}
.ex-card{background:var(--white);border-radius:16px;overflow:hidden;border:1px solid var(--mist);}
.ex-card-top{padding:14px 16px;display:flex;align-items:center;gap:12px;cursor:pointer;}
.ex-num{width:34px;height:34px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-family:'Fraunces',serif;font-size:16px;font-weight:700;flex-shrink:0;}
.ex-card-name{font-family:'Fraunces',serif;font-size:15px;font-weight:600;color:var(--bark);margin-bottom:2px;display:flex;align-items:center;gap:7px;}
.ex-done-badge{font-size:9px;background:var(--sage);color:#fff;padding:2px 7px;border-radius:20px;font-family:'DM Sans',sans-serif;font-weight:600;}
.ex-card-sub{font-size:11px;color:var(--muted);}
.ex-chev{font-size:12px;color:var(--muted);transition:transform .2s;}
.ex-chev.open{transform:rotate(180deg);}
.ex-expand{padding:0 16px 14px;border-top:1px solid var(--mist);}
.ex-steps{margin:12px 0;}
.ex-step{display:flex;gap:9px;margin-bottom:9px;align-items:flex-start;}
.ex-step-num{width:20px;height:20px;border-radius:50%;background:var(--sage-pale);color:var(--moss);font-size:9px;font-weight:800;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:2px;}
.ex-step-txt{font-size:12px;color:var(--bark-soft);line-height:1.55;flex:1;}
.ex-reps{display:inline-flex;align-items:center;gap:6px;background:var(--earth-pale);border-radius:10px;padding:8px 12px;margin-bottom:12px;}
.ex-reps-txt{font-size:11px;font-weight:600;color:var(--bark-soft);}
.ex-btn{border:none;border-radius:12px;padding:11px 16px;width:100%;font-family:'Fraunces',serif;font-size:13px;font-weight:700;cursor:pointer;transition:all .2s;}
.ex-btn.start{background:var(--bark);color:var(--cream);}
.ex-btn.done{background:var(--sage-pale);color:var(--moss);}

/* ════════════════════════════
   PROGRESS + REWARDS
════════════════════════════ */
.prog-hdr{background:linear-gradient(160deg,var(--sage) 0%,var(--sage-light) 100%);padding:52px 22px 24px;}
.prog-score-row{display:flex;align-items:center;gap:16px;margin-top:14px;}
.prog-big{font-family:'Fraunces',serif;font-size:56px;font-weight:700;color:#fff;line-height:1;}
.prog-info{flex:1;}
.prog-lbl{font-size:10px;color:rgba(255,255,255,.6);letter-spacing:.07em;text-transform:uppercase;margin-bottom:4px;}
.prog-interp{font-size:15px;font-weight:600;color:#fff;margin-bottom:2px;}
.prog-sub{font-size:11px;color:rgba(255,255,255,.6);}
.accord-wrap{padding:16px 16px 0;display:flex;flex-direction:column;gap:8px;}
.accord{background:var(--white);border-radius:16px;overflow:hidden;border:1px solid var(--mist);}
.accord-row{padding:14px 16px;display:flex;align-items:center;justify-content:space-between;cursor:pointer;}
.accord-left{display:flex;align-items:center;gap:10px;}
.accord-icon{width:36px;height:36px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:17px;flex-shrink:0;}
.accord-title{font-family:'Fraunces',serif;font-size:14px;font-weight:600;color:var(--bark);margin-bottom:2px;}
.accord-sub{font-size:10px;color:var(--muted);}
.accord-chev{font-size:11px;color:var(--muted);transition:transform .2s;}
.accord-chev.open{transform:rotate(180deg);}
.accord-body{padding:0 16px 16px;border-top:1px solid var(--mist);}
.week-rings{display:flex;gap:5px;margin-top:12px;}
.day-ring{display:flex;flex-direction:column;align-items:center;gap:3px;flex:1;}
.dc{width:32px;height:32px;border-radius:50%;border:2.5px solid;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:700;}
.dc.done{border-color:var(--sage);background:var(--sage-pale);color:var(--moss);}
.dc.today{border-color:var(--sage);background:var(--sage);color:#fff;}
.dc.missed{border-color:var(--earth);background:var(--earth-pale);color:var(--earth);}
.dc.future{border-color:#E0E0D8;background:transparent;color:#ccc;}
.day-lbl{font-size:9px;color:var(--muted);}
.stats-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px;}
.stat-box{background:var(--mist);border-radius:12px;padding:12px;}
.stat-val{font-family:'Fraunces',serif;font-size:22px;font-weight:700;color:var(--bark);margin-bottom:2px;}
.stat-lbl{font-size:10px;color:var(--muted);}
.pbars{margin-top:12px;display:flex;flex-direction:column;gap:9px;}
.pbar-top{display:flex;justify-content:space-between;margin-bottom:4px;}
.pbar-lbl{font-size:11px;color:var(--bark-soft);font-weight:500;}
.pbar-val{font-size:11px;color:var(--muted);}
.pbar-bg{background:var(--mist);border-radius:6px;height:6px;overflow:hidden;}
.pbar-fill{height:100%;border-radius:6px;background:var(--sage);}
.lb-list{margin-top:12px;display:flex;flex-direction:column;gap:6px;}
.lb-item{display:flex;align-items:center;gap:9px;padding:10px 12px;border-radius:12px;background:var(--mist);}
.lb-item.me{background:var(--sage-pale);border:1.5px solid var(--sage);}
.lb-rank{font-family:'Fraunces',serif;font-size:13px;font-weight:700;color:var(--muted);width:18px;text-align:center;}
.lb-av{width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:13px;flex-shrink:0;}
.lb-name{flex:1;font-size:12px;font-weight:600;color:var(--bark);}
.lb-streak{font-family:'Fraunces',serif;font-size:13px;font-weight:700;color:var(--bark);}
.friends-list{margin-top:12px;display:flex;flex-direction:column;gap:8px;}
.friend-item{display:flex;align-items:center;gap:10px;}
.friend-av{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;background:var(--mist);}
.friend-name{font-size:13px;font-weight:600;color:var(--bark);}
.friend-streak{font-size:11px;color:var(--muted);}
.f-status{font-size:10px;font-weight:600;padding:3px 9px;border-radius:20px;margin-left:auto;}
.f-status.ahead{background:var(--sage-pale);color:var(--moss);}
.f-status.behind{background:#FFECEC;color:var(--coral);}
.f-status.tied{background:var(--earth-pale);color:var(--bark-soft);}
.pts-card{background:var(--bark);border-radius:14px;padding:14px 16px;margin-top:12px;display:flex;align-items:center;justify-content:space-between;}
.pts-val{font-family:'Fraunces',serif;font-size:30px;font-weight:700;color:var(--earth-light);}
.pts-lbl{font-size:10px;color:rgba(255,255,255,.4);letter-spacing:.06em;text-transform:uppercase;margin-top:2px;}
.pts-btn{background:var(--sage);color:#fff;border:none;border-radius:10px;padding:8px 14px;font-family:'Fraunces',serif;font-size:12px;font-weight:700;cursor:pointer;}
.titles-row{display:flex;gap:7px;margin-top:10px;flex-wrap:wrap;}
.title-chip{padding:6px 13px;border-radius:20px;font-size:11px;font-weight:600;}
.title-chip.active{background:var(--sage);color:#fff;}
.title-chip.locked{background:var(--mist);color:#bbb;}
.badge-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:12px;}
.badge{display:flex;flex-direction:column;align-items:center;gap:4px;}
.badge-icon{width:46px;height:46px;border-radius:13px;display:flex;align-items:center;justify-content:center;font-size:21px;}
.badge-icon.earned{background:var(--sage-pale);border:1.5px solid var(--sage-light);}
.badge-icon.locked{background:var(--mist);filter:grayscale(1);opacity:.4;}
.badge-lbl{font-size:8px;color:var(--muted);text-align:center;line-height:1.3;}
.sloucho-card{background:linear-gradient(135deg,var(--bark) 0%,var(--bark-soft) 100%);border-radius:14px;padding:14px 16px;margin-top:10px;display:flex;align-items:center;gap:12px;}
.sloucho-av{font-size:38px;}
.sloucho-name{font-family:'Fraunces',serif;font-size:14px;font-weight:700;color:#fff;margin-bottom:2px;}
.sloucho-outfit{font-size:10px;color:rgba(255,255,255,.4);}
.sloucho-btn{background:rgba(255,255,255,.1);color:rgba(255,255,255,.8);border:1px solid rgba(255,255,255,.2);border-radius:10px;padding:7px 12px;font-size:11px;font-weight:600;cursor:pointer;margin-left:auto;}

@keyframes fadeUp{from{opacity:0;transform:translateY(10px);}to{opacity:1;transform:translateY(0);}}
.a1{animation:fadeUp .3s ease both;}
.a2{animation:fadeUp .3s .06s ease both;}
.a3{animation:fadeUp .3s .12s ease both;}
.a4{animation:fadeUp .3s .18s ease both;}
.a5{animation:fadeUp .3s .24s ease both;}
.a6{animation:fadeUp .3s .30s ease both;}
`;

// ── DATA ──
const days = ["M","T","W","T","F","S","S"];
const dayStates = ["done","done","done","missed","today","future","future"];

const todayExercises = [
  {id:5, name:"Chin Tuck", cat:"Cervical", reps:"10 reps · hold 5s", color:"#B5CEB8", done:true},
  {id:9, name:"Scapular Retractions", cat:"Strengthening", reps:"10 reps · hold 5s", color:"#DCF0F7", done:true},
  {id:1, name:"Cervical Flexion", cat:"Cervical", reps:"10 reps", color:"#B5CEB8", done:false},
];

// Detected problem drives the recommended section
const detectedProblem = {
  icon:"👁️",
  title:"High screen distance detected",
  sub:"Your phone was held closer than 30cm for 68% of your session today.",
  badge:"Eye strain · Moderate severity",
  color:"#E8F0E9",
};

const recommendedExercises = [
  {
    id:14, name:"20-20-20 Rule", cat:"Eye Relief", reps:"Every 20 min",
    color:"#E8F0E9", catColor:"#4A6741",
    why:"Closest match for sustained near-vision screen use detected today.",
    steps:["Every 20 minutes, pause your screen use.","Look at an object 20 feet away.","Hold gaze for 20 seconds. Return to screen."],
  },
  {
    id:15, name:"Blinking Exercise", cat:"Eye Relief", reps:"10 reps each break",
    color:"#E8F0E9", catColor:"#4A6741",
    why:"Reduces dry eye caused by reduced blink rate during screen use.",
    steps:["Close eyes slowly and fully.","Hold for 1 second.","Open fully and slowly. Repeat 10 times."],
  },
  {
    id:16, name:"Near-Far Focus Shift", cat:"Eye Relief", reps:"10 reps",
    color:"#E8F0E9", catColor:"#4A6741",
    why:"Trains ciliary muscle flexibility strained by prolonged close-focus work.",
    steps:["Focus on a near object for 5 seconds.","Shift gaze to a distant object for 5 seconds.","Alternate 10 times."],
  },
];

const allExercises = {
  "Cervical":[
    {id:1,name:"Cervical Flexion",reps:"10 reps",color:"#B5CEB8",steps:["Slowly bring your chin toward your chest.","Return to the starting position.","Perform 10 repetitions in a slow, controlled manner."]},
    {id:2,name:"Cervical Extension",reps:"10 reps",color:"#B5CEB8",steps:["Slowly look upward toward the ceiling.","Return to the starting position.","Perform 10 repetitions. Keep movement pain-free."]},
    {id:3,name:"Cervical Side Flexion",reps:"10 reps each side",color:"#B5CEB8",steps:["Tilt your ear toward your shoulder on one side.","Hold briefly, feel the gentle stretch.","Return to centre and repeat on the other side.","Perform 10 repetitions each side."]},
    {id:4,name:"Cervical Rotation",reps:"10 reps each side",color:"#B5CEB8",steps:["Slowly rotate your head to the left.","Keep the movement controlled and pain-free.","Return to centre, then rotate to the right.","Perform 10 repetitions each side."]},
    {id:5,name:"Chin Tuck",reps:"10 reps · hold 5s",color:"#B5CEB8",steps:["Sit or stand upright with eyes level.","Pull your chin straight backward — like making a double chin.","Do not bend your neck forward.","Hold for 5 seconds, then release.","Repeat 10 times."]},
  ],
  "Stretching":[
    {id:6,name:"Upper Trapezius Stretch",reps:"3 × 30s each side",color:"#EDD9BE",steps:["Tilt your head to one side, ear toward shoulder.","Use your hand to apply gentle downward pressure.","Keep the opposite shoulder relaxed and down.","Hold 30 seconds. Repeat 3 times each side."]},
    {id:7,name:"Levator Scapulae Stretch",reps:"3 × 30s each side",color:"#EDD9BE",steps:["Turn your head 45° to one side.","Look down toward your armpit.","Apply gentle pressure with your hand on the back of your head.","Hold 30 seconds. Repeat 3 times each side."]},
    {id:8,name:"Seated Pectoral Stretch",reps:"3 × 30s",color:"#EDD9BE",steps:["Sit or stand upright.","Interlock your fingers behind your back.","Gently pull shoulders back and open your chest.","Lift your chest slightly, keeping back straight.","Hold 30 seconds. Repeat 3 times."]},
  ],
  "Strengthening":[
    {id:9,name:"Scapular Retractions",reps:"10 reps · hold 5s",color:"#DCF0F7",steps:["Sit upright with arms relaxed at sides.","Pull shoulder blades back and down together.","Keep your neck relaxed — do not shrug.","Hold for 5 seconds, then release slowly.","Repeat 10 times."]},
    {id:10,name:"Shoulder Shrugs",reps:"10 reps",color:"#DCF0F7",steps:["Sit or stand upright.","Lift shoulders up toward your ears.","Hold briefly at the top.","Release slowly and fully downward.","Repeat 10 times."]},
    {id:11,name:"Seated Thoracic Extension",reps:"10 reps",color:"#DCF0F7",steps:["Sit upright with hands interlaced behind your head.","Gently extend your upper back backward over the chair.","Keep your lower back stable.","Return to starting position slowly.","Repeat 10 times."]},
    {id:12,name:"Seated Thoracic Mobility",reps:"10 reps",color:"#DCF0F7",steps:["Sit upright with hands on thighs.","Round your upper back forward slowly.","Then straighten to an upright position.","Move through full range. 10 repetitions."]},
    {id:13,name:"Isometric Neck Strengthening",reps:"10 reps each direction · hold 5s",color:"#DCF0F7",steps:["Place hand on forehead. Press head forward into hand. Hold 5s. Don't let neck move.","Place hand on back of head. Press backward. Hold 5s.","Place hand on side of head. Press sideways. Hold 5s each side.","Repeat 10 times in each direction."]},
  ],
  "Eye Relief":[
    {id:14,name:"20-20-20 Rule",reps:"Every 20 min",color:"#E8F0E9",steps:["Every 20 minutes, pause your screen use.","Look at an object approximately 20 feet (6 metres) away.","Hold your gaze for a full 20 seconds.","Return to your screen. Set a reminder if needed."]},
    {id:15,name:"Blinking Exercise",reps:"10 reps each break",color:"#E8F0E9",steps:["During each screen break, close your eyes slowly and fully.","Hold closed for one second.","Open your eyes fully and slowly.","Repeat 10 times."]},
    {id:16,name:"Near-Far Focus Shift",reps:"10 reps",color:"#E8F0E9",steps:["Hold one finger about 10cm in front of your face.","Focus on your finger for 5 seconds.","Shift gaze to a distant object for 5 seconds.","Alternate near and far 10 times."]},
  ],
};

const leaderboard=[
  {rank:1,av:"🦁",name:"Aryan K.",streak:22,me:false,bg:"#EDD9BE"},
  {rank:2,av:"🌸",name:"Priya M.",streak:18,me:false,bg:"#E8F0E9"},
  {rank:3,av:"⚡",name:"Zoey (You)",streak:5,me:true,bg:"#B5CEB833"},
  {rank:4,av:"🌊",name:"Rohan S.",streak:4,me:false,bg:"#DCF0F7"},
  {rank:5,av:"🔥",name:"Sia T.",streak:3,me:false,bg:"#F7F0E6"},
];
const friends=[
  {av:"🌸",name:"Priya M.",streak:18,status:"behind"},
  {av:"🌊",name:"Rohan S.",streak:4,status:"ahead"},
  {av:"🎯",name:"Karan B.",streak:5,status:"tied"},
];
const badges=[
  {icon:"🔥",label:"5-day streak",earned:true},
  {icon:"📱",label:"First session",earned:true},
  {icon:"🧘",label:"All exercises",earned:true},
  {icon:"⭐",label:"Perfect score",earned:false},
  {icon:"🏆",label:"30-day club",earned:false},
  {icon:"👥",label:"Beat a friend",earned:false},
  {icon:"💎",label:"100 days",earned:false},
  {icon:"🌟",label:"Posture Pro",earned:false},
];
const titles=["Neck Newbie","Posture Pro","Spine Savant","Neck Guardian","Movement Master"];
const earnedTitles=[0,1];

// ── HOME ──
function HomeTab({ goToEx, doneIds, setDoneIds }) {
  const doneTodayCount = todayExercises.filter(e => doneIds.includes(e.id)).length;

  return (
    <div>
      {/* HEADER */}
      <div className="home-hdr a1">
        <div className="topbar">
          <div className="logobadge">NudgeUp ↑</div>
          <div className="avatar">👤</div>
        </div>
        <div className="h-greeting">Saturday, 18 April</div>
        <div className="h-name">Hey, <span>Zoey</span> 👋</div>
        <div className="h-sub">Your posture scan is complete for today.</div>
        <div className="score-row">
          <div className="score-num">82</div>
          <div className="score-info">
            <div className="score-lbl">Posture score</div>
            <div className="score-interp">Superb 🌟</div>
            <div className="score-delta">↑ +6 vs yesterday</div>
          </div>
          <svg width="52" height="52" viewBox="0 0 52 52">
            <circle cx="26" cy="26" r="21" fill="none" stroke="rgba(255,255,255,.25)" strokeWidth="4"/>
            <circle cx="26" cy="26" r="21" fill="none" stroke="#fff" strokeWidth="4"
              strokeDasharray={2*Math.PI*21}
              strokeDashoffset={2*Math.PI*21*(1-0.82)}
              strokeLinecap="round" transform="rotate(-90 26 26)"/>
          </svg>
        </div>
        <div className="live-pill"><div className="pulse"/>MONITORING ACTIVE</div>
      </div>

      <div style={{padding:"12px 16px 0",display:"flex",flexDirection:"column",gap:10}}>

        {/* STREAK */}
        <div className="streak-pill a2">
          <div className="s-left">
            <span style={{fontSize:16}}>⚡</span>
            <span className="s-txt">5-day streak</span>
          </div>
          <div className="s-right">Next nudge in <span>18 min</span></div>
        </div>

        {/* NUDGE */}
        <div className="nudge-home a3">
          <div className="nudge-top-row">
            <div className="nudge-icon">📱</div>
            <div>
              <div className="nudge-lbl">Today's nudge</div>
              <div className="nudge-txt">Raise your phone to eye level right now</div>
            </div>
          </div>
          <div className="nudge-tags">
            <div className="ntag">🟢 Easy</div>
            <div className="ntag">⏱ 5 seconds</div>
            <div className="ntag">No equipment</div>
          </div>
          <div className="nudge-why">
            <div className="nw-lbl">Why this works</div>
            <div className="nw-txt">Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine. Raising your phone neutralises that force instantly.</div>
          </div>
        </div>

        {/* TODAY'S EXERCISES */}
        <div className="a4">
          <div className="sec-divider">
            <div className="sec-divider-line"/>
            <div className="sec-divider-txt">Today's exercises</div>
            <div className="sec-divider-line"/>
          </div>
        </div>

        <div className="today-ex a4">
          <div className="today-ex-hdr">
            <div>
              <div className="today-ex-title">Your assigned routine</div>
              <div className="today-ex-sub">Based on today's scan · {doneTodayCount}/{todayExercises.length} completed</div>
            </div>
            <span style={{fontSize:11,color:"var(--sage)",fontWeight:600,cursor:"pointer"}} onClick={() => goToEx(null)}>See all →</span>
          </div>
          {todayExercises.map(ex => (
            <div key={ex.id} className="tex-item" onClick={() => goToEx(ex.id)}>
              <div className="tex-dot" style={{background:ex.color,border:`2px solid ${ex.color}`}}/>
              <div className={`tex-name ${doneIds.includes(ex.id)?"done":""}`}>{ex.name}</div>
              <div className="tex-cat">{ex.cat}</div>
              <div className="tex-arr">{doneIds.includes(ex.id)?"✓ done":"→"}</div>
            </div>
          ))}
        </div>

        {/* RECOMMENDED SECTION */}
        <div className="a5">
          <div className="sec-divider">
            <div className="sec-divider-line"/>
            <div className="sec-divider-txt">Recommended for you</div>
            <div className="sec-divider-line"/>
          </div>
        </div>

        {/* DETECTION BANNER */}
        <div className="detect-banner a5">
          <div className="detect-icon">{detectedProblem.icon}</div>
          <div className="detect-info">
            <div className="detect-lbl">Detected today</div>
            <div className="detect-title">{detectedProblem.title}</div>
            <div className="detect-sub">{detectedProblem.sub}</div>
            <div className="detect-badge">{detectedProblem.badge}</div>
          </div>
        </div>

        {/* RECOMMENDED EXERCISE CARDS */}
        <div style={{display:"flex",flexDirection:"column",gap:8,paddingBottom:8}}>
          {recommendedExercises.map((ex, i) => (
            <div key={ex.id} className={`rec-card a${5+Math.min(i,3)}`} onClick={() => goToEx(ex.id)}>
              <div className="rec-card-inner">
                <div className="rec-num" style={{background:ex.color,color:"var(--moss)"}}>
                  {i+1}
                </div>
                <div className="rec-info">
                  <div className="rec-name">{ex.name}</div>
                  <div className="rec-meta">
                    <div className="rec-cat" style={{background:ex.color,color:ex.catColor}}>{ex.cat}</div>
                    <div className="rec-reps">{ex.reps}</div>
                  </div>
                </div>
                <div className="rec-arr">→</div>
              </div>
              <div className="rec-why">
                <span style={{fontSize:12}}>💡</span>
                <div className="rec-why-txt">{ex.why}</div>
              </div>
            </div>
          ))}
        </div>

      </div>
    </div>
  );
}

// ── EXERCISES ──
function ExercisesTab({ highlightId, doneIds, setDoneIds }) {
  const cats = Object.keys(allExercises);
  const [activeCat, setActiveCat] = useState(() => {
    if (!highlightId) return "Cervical";
    for (const [cat, exs] of Object.entries(allExercises)) {
      if (exs.find(e => e.id === highlightId)) return cat;
    }
    return "Cervical";
  });
  const [openId, setOpenId] = useState(highlightId || null);
  const list = allExercises[activeCat];

  return (
    <div>
      <div className="ex-hdr a1">
        <div className="topbar">
          <div className="logobadge">NudgeUp ↑</div>
          <div className="avatar">👤</div>
        </div>
        <div className="ex-hdr-title">Your exercises</div>
        <div className="ex-hdr-sub">Evidence-based · assigned to your posture profile</div>
        <div className="cat-tabs">
          {cats.map(c => (
            <div key={c} className={`cat-tab ${activeCat===c?"active":"inactive"}`}
              onClick={() => { setActiveCat(c); setOpenId(null); }}>
              {c}
            </div>
          ))}
        </div>
      </div>
      <div className="ex-list">
        {list.map((ex, i) => (
          <div key={ex.id} className="ex-card a2">
            <div className="ex-card-top" onClick={() => setOpenId(openId===ex.id?null:ex.id)}>
              <div className="ex-num" style={{background:ex.color+"66",color:"var(--bark)"}}>{i+1}</div>
              <div style={{flex:1}}>
                <div className="ex-card-name">
                  {ex.name}
                  {doneIds.includes(ex.id) && <span className="ex-done-badge">Done ✓</span>}
                </div>
                <div className="ex-card-sub">{ex.reps}</div>
              </div>
              <div className={`ex-chev ${openId===ex.id?"open":""}`}>▼</div>
            </div>
            {openId===ex.id && (
              <div className="ex-expand">
                <div className="ex-reps">
                  <span style={{fontSize:14}}>🔁</span>
                  <span className="ex-reps-txt">{ex.reps}</span>
                </div>
                <div className="ex-steps">
                  {ex.steps.map((s,si) => (
                    <div key={si} className="ex-step">
                      <div className="ex-step-num">{si+1}</div>
                      <div className="ex-step-txt">{s}</div>
                    </div>
                  ))}
                </div>
                {doneIds.includes(ex.id)
                  ? <button className="ex-btn done">✓ Completed</button>
                  : <button className="ex-btn start" onClick={() => setDoneIds(d=>[...d,ex.id])}>Mark as done</button>
                }
              </div>
            )}
          </div>
        ))}
      </div>
      <div style={{height:16}}/>
    </div>
  );
}

// ── PROGRESS ──
function ProgressTab() {
  const [open, setOpen] = useState({});
  const tog = k => setOpen(o => ({...o,[k]:!o[k]}));
  const sections = [
    {key:"week",icon:"📅",iconBg:"#E8F0E9",title:"This week",sub:"Apr 14–18 · Avg score 84",content:(
      <>
        <div className="week-rings">{days.map((d,i)=><div className="day-ring" key={i}><div className={`dc ${dayStates[i]}`}>{dayStates[i]==="done"?"✓":dayStates[i]==="missed"?"!":d}</div><div className="day-lbl">{d}</div></div>)}</div>
        <div className="stats-grid">
          <div className="stat-box"><div className="stat-val">11.4h</div><div className="stat-lbl">Time tracked</div></div>
          <div className="stat-box"><div className="stat-val">9/14</div><div className="stat-lbl">Exercises done</div></div>
          <div className="stat-box"><div className="stat-val">6/7</div><div className="stat-lbl">Nudges acted on</div></div>
          <div className="stat-box"><div className="stat-val">84</div><div className="stat-lbl">Avg posture score</div></div>
        </div>
        <div className="pbars">{[{lbl:"Best day: Wednesday",val:94},{lbl:"Worst day: Thursday",val:61},{lbl:"Exercise completion",val:64}].map((b,i)=><div key={i}><div className="pbar-top"><span className="pbar-lbl">{b.lbl}</span><span className="pbar-val">{b.val}%</span></div><div className="pbar-bg"><div className="pbar-fill" style={{width:`${b.val}%`}}/></div></div>)}</div>
      </>
    )},
    {key:"lb",icon:"🏆",iconBg:"#EDD9BE",title:"Leaderboard",sub:"Your rank: #3 this week",content:<div className="lb-list">{leaderboard.map(u=><div key={u.rank} className={`lb-item ${u.me?"me":""}`}><div className="lb-rank">{u.rank<=2?["🥇","🥈"][u.rank-1]:u.rank}</div><div className="lb-av" style={{background:u.bg}}>{u.av}</div><div className="lb-name">{u.name}</div><div className="lb-streak">{u.streak} 🔥</div></div>)}</div>},
    {key:"friends",icon:"👥",iconBg:"#DCF0F7",title:"Friends' streaks",sub:"3 friends connected",content:<div className="friends-list">{friends.map((f,i)=><div key={i} className="friend-item"><div className="friend-av">{f.av}</div><div><div className="friend-name">{f.name}</div><div className="friend-streak">⚡ {f.streak}-day streak</div></div><div className={`f-status ${f.status}`}>{f.status==="ahead"?"You're ahead":f.status==="behind"?"You're behind":"Tied!"}</div></div>)}<div style={{fontSize:11,color:"var(--muted)",textAlign:"center",marginTop:4,cursor:"pointer"}}>+ Invite friends</div></div>},
    {key:"rewards",icon:"🎁",iconBg:"#F7F0E6",title:"Rewards",sub:"420 pts · Posture Pro title",content:(
      <>
        <div className="pts-card"><div><div className="pts-val">420 pts</div><div className="pts-lbl">Earned this week</div></div><button className="pts-btn">Redeem →</button></div>
        <div style={{marginTop:14}}><div style={{fontSize:10,fontWeight:600,letterSpacing:".08em",textTransform:"uppercase",color:"var(--muted)",marginBottom:8}}>Your titles</div><div className="titles-row">{titles.map((t,i)=><div key={i} className={`title-chip ${earnedTitles.includes(i)?"active":"locked"}`}>{t}</div>)}</div></div>
        <div style={{marginTop:14}}><div style={{fontSize:10,fontWeight:600,letterSpacing:".08em",textTransform:"uppercase",color:"var(--muted)",marginBottom:8}}>Badges</div><div className="badge-grid">{badges.map((b,i)=><div key={i} className="badge"><div className={`badge-icon ${b.earned?"earned":"locked"}`}>{b.icon}</div><div className="badge-lbl">{b.label}</div></div>)}</div></div>
        <div style={{marginTop:14}}><div style={{fontSize:10,fontWeight:600,letterSpacing:".08em",textTransform:"uppercase",color:"var(--muted)",marginBottom:8}}>Sloucho's look</div><div className="sloucho-card"><div className="sloucho-av">🐢</div><div><div className="sloucho-name">Sloucho</div><div className="sloucho-outfit">Sage Hoodie · Level 3</div></div><button className="sloucho-btn">Customise</button></div></div>
      </>
    )},
  ];
  return (
    <div>
      <div className="prog-hdr a1">
        <div className="topbar"><div className="logobadge">NudgeUp ↑</div><div className="avatar">👤</div></div>
        <div style={{marginTop:4,fontSize:12,color:"rgba(255,255,255,.55)",letterSpacing:".07em",textTransform:"uppercase",marginBottom:6}}>Progress & Rewards</div>
        <div className="prog-score-row"><div className="prog-big">82</div><div className="prog-info"><div className="prog-lbl">Today's score</div><div className="prog-interp">Superb posture 🌟</div><div className="prog-sub">↑ +6 vs yesterday</div></div></div>
      </div>
      <div className="accord-wrap">
        {sections.map(s=>(
          <div key={s.key} className="accord a2">
            <div className="accord-row" onClick={()=>tog(s.key)}>
              <div className="accord-left"><div className="accord-icon" style={{background:s.iconBg}}>{s.icon}</div><div><div className="accord-title">{s.title}</div><div className="accord-sub">{s.sub}</div></div></div>
              <div className={`accord-chev ${open[s.key]?"open":""}`}>▼</div>
            </div>
            {open[s.key]&&<div className="accord-body">{s.content}</div>}
          </div>
        ))}
        <div style={{height:8}}/>
      </div>
    </div>
  );
}

// ── ROOT ──
export default function App() {
  const [tab, setTab] = useState("home");
  const [highlightExId, setHighlightExId] = useState(null);
  const [doneIds, setDoneIds] = useState([5,9]);

  const goToEx = (id) => {
    setHighlightExId(id);
    setTab("exercises");
  };

  return (
    <>
      <style>{S}</style>
      <div className="app">
        <div className="screen">
          {tab==="home" && <HomeTab goToEx={goToEx} doneIds={doneIds} setDoneIds={setDoneIds}/>}
          {tab==="exercises" && <ExercisesTab highlightId={highlightExId} doneIds={doneIds} setDoneIds={setDoneIds}/>}
          {tab==="progress" && <ProgressTab/>}
        </div>
        <div className="nav">
          {[{key:"home",icon:"🏠",label:"Home"},{key:"exercises",icon:"🧘",label:"Exercises"},{key:"progress",icon:"📊",label:"Progress"}].map(n=>(
            <div key={n.key} className={`ni ${tab===n.key?"active":""}`} onClick={()=>setTab(n.key)}>
              <div className="ni-icon">{n.icon}</div>
              <div className="ni-lbl">{n.label}</div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
