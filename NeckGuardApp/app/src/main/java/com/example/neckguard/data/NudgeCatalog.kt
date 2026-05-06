package com.example.neckguard.data

import com.example.neckguard.ui.NudgeData
import kotlin.random.Random

object NudgeCatalog {
    val tips = listOf(
        NudgeData("Raise your phone to eye level.", listOf("🟢 Tip"), "Your neck isn't built to look down for hours."),
        NudgeData("Switch hands when you scroll.", listOf("🟢 Tip"), "One side shouldn't take all the damage."),
        NudgeData("Every time you open Instagram, check your chin first..", listOf("🟢 Tip"), ""),
        NudgeData("Voice note that long reply.", listOf("🟢 Tip"), "Saves your neck and honestly sounds cooler."),
        NudgeData("Brightness too low means you're leaning in.", listOf("🟢 Tip"), "Turn it up."),
        NudgeData("Two thumbs when texting, not one.", listOf("🟢 Tip"), "40% less neck strain, same message sent."),
        NudgeData("Put your phone on a surface and read it.", listOf("🟢 Tip"), "Not in your lap."),
        NudgeData("Lying down scrolling? Prop your elbow.", listOf("🟢 Tip"), "Don't float your arm all night."),
        NudgeData("Every 20 minutes, look at something far away for 20 seconds.", listOf("🟢 Tip"), "Non-negotiable."),
        NudgeData("Blink slowly ten times right now.", listOf("🟢 Tip"), "Your eyes are probably drying out."),
        NudgeData("Sit all the way back in your chair.", listOf("🟢 Tip"), "The backrest exists for a reason."),
        NudgeData("Feet flat on the floor.", listOf("🟢 Tip"), "You can't fix your neck with a twisted base."),
        NudgeData("Keyboard too far? You're rounding forward to reach it.", listOf("🟢 Tip"), "Pull it closer."),
        NudgeData("Screen at eye level, always.", listOf("🟢 Tip"), "Stack books under your laptop if you have to."),
        NudgeData("Stand up every 30 minutes.", listOf("🟢 Tip"), "Even for 60 seconds. Your spine will remember it."),
        NudgeData("Shoulders down, not up near your ears.", listOf("🟢 Tip"), "Reset them right now."),
        NudgeData("Chin tuck.", listOf("🟢 Tip"), "Pull it straight back, hold five seconds. Do it three times. Do it now."),
        NudgeData("Roll your shoulders back twice.", listOf("🟢 Tip"), "That's the whole tip."),
        NudgeData("Drop your ear to your shoulder, hold fifteen seconds each side..", listOf("🟢 Tip"), ""),
        NudgeData("Jaw clenching means your neck is tensing too.", listOf("🟢 Tip"), "Unclench right now."),
        NudgeData("Three deep breaths.", listOf("🟢 Tip"), "Shallow breathing tightens the muscles around your neck."),
        NudgeData("Gently nod yes, then no, five times each.", listOf("🟢 Tip"), "Takes thirty seconds."),
        NudgeData("Close your eyes and relax your face completely.", listOf("🟢 Tip"), "Facial tension travels straight to your neck."),
        NudgeData("Interlace your hands behind your head and open your elbows back slowly..", listOf("🟢 Tip"), ""),
        NudgeData("Stand up, reach both arms overhead, hold ten seconds.", listOf("🟢 Tip"), "Full reset."),
        NudgeData("Near-far focus shift — finger close, then something far, ten times.", listOf("🟢 Tip"), "Thirty seconds for your eyes."),
        NudgeData("Hold your phone like you're FaceTiming someone important.", listOf("🟢 Tip"), "Because your spine is."),
        NudgeData("Set a reminder right now for every twenty minutes.", listOf("🟢 Tip"), "Don't trust yourself to remember."),
        NudgeData("If your chin is jutting forward your monitor is too low.", listOf("🟢 Tip"), "Not a personality trait."),
        NudgeData("Good posture feels weird at first because bad posture has been your normal.", listOf("🟢 Tip"), "Keep going anyway."),
    )

    val facts = listOf(
        NudgeData("Your head weighs 5kg.", listOf("📘 Fact"), "At 45 degrees forward tilt, your spine feels 22kg of force."),
        NudgeData("You check your phone 96 times a day on average.", listOf("📘 Fact"), "How many times did you check your posture?"),
        NudgeData("Cervical discs have no blood supply.", listOf("📘 Fact"), "Movement is their only nutrition."),
        NudgeData("You blink 66% less when looking at a screen.", listOf("📘 Fact"), "Your eyes are drying out in real time."),
        NudgeData("Sitting is harder on your spine than standing.", listOf("📘 Fact"), "The sofa is a trap."),
        NudgeData("Neck muscles fatigue after just 20 minutes of sustained forward posture..", listOf("📘 Fact"), ""),
        NudgeData("The average scroll session is 17 minutes of zero posture change..", listOf("📘 Fact"), ""),
        NudgeData("Chronic forward head posture can reduce your lung capacity by up to 30%..", listOf("📘 Fact"), ""),
        NudgeData("Text neck was named as a clinical diagnosis in 2008.", listOf("📘 Fact"), "It's only gotten worse since."),
        NudgeData("Your spine is most vulnerable in the first 30 minutes after waking up.", listOf("📘 Fact"), "Morning scroll is a bad start."),
        NudgeData("Weak deep neck flexors are present in almost every single case of chronic neck pain..", listOf("📘 Fact"), ""),
        NudgeData("Three weeks of consistent exercise starts rewiring your posture patterns.", listOf("📘 Fact"), "Three weeks."),
        NudgeData("Slouching raises cortisol.", listOf("📘 Fact"), "Bad posture is literally triggering your stress response."),
        NudgeData("Sitting upright increases testosterone and lowers cortisol.", listOf("📘 Fact"), "Posture affects your mood more than you think."),
        NudgeData("Your body has 650 muscles.", listOf("📘 Fact"), "26 of them exist specifically to stabilise your spine."),
        NudgeData("Holding your phone lower than eye level is linked to 3x more neck pain.", listOf("📘 Fact"), "The data is not subtle."),
        NudgeData("Spine damage from smartphones is now showing up in teenagers.", listOf("📘 Fact"), "Not 50-year-olds. Teenagers."),
        NudgeData("The upper trapezius is the most overloaded muscle in people who use screens regularly..", listOf("📘 Fact"), ""),
        NudgeData("People spend over 2,400 hours on screens per year.", listOf("📘 Fact"), "Your neck is doing that shift with you."),
        NudgeData("Eye strain is not your eyes hurting.", listOf("📘 Fact"), "It's your eyes quietly failing to keep up."),
        NudgeData("Blue light doesn't damage your eyes.", listOf("📘 Fact"), "Not blinking does. Know the actual enemy."),
        NudgeData("Reading on a screen is 25% slower than reading on paper.", listOf("📘 Fact"), "Your brain is working harder than you realise."),
        NudgeData("Good posture for one hour cannot undo three hours of slouching.", listOf("📘 Fact"), "The math is brutal."),
        NudgeData("The muscle that tightens when you're stressed connects your neck to your shoulder blade.", listOf("📘 Fact"), "Stress lives there."),
        NudgeData("Texting with one thumb produces 40% more neck strain than using both.", listOf("📘 Fact"), "Small habit, real difference."),
        NudgeData("Digital eye strain affects 65% of regular screen users.", listOf("📘 Fact"), "It's not rare. It's almost everyone."),
        NudgeData("Your eyes use more energy per gram of tissue than almost any other organ.", listOf("📘 Fact"), "They need rest too."),
        NudgeData("Gen Z sleeps less than they scroll.", listOf("📘 Fact"), "The spine gets no recovery window."),
        NudgeData("Most people have had bad posture for so long that good posture feels uncomfortable.", listOf("📘 Fact"), "That discomfort is progress."),
        NudgeData("One hour of sustained static posture, even in a perfect position, is still harmful.", listOf("📘 Fact"), "Movement is the point."),
    )

    val allNudges = tips + facts

    // On days where PostureScore is below 60, pull from the subset of cards that match the worst detected parameter
    val forwardHeadNudges = listOf(tips[16], tips[17], tips[18], facts[0], facts[5], facts[10], facts[15])
    val screenDistanceNudges = listOf(tips[8], tips[9], tips[25], facts[3], facts[20], facts[25])
    val lateralTiltNudges = listOf(tips[18], tips[21], facts[14], facts[23])
    val phoneAngleNudges = listOf(tips[0], tips[1], tips[2], tips[6], facts[6], facts[16])
    val breakFrequencyNudges = listOf(tips[14], tips[27], facts[2], facts[6], facts[29])

    fun getDailyNudge(score: Int, worstParameter: String?, dayOfYear: Int): NudgeData {
        val r = Random(dayOfYear) // shift randomly every day, but stable for that day
        return if (score >= 60 || worstParameter == null) {
            allNudges.random(r)
        } else {
            when (worstParameter) {
                "forward_head" -> forwardHeadNudges.random(r)
                "screen_distance" -> screenDistanceNudges.random(r)
                "lateral_tilt" -> lateralTiltNudges.random(r)
                "phone_angle" -> phoneAngleNudges.random(r)
                "break_frequency" -> breakFrequencyNudges.random(r)
                else -> allNudges.random(r)
            }
        }
    }
}
