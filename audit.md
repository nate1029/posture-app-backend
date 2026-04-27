# NeckGuard — Audit Checklist

> Generated: April 2026  
> Covers: Security audit (full static review) + UI performance items to address after the new UI is built.  
> The performance backend fixes (#1, #3–#10 from the performance audit) have already been applied.

---

## Part 1 — Security Audit

### How to read this

- 🔴 **Critical** — can be exploited today with no special access; fix before shipping
- 🟠 **High** — exploitable with minimal effort or causes policy rejection; fix before TestFlight/Play
- 🟡 **Medium** — real risk but requires specific conditions; fix before public launch
- 🟢 **Low / Info** — hygiene, defence-in-depth, or Play Store policy; fix when convenient

---

## 🔴 Critical

### C-1 · Deep-link OAuth callback accepts unverified JWTs → session fixation / account takeover

**File:** `MainActivity.kt` → `handleAuthIntent()`  
**File:** `AuthScreen.kt` → Google SSO button

**Problems:**
1. You base64-decode the JWT payload and trust the `sub` claim **without verifying the JWT signature**. Any app/webpage can fire `neckguard://callback#access_token=<crafted_token>` and your app will save that as the authenticated identity.
2. No `state` parameter on the Google OAuth URL → classic OAuth CSRF. Attacker can force a victim to be silently logged into the attacker's account.
3. No PKCE code-challenge. The raw access-token appears in the URL fragment (logged by Android), not exchanged server-side.
4. Custom URI scheme (`neckguard://`) is squattable by any installed app; another app can declare the same scheme.

**What to fix:**
- Implement **Authorization Code + PKCE** (Supabase supports it): generate `code_verifier` before opening browser, verify `state` on callback, exchange code server-side.
- After receiving a token, **verify it** by making a Supabase `/auth/v1/user` call before saving to prefs — only commit if the server confirms it.
- Migrate to **Android App Links** (HTTPS verified domain) to replace the custom scheme.
- Fix the `split("=")` parser in `handleAuthIntent` — see M-6.

---

### C-2 · Supabase anon key baked into APK — safety depends entirely on Row-Level Security

**File:** `local.properties` (injected into `BuildConfig`)

The anon key is extractable by anyone who decompiles the APK. This is by Supabase design — **it is safe only if every table has RLS enabled with correct policies.**

**Action required (Supabase dashboard, not code):**
1. Run: `SELECT relname, relrowsecurity FROM pg_class WHERE relnamespace = 'public'::regnamespace;`  
   Every table must show `relrowsecurity = true`.
2. For `user_profiles`: policy must be `auth.uid() = user_id` on SELECT/INSERT/UPDATE/DELETE.
3. For `crash_reports`: see C-3.
4. For any other tables: audit each one.

---

### C-3 · `crash_reports` table accepts anonymous writes → spam / cost DoS

**File:** `SupabaseClient.kt` → `uploadCrashLog()`

Your own code comment confirms this: if `accessToken` is null the upload still goes through without auth. Anyone with your anon key (see C-2) can insert unlimited rows.

**Fix:**
- Require `Authorization: Bearer <token>` on the `crash_reports` table — make it non-nullable in the RLS policy.
- Or add a Supabase Edge Function that rate-limits by IP.

---

### C-4 · `google-services.json` committed to git with Firebase API key

**Commit:** `8d53216 "App stable version 0.2"`  
**File:** `NeckGuardApp/app/google-services.json`  
**Key:** `AIzaSyBCQp5j7NXiura_xs1kliinGxluGJogAss`

The key itself is public by design, **but only if restricted in Google Cloud Console**.

**Action required (Google Cloud Console):**
1. Go to **APIs & Services → Credentials**, project `neckguard`.
2. Click the API key → **Application restrictions** → set to "Android apps".
3. Add package `com.example.neckguard` + your release SHA-1 fingerprint.
4. Under **API restrictions**, limit to only Firebase services you use (FCM, Analytics).
5. Without these restrictions anyone can use your key against billable Google APIs.

> `google-services.json` should be added to `.gitignore` and removed from git history before making the repo public: `git rm --cached NeckGuardApp/app/google-services.json`

---

## 🟠 High

### H-1 · `Log.d` calls not stripped in release → FCM token and User ID leak to logcat

**File:** `NeckGuardFirebaseMessagingService.kt` line 20 — FCM token logged  
**File:** `SupabaseClient.kt` line 170 — User ID logged  
**File:** `proguard-rules.pro` — currently empty (all rules commented out)

R8/ProGuard does **not** strip `Log.*` calls unless explicitly configured.

**Fix** — add to `proguard-rules.pro`:
```proguard
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute NeckGuard
```

---

### H-2 · Three unused dangerous permissions declared in manifest

**File:** `AndroidManifest.xml` lines 13–15

```xml
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />
```

After a full codebase scan: none of these are used anywhere.
- `ACTIVITY_RECOGNITION` is a **runtime dangerous permission** — prompts users on first open (the `LaunchedEffect` in `AppScreen` requests it).
- `BODY_SENSORS` is flagged as health-data permission in Play Store review.
- `HIGH_SAMPLING_RATE_SENSORS` is not needed after switching to `SENSOR_DELAY_NORMAL`.

**Fix:** delete all three lines. Also remove `ACTIVITY_RECOGNITION` from the `LaunchedEffect` permission request in `MainActivity.kt`.

---

### H-3 · `allowBackup="true"` with empty backup rules → session token backed up to Google Drive

**Files:** `AndroidManifest.xml`, `data_extraction_rules.xml`, `backup_rules.xml`

Both XML rule files are sample stubs with no actual rules. The encrypted prefs file (containing the Supabase access token) gets backed up — then on restore the KeyStore master key is missing, the decryption silently falls back to plain prefs, and the user is invisibly logged out with possible token exposure to the backup destination.

**Option A (quickest):** Set `android:allowBackup="false"` in the manifest.

**Option B (proper — also covers pre-API-31 devices):**

In `data_extraction_rules.xml`:
```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="neckguard_secure_prefs.xml"/>
        <exclude domain="sharedpref" path="neckguard_prefs_fallback.xml"/>
        <exclude domain="sharedpref" path="neckguard_crashes.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="neckguard_secure_prefs.xml"/>
        <exclude domain="sharedpref" path="neckguard_prefs_fallback.xml"/>
    </device-transfer>
</data-extraction-rules>
```

Repeat the same `<exclude>` entries in `backup_rules.xml`.

---

### H-4 · `launchMode="singleTask"` on exported Activity without empty `taskAffinity` → StrandHogg task hijacking

**File:** `AndroidManifest.xml` — `MainActivity`

A malicious app can declare `taskAffinity="com.example.neckguard"` with `allowTaskReparenting=true` and push its own login-mimic activity onto your task stack. The user taps your icon and sees the attacker's screen.

**Fix:**
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:taskAffinity=""
    ...
```
Change `singleTask` → `singleTop` and add `android:taskAffinity=""`.

---

### H-5 · Weak password policy (client-side 6-char check only)

**File:** `AuthScreen.kt` line 64

Server-side: verify in Supabase dashboard → **Authentication → Providers → Email** that `password_min_length` is at least 10 and "Enable Have I Been Pwned breach protection" is turned on.

---

### H-6 · No `networkSecurityConfig` — no explicit cleartext ban or cert pinning

**File:** `AndroidManifest.xml` — no `android:networkSecurityConfig` attribute

**Fix** — create `res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

Add to manifest `<application>` tag: `android:networkSecurityConfig="@xml/network_security_config"`

---

## 🟡 Medium

### M-1 · User ID printed to logcat on every login

**File:** `SupabaseClient.kt` line 170  
Resolved by H-1 ProGuard fix.

---

### M-2 · No timeouts on `HttpURLConnection` → login hangs indefinitely on bad networks

**File:** `SupabaseClient.kt` → `setupConnection()`

**Fix:**
```kotlin
conn.connectTimeout = 10_000
conn.readTimeout = 15_000
```

---

### M-3 · Raw stack traces uploaded to Supabase without PII scrubbing

**File:** `CrashReporter.kt`

Stack traces can contain email addresses embedded in exception messages, access tokens, URL query strings. These live in Supabase permanently.

**Fix:** before uploading, sanitize with:
```kotlin
val sanitized = trace
    .replace(Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"), "[email]")
    .replace(Regex("ey[A-Za-z0-9_\\-]{20,}\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*"), "[jwt]")
    .replace(Regex("\\?[^\\s\"']+"), "?[params_redacted]")
```

---

### M-4 · `SupabaseClient.accessToken` never refreshed — session breaks silently after ~1 hour

**File:** `SupabaseClient.kt`

Supabase access tokens expire in 1 hour by default. No refresh-token logic exists. When it expires, every API call returns 401 silently (no error surfaces to the user).

**Fix:**
- Store `refresh_token` in `SecurePrefs` alongside the access token.
- Catch 401 responses → call `POST /auth/v1/token?grant_type=refresh_token` → save new tokens.
- Add `@Volatile` to `accessToken` and `userId` fields.

---

### M-5 · Remote-triggered camera via FCM is hinted in a comment — document or remove

**File:** `NeckGuardFirebaseMessagingService.kt` line 35

```kotlin
// e.g. Triggering an immediate posture check remotely via JSON payload
```

If you implement this: require HMAC-signed payload, require recent user activity, cap at 1 trigger/hour, never auto-open camera without visible UI. If you don't implement it: delete the comment.

---

### M-6 · OAuth fragment parser breaks on base64 `=` padding

**File:** `MainActivity.kt` line 131

```kotlin
val split = it.split("=")  // truncates values containing =
```

**Fix:**
```kotlin
val split = it.split("=", limit = 2)
```

---

### M-7 · `NeckGuardService` action dispatch uses unprotected string — safe today, fragile if exported

**File:** `NeckGuardService.kt`  
Service is `exported="false"` so unexploitable now. Note: if you ever export the service, add `android:permission="com.example.neckguard.CONTROL"` with `protectionLevel="signature"`.

---

### M-8 · `CheckPostureActivity` activates camera invisibly — user trust risk

**File:** `AndroidManifest.xml` — `CheckPostureActivity` uses `Theme.Translucent.NoTitleBar`

Android 12+ shows the green camera indicator dot, but many users won't notice. Recommendation: show a small full-screen overlay or toast ("Checking your posture…") so the user knows what's happening. This reduces user confusion and Play Store policy risk.

---

## 🟢 Low / Informational

| # | Location | Issue |
|---|----------|-------|
| L-1 | `build.gradle.kts` release block | Add `isDebuggable = false` and `isShrinkResources = true` explicitly. |
| L-2 | `build.gradle.kts` | No root-level `.gitignore`. Easy to accidentally `git add` `local.properties`, PDFs, scratch files. Create one at repo root. |
| L-3 | `AndroidManifest.xml` `uses-feature` | `android:required="true"` on front camera excludes many tablets. Consider `required="false"` + graceful fallback (already partially in place). |
| L-4 | `CrashReporter.kt` | Uses `GlobalScope.launch` — technically leaks if process exits, though harmless given `Application` scope. Replace with an `Application`-bound `CoroutineScope`. |
| L-5 | `BootReceiver` | `exported="true"` required pre-API 33 for `BOOT_COMPLETED`. On API 33+ you can combine with `android:directBootAware` + `exported="false"`. Keep as-is until minSdk bump. |
| L-6 | `NeckGuardFirebaseMessagingService` | FCM `onNewToken` logs the raw token (line 20). Resolved by H-1 ProGuard fix. |
| L-7 | Play Console | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `SCHEDULE_EXACT_ALARM` require Play Store permission declarations. Have justification text ready for review. |
| L-8 | No code | No root/emulator detection. Probably not worth false-positive pain for a posture app. Optional. |

---

## Security — Priority Fix Order

| Priority | Fix | Effort | Risk if skipped |
|----------|-----|--------|-----------------|
| 1 | Verify RLS on all Supabase tables (C-2) | 15 min in dashboard | Any user's data readable by public |
| 2 | Lock `crash_reports` to auth-only inserts (C-3) | 5 min in dashboard | Spam / unbounded Postgres growth |
| 3 | Restrict Firebase API key in Google Cloud Console (C-4) | 10 min in console | Unexpected billing |
| 4 | ProGuard log stripping (H-1) | 5 lines in proguard-rules.pro | FCM tokens in logcat |
| 5 | Remove 3 unused permissions (H-2) | 3 line deletions | Play Store rejection + user trust |
| 6 | Exclude encrypted prefs from backup (H-3) | 10 min | Token exposure on device transfer |
| 7 | Add `taskAffinity=""` + `singleTop` on MainActivity (H-4) | 2 lines | Task hijacking |
| 8 | PKCE + state on OAuth flow (C-1) | 2–4 hrs | Account takeover |
| 9 | Add `networkSecurityConfig` (H-6) | 10 min | No cleartext protection |
| 10 | `HttpURLConnection` timeouts (M-2) | 2 lines | Indefinite hang on login |
| 11 | Sanitize crash traces before upload (M-3) | 30 min | PII in Supabase |
| 12 | Refresh token rotation (M-4) | 2 hrs | Silent session expiry after 1 hr |

---

---

## Part 2 — UI Performance Items (After New UI is Built)

> These are items that were **left intentionally untouched** because the UI is being redesigned by your partner. Apply them when the new UI code is written, or use them as design guidelines.

---

### U-1 · `SecurePrefs.get()` must not be called inside `@Composable` bodies

**Current problem:** `HomeTab`, `AppScreen`, and `SettingsTab` all call `SecurePrefs.get(context)` directly inside composable functions. Since `EncryptedSharedPreferences` is now cached, this is no longer as expensive — but reads from encrypted prefs (`prefs.getLong(...)`, `prefs.getInt(...)`) still happen **on the UI thread during layout and every recomposition**.

**Rule for new UI:**
- Read prefs values in `ViewModel.init` or `ViewModel.checkStatus()`, expose them as `StateFlow<T>`.
- In composables: `val totalHours by viewModel.totalHours.collectAsState()` — never call `prefs.get*()` inline.

---

### U-2 · Dynamic theme colors cause full recomposition on every dark-mode read

**File:** `Color.kt`

```kotlin
val Teal: Color get() = if (isDarkModeState) DarkTeal else LightTeal
// ... ~40 more like this
```

Every color is a computed property read on every access. When any composable reads `Teal`, `Slate`, etc., it's not subscribed to `isDarkModeState` — so when the mode changes, everything that touches these colors recomposes but can't skip because the reads are not tracked as `State` reads.

**Fix for new UI:** create an immutable `AppColors` data class. Rebuild it only when `isDarkModeState` changes via `remember(isDarkModeState) { buildColors(isDarkModeState) }` at the theme root. Pass `colors` down via `CompositionLocalProvider`, then reads are automatically reactive and stable.

---

### U-3 · `NavItem` runs 4 concurrent `animateXAsState` per item on every tab switch

**Current code:** `bgColor`, `iconTint`, `hPad`, `animateFloatAsState(scale)` — all running simultaneously for each of 3 nav items = 12 live animations per switch.

**Fix for new UI:**
- Collapse `bgColor` + `iconTint` into a single `animateColorAsState` targeting an opaque "selected" state.
- Replace the animated `hPad` (which forces a full relayout) with `graphicsLayer { scaleX = scale; scaleY = scale }` on the whole item — cheap GPU layer transform, no layout invalidation.
- 12 animations → 4 total.

---

### U-4 · `MiniSquircleCard` stats use `AnimatedContent` for odometer effect — triggers on every recomposition

**Current code:** `AnimatedContent(targetState = value, ...)` inside the card. Every time the parent recomposes (e.g., during a tab transition), `AnimatedContent` re-evaluates its `transitionSpec`.

**Fix for new UI:**
- Wrap in `key(value) { Text(value, ...) }` and let Compose skip the transition when the value hasn't changed.
- Or only trigger the animation on explicit user-facing value changes (e.g., when the ViewModel publishes a new value).

---

### U-5 · Canvas `drawArc` in `POSTURE` card redraws every frame

**Current code:** A `Canvas` inside `MiniSquircleCard.POSTURE` redraws the progress ring on every recomposition, even when the `value` hasn't changed.

**Fix for new UI:** use `Modifier.drawBehind { }` or move the draw logic into a `Modifier.drawWithCache { }` so it only re-executes when the progress float actually changes.

---

### U-6 · Entrance animations in `HomeTab` and `RewardsTab` run sequentially with no `launch {}`

**Current code (simplified):**
```kotlin
LaunchedEffect(Unit) {
    streakOffset.animateTo(0f, tween(350))   // suspends until done
    row1Offset.animateTo(0f, tween(350))     // waits for above
    row2Offset.animateTo(0f, tween(350))     // waits for above
}
```

This makes the stagger work but each animation **suspends the coroutine** until completion, so the last card appears ~1 second after the first. There is inconsistency with `launch { }` blocks mixed in.

**Fix for new UI:** decide on either:
- True parallel: all wrapped in `launch { }` inside the `LaunchedEffect`
- True stagger: use `AnimatedVisibility` with `delayMillis` or `Animatable.animateTo` in separate `launch { delay(n); ... }` blocks

---

### U-7 · `LazyVerticalGrid` inside `ExercisesScreen` must not be inside a `verticalScroll` parent

The current `ExercisesScreen` uses `LazyVerticalGrid(modifier = Modifier.fillMaxSize())` inside a `Column`. If the new UI wraps this in a scrollable parent, it will crash at runtime with an unbounded height error.

**Rule for new UI:** `LazyVerticalGrid` must have a bounded height — either `fillMaxSize()` with no outer scroll, or a fixed `height()` modifier if placed inside a scrollable column.

---

### U-8 · Gradient hero image bitmap decoded on main thread via `painterResource`

**Current code:** `painterResource(id = currentTheme.resId)` inside `HomeTab` decodes the JPEG on the UI thread.

**Fix for new UI:**
- Use `AsyncImage` from **Coil** or **Glide Compose** for async decoding.
- Convert gradient images from JPEG to **WebP** (lossless or ~85 quality). Three of the six images are 130–190 KB on disk; WebP would cut them to 40–70 KB and decode ~30% faster.
- Add a small thumbnail variant (128×128) for the Settings picker row, separate from the full-size background image.

---

### U-9 · `AppScreen` re-reads `SecurePrefs` for `userName` and `PostureChecksToday` every recomposition

**File:** `MainActivity.kt` — `HomeTab`

```kotlin
val userName = prefs.getString("UserName", "Max") ?: "Max"
// ...
prefs.getInt("PostureChecksToday", 4)
```

Both are prefs reads inside composable bodies. Even with the cached singleton these are encrypted-map lookups on the UI thread.

**Fix for new UI:** expose these as properties in `MainViewModel` backed by `StateFlow`. Update `PostureChecksToday` from the service via a `SharedPreferences.OnSharedPreferenceChangeListener` → `MutableStateFlow`.

---

### U-10 · `ExerciseDetailContent` always shows the same Rive animation regardless of exercise

**Current code:**
```kotlin
setRiveResource(com.example.neckguard.R.raw.girl, autoplay = true)
```

Every exercise shows the same `girl.riv` animation, even though the exercise data has a `lottieRawName` field (currently unused). The `AnimatedExerciseCharacter` composable with custom Canvas drawings exists but is never called.

**For new UI:** wire `exercise.lottieRawName` to play the correct animation, or use the `AnimatedExerciseCharacter` canvas implementation for exercises without a Rive file.

---

### UI Performance — Priority Fix Order

| Priority | Fix | When |
|----------|-----|------|
| 1 | Move all prefs reads to ViewModel StateFlow (U-1, U-9) | Start of new UI work |
| 2 | Replace dynamic Color properties with immutable AppColors (U-2) | Start of new UI work |
| 3 | Verify LazyVerticalGrid not inside scroll parent (U-7) | During new UI layout |
| 4 | Convert gradient images to WebP + add Coil async loading (U-8) | Asset prep phase |
| 5 | Fix NavItem animations: 12 → 4 (U-3) | Nav component rebuild |
| 6 | Fix entrance animation stagger (U-6) | Animation pass |
| 7 | Fix MiniCard AnimatedContent (U-4) | Card component rebuild |
| 8 | Fix Canvas drawArc (U-5) | Card component rebuild |
| 9 | Wire correct exercise animations (U-10) | Exercises screen rebuild |

---

## Already Applied (reference)

The following performance fixes were applied to the codebase during the pre-UI optimisation pass:

| Fix | Files changed |
|-----|---------------|
| `SecurePrefs` cached singleton — eliminates repeated KeyStore calls | `SecurePrefs.kt` |
| Sensor batch latency 30 s → 2 s; `SENSOR_DELAY_UI` → `SENSOR_DELAY_NORMAL` | `NeckGuardService.kt` |
| `DisplayManager` cached in `onCreate`, updated via `DisplayListener` | `NeckGuardService.kt` |
| `NeckGuardApplication` registered in manifest; `Rive.init` + `CrashReporter.initialize` centralised there | `NeckGuardApplication.kt`, `AndroidManifest.xml`, `MainActivity.kt` |
| `FaceDetector` and `ProcessCameraProvider` closed in `CheckPostureActivity.onDestroy` | `CheckPostureActivity.kt` |
| `hydrateSession()` / `logout()` / `finishOnboarding()` moved to `Dispatchers.IO` | `MainViewModel.kt` |
| Watchdog moved to dedicated `HandlerThread`; interval 8 s → 60 s | `NeckGuardService.kt` |
| DB writes use service-scoped `CoroutineScope` (cancelled on `onDestroy`) instead of `GlobalScope` | `NeckGuardService.kt` |
