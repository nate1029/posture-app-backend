import re

with open('read_docx.py', 'r') as f: pass # just making sure

text = """30 TIPS
1. Raise your phone to eye level. Your neck isn't built to look down for hours.
2. Switch hands when you scroll. One side shouldn't take all the damage.
3. Every time you open Instagram, check your chin first.
4. Voice note that long reply. Saves your neck and honestly sounds cooler.
5. Brightness too low means you're leaning in. Turn it up.
6. Two thumbs when texting, not one. 40% less neck strain, same message sent.
7. Put your phone on a surface and read it. Not in your lap.
8. Lying down scrolling? Prop your elbow. Don't float your arm all night.
9. Every 20 minutes, look at something far away for 20 seconds. Non-negotiable.
10. Blink slowly ten times right now. Your eyes are probably drying out.
11. Sit all the way back in your chair. The backrest exists for a reason.
12. Feet flat on the floor. You can't fix your neck with a twisted base.
13. Keyboard too far? You're rounding forward to reach it. Pull it closer.
14. Screen at eye level, always. Stack books under your laptop if you have to.
15. Stand up every 30 minutes. Even for 60 seconds. Your spine will remember it.
16. Shoulders down, not up near your ears. Reset them right now.
17. Chin tuck. Pull it straight back, hold five seconds. Do it three times. Do it now.
18. Roll your shoulders back twice. That's the whole tip.
19. Drop your ear to your shoulder, hold fifteen seconds each side.
20. Jaw clenching means your neck is tensing too. Unclench right now.
21. Three deep breaths. Shallow breathing tightens the muscles around your neck.
22. Gently nod yes, then no, five times each. Takes thirty seconds.
23. Close your eyes and relax your face completely. Facial tension travels straight to your neck.
24. Interlace your hands behind your head and open your elbows back slowly.
25. Stand up, reach both arms overhead, hold ten seconds. Full reset.
26. Near-far focus shift — finger close, then something far, ten times. Thirty seconds for your eyes.
27. Hold your phone like you're FaceTiming someone important. Because your spine is.
28. Set a reminder right now for every twenty minutes. Don't trust yourself to remember.
29. If your chin is jutting forward your monitor is too low. Not a personality trait.
30. Good posture feels weird at first because bad posture has been your normal. Keep going anyway.

**30 FACTS**
1. Your head weighs 5kg. At 45 degrees forward tilt, your spine feels 22kg of force.
2. You check your phone 96 times a day on average. How many times did you check your posture?
3. Cervical discs have no blood supply. Movement is their only nutrition.
4. You blink 66% less when looking at a screen. Your eyes are drying out in real time.
5. Sitting is harder on your spine than standing. The sofa is a trap.
6. Neck muscles fatigue after just 20 minutes of sustained forward posture.
7. The average scroll session is 17 minutes of zero posture change.
8. Chronic forward head posture can reduce your lung capacity by up to 30%.
9. Text neck was named as a clinical diagnosis in 2008. It's only gotten worse since.
10. Your spine is most vulnerable in the first 30 minutes after waking up. Morning scroll is a bad start.
11. Weak deep neck flexors are present in almost every single case of chronic neck pain.
12. Three weeks of consistent exercise starts rewiring your posture patterns. Three weeks.
13. Slouching raises cortisol. Bad posture is literally triggering your stress response.
14. Sitting upright increases testosterone and lowers cortisol. Posture affects your mood more than you think.
15. Your body has 650 muscles. 26 of them exist specifically to stabilise your spine.
16. Holding your phone lower than eye level is linked to 3x more neck pain. The data is not subtle.
17. Spine damage from smartphones is now showing up in teenagers. Not 50-year-olds. Teenagers.
18. The upper trapezius is the most overloaded muscle in people who use screens regularly.
19. People spend over 2,400 hours on screens per year. Your neck is doing that shift with you.
20. Eye strain is not your eyes hurting. It's your eyes quietly failing to keep up.
21. Blue light doesn't damage your eyes. Not blinking does. Know the actual enemy.
22. Reading on a screen is 25% slower than reading on paper. Your brain is working harder than you realise.
23. Good posture for one hour cannot undo three hours of slouching. The math is brutal.
24. The muscle that tightens when you're stressed connects your neck to your shoulder blade. Stress lives there.
25. Texting with one thumb produces 40% more neck strain than using both. Small habit, real difference.
26. Digital eye strain affects 65% of regular screen users. It's not rare. It's almost everyone.
27. Your eyes use more energy per gram of tissue than almost any other organ. They need rest too.
28. Gen Z sleeps less than they scroll. The spine gets no recovery window.
29. Most people have had bad posture for so long that good posture feels uncomfortable. That discomfort is progress.
30. One hour of sustained static posture, even in a perfect position, is still harmful. Movement is the point.
"""

tips = []
facts = []

lines = text.split('\n')
mode = None
for line in lines:
    line = line.strip()
    if '30 TIPS' in line:
        mode = 'tips'
        continue
    if '30 FACTS' in line:
        mode = 'facts'
        continue
    if not line or not line[0].isdigit():
        continue
    
    # parse "1. Something. Something."
    match = re.match(r'\d+\.\s*(.+)', line)
    if match:
        content = match.group(1).replace('"', '\\"')
        
        # Split by first period to make instruction / reasoning
        parts = content.split('. ', 1)
        if len(parts) == 1:
            parts = content.split('? ', 1)
        
        instr = parts[0] + ('.' if not parts[0].endswith('?') else '?')
        reason = parts[1] if len(parts) > 1 else ""
        
        if mode == 'tips':
            tips.append(f'        NudgeData("{instr}", listOf("🟢 Tip"), "{reason}")')
        else:
            facts.append(f'        NudgeData("{instr}", listOf("📘 Fact"), "{reason}")')

kt_code = f"""package com.example.neckguard.data

import com.example.neckguard.ui.NudgeData
import kotlin.random.Random

object NudgeCatalog {{
    val tips = listOf(
{chr(10).join(t + ',' for t in tips)}
    )

    val facts = listOf(
{chr(10).join(f + ',' for f in facts)}
    )

    val allNudges = tips + facts

    // On days where PostureScore is below 60, pull from the subset of cards that match the worst detected parameter
    val forwardHeadNudges = listOf(tips[16], tips[17], tips[18], facts[0], facts[5], facts[10], facts[15])
    val screenDistanceNudges = listOf(tips[8], tips[9], tips[25], facts[3], facts[20], facts[25])
    val lateralTiltNudges = listOf(tips[18], tips[21], facts[14], facts[23])
    val phoneAngleNudges = listOf(tips[0], tips[1], tips[2], tips[6], facts[6], facts[16])
    val breakFrequencyNudges = listOf(tips[14], tips[27], facts[2], facts[6], facts[29])

    fun getDailyNudge(score: Int, worstParameter: String?, dayOfYear: Int): NudgeData {{
        val r = Random(dayOfYear) // shift randomly every day, but stable for that day
        return if (score >= 60 || worstParameter == null) {{
            allNudges.random(r)
        }} else {{
            when (worstParameter) {{
                "forward_head" -> forwardHeadNudges.random(r)
                "screen_distance" -> screenDistanceNudges.random(r)
                "lateral_tilt" -> lateralTiltNudges.random(r)
                "phone_angle" -> phoneAngleNudges.random(r)
                "break_frequency" -> breakFrequencyNudges.random(r)
                else -> allNudges.random(r)
            }}
        }}
    }}
}}
"""

with open(r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\data\NudgeCatalog.kt', 'w', encoding='utf-8') as f:
    f.write(kt_code)
