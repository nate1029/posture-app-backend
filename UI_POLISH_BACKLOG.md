# NudgeUp — UI Polish Backlog

**Phase 1 (this doc): catalog observant UI bugs — cosmetic/interaction issues that don't
break functionality but read as "unpolished."**
**Phase 2 (later): fix each item below.**

Nothing here affects app logic, permissions, or Play policy. These are purely
visual / interaction-feel issues.

---

## BUG-01 — "NudgeUp ↑" pill is a different size on every tab

The top-left brand pill (same green box on all three main tabs) renders its text at a
**different font size per tab**, so the pill looks visibly larger/smaller as you move
between tabs. The box padding & shape are identical — only the text size drifts.

| Tab | File:Line | `fontSize` |
|-----|-----------|-----------|
| Home | [MainActivity.kt:500](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:500) | **15.sp** |
| Progress / Rewards | [MainActivity.kt:835](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:835) | **14.sp** |
| Exercises | [ExercisesScreen.kt:148](NeckGuardApp/app/src/main/java/com/example/neckguard/ui/ExercisesScreen.kt:148) | **17.sp** |

**Expected:** one consistent size everywhere (pick one, e.g. 15.sp).
**Phase-2 fix:** extract a single `NudgeUpBrandPill()` composable and use it on all three
tabs so the pill can never drift again.
**Severity:** low (cosmetic), but very noticeable side-by-side.

---

## BUG-02 — Settings gear icon is a different shape, glyph, and container per tab

The top-right settings button is built **three different ways**. Home uses a real Material
vector icon on a white circle; the other two use a raw emoji "⚙" on an earth-pale circle at
two different sizes. Result: the gear looks like a different button depending on the tab.

| Tab | File:Line | Glyph | Inner size | Circle bg | Border | A11y label |
|-----|-----------|-------|-----------|-----------|--------|-----------|
| Home | [MainActivity.kt:502-503](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:502) | `Icon(Icons.Default.Settings)` | 17.dp | `FinalWhite` | `FinalMist` | "Settings" ✓ |
| Progress / Rewards | [MainActivity.kt:835](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:835) | emoji `"⚙"` | 15.sp | `FinalEarthPale` | `FinalEarthPale` | none ✗ |
| Exercises | [ExercisesScreen.kt:150-151](NeckGuardApp/app/src/main/java/com/example/neckguard/ui/ExercisesScreen.kt:150) | emoji `"⚙"` | 18.sp | `FinalEarthPale` | `FinalEarthPale` | none ✗ |

**Differences that read as "wrong":** vector icon vs emoji (emoji renders differently per
device/OS), 3 different inner sizes, different circle color, different border color.
**Expected:** identical settings button on every tab.
**Phase-2 fix:** extract one `SettingsIconButton(onClick)` composable using the Material
`Icons.Default.Settings` vector (not emoji) with a fixed size, color, and `contentDescription`.
The emoji versions also silently lose the accessibility label — fixed for free by consolidating.
**Severity:** low (cosmetic + minor a11y).

---

## BUG-03 — Exercises: can't swipe between exercise categories/folders

In the Exercises tab the category tabs (Cervical Movements, Stretching, Strengthening,
Eye Relief) are **tap-only**. You cannot swipe left/right on the list to move between
categories, which is the expected phone gesture.

- Tabs: `LazyRow` of clickable `Box`es — [ExercisesScreen.kt:160-177](NeckGuardApp/app/src/main/java/com/example/neckguard/ui/ExercisesScreen.kt:160)
- List: a single `LazyColumn` bound to the active category — [ExercisesScreen.kt:183](NeckGuardApp/app/src/main/java/com/example/neckguard/ui/ExercisesScreen.kt:183)
- There is no `HorizontalPager`, so no horizontal swipe gesture exists.

**Expected:** swiping the exercise list horizontally changes the category (and the active
tab highlight follows), like a standard tabbed pager.
**Phase-2 fix:** wrap the per-category list in a `HorizontalPager` (page count = number of
categories) and sync it two-way with the tab row: tapping a tab animates the pager, swiping
the pager updates the selected tab. Also consider auto-scrolling the `LazyRow` so the active
tab stays visible when it changes.
**Severity:** medium (interaction feel — this is the one that most "isn't how apps work").

---

## BUG-04 — "15 Seconds (Testing)" interval is shipping in production Settings

The Check Interval list in Settings includes a developer testing option that real users can
select and get pinged every 15 seconds.

- [MainActivity.kt:970](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:970)
  — `listOf(15_000L to "15 Seconds (Testing)", 15 * 60 * 1000L to "15 Minutes", 30 * 60 * 1000L to "30 Minutes")`

**Expected:** no testing-only option in a production build.
**Phase-2 fix:** gate it behind `BuildConfig.DEBUG` (only add the 15s entry in debug), or
remove it entirely.
**Severity:** medium — a real user picking this gets a spam-y experience and it looks unfinished.

---

## BUG-05 — Home header subtitle is hardcoded and contradicts a high score

The dashboard header computes a `statusTitle` ("Superb 🌟" / "Good 👍" / "Needs Work ⚠️")
based on score, but the sentence under it is **static**.

- Status badge (adapts): [MainActivity.kt:529-531](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:529)
- Static subtitle: [MainActivity.kt:534](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:534)
  — always `"Small adjustments can move you to \"Good\" today. Keep going."`

So a user with a **Superb (>80)** score is told to "move you to Good" — i.e. it reads as a
downgrade and contradicts the badge right above it.
**Expected:** the subtitle should match the score tier (praise for Superb, encouragement for
Needs Work).
**Phase-2 fix:** branch the subtitle on the same score thresholds used for `statusTitle`.
**Severity:** low-medium (content correctness).

---

## BUG-06 — "NEXT NUDGE" shows a frozen `MM:00` that looks like a live countdown

The streak card renders next-nudge time as `"$mm:00"` (minutes padded to 2 digits, seconds
hardcoded to `00`).

- [MainActivity.kt:755-756](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:755)

Two issues: (1) the `:00` seconds never tick, so it looks like a countdown timer that's stuck;
(2) for intervals ≥ 100 min (custom / 90-min rounding) the `MM:SS` shape breaks (e.g. `120:00`).
**Expected:** either a clearly-labelled relative value ("in 25 min") or a real ticking countdown.
**Phase-2 fix:** render as "in {mins} min" text, or drive a real countdown; drop the fake `:00`.
**Severity:** low (clarity).

---

## BUG-07 — Interval options don't match between Onboarding and Settings

The two places a user sets their check interval offer **different menus**, so a choice made in
onboarding can't be reselected in Settings.

- Onboarding: 15 / 30 / 45 / 60 / 90 min —
  [OnboardingScreen.kt:269-275](NeckGuardApp/app/src/main/java/com/example/neckguard/ui/OnboardingScreen.kt:269)
- Settings: 15 sec / 15 min / 30 min + Custom —
  [MainActivity.kt:970](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt:970)

A user who picks "45 / 60 / 90 min" in onboarding then opens Settings and finds their value
only under the "Custom" fallback, with none of the radio presets highlighted.
**Expected:** one shared, consistent set of interval presets in both places.
**Phase-2 fix:** extract the interval options to a single source of truth and reuse in both
screens.
**Severity:** low.

---

## BUG-08 — Bottom nav bar doesn't reach the screen edge (gap below it)

The bottom bar (Home / Progress / Exercises) floats above the system-navigation area, leaving
a strip below it where the scrolling screen content shows through. Caused by
`.navigationBarsPadding()` on the `NavigationBar`, which pushes the whole bar up instead of
letting the bar's own container fill down to the bottom edge.

- [MainActivity.kt BottomNavBar](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt)

**Expected:** the white bar fills to the very bottom of the screen; nav items sit above the
system nav; no content peeks through underneath.
**Phase-2 fix:** remove `.navigationBarsPadding()` and rely on `NavigationBar`'s built-in
`windowInsets` (default = bottom system-bars), which draws the container behind the system nav
while insetting the items.
**Severity:** medium (looks broken while scrolling).

---

## Related minor notes (confirm intentional in Phase 2)

- **Header gradients differ per tab** — Home is a 3-stop `Moss→Sage→SageLight`
  ([MainActivity.kt](NeckGuardApp/app/src/main/java/com/example/neckguard/MainActivity.kt)),
  Progress is a 2-stop `Sage→SageLight`, Exercises is dark `Bark→BarkSoft`. The dark
  Exercises header is likely deliberate theming; the Home-vs-Progress difference may or may
  not be. Decide whether these should align.
- **Duplicate color token definitions** — `FinalBark`, `FinalSage`, etc. are declared in
  **both** `MainActivity.kt` and `ExercisesScreen.kt`. Not a visible bug, but it's how the
  per-tab drift above crept in (each file evolves its own copy). Phase-2 cleanup: move all
  `Final*` tokens into one shared file (e.g. `ui/theme/Color.kt`) and delete the duplicates.

---

*Generated Phase 1 — catalog only, no code changed. Values verified against source at time
of writing.*
