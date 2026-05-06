# 🛡️ NudgeUp — Google Play Store Pre-Launch Audit

> **App:** NudgeUp (applicationId `app.nudgeup.android`)
> **Version:** 1.0 (versionCode 1)
> **Target SDK:** 36 · **Min SDK:** 24
> **Audit Date:** 29 April 2026

---

## Severity Legend

| Icon | Level | Meaning |
|------|-------|---------|
| 🔴 | **BLOCKER** | Google Play **will reject** your submission or remove your app |
| 🟠 | **HIGH** | Very likely to trigger a policy warning, delayed review, or conditional approval |
| 🟡 | **MEDIUM** | May cause issues during review or reduce trust score |
| 🟢 | **LOW** | Best practice / polish — won't block submission but improves review outcomes |

---

## Executive Summary

Your app is **architecturally solid** — encrypted prefs, proper foreground service typing, telemetry consent, cleartext-traffic disabled, backup exclusion rules — all great. But there are **4 blockers** and **5 high-severity issues** that will get your app rejected or suspended if not addressed before upload.

| Severity | Count |
|----------|-------|
| 🔴 BLOCKER | 4 |
| 🟠 HIGH | 5 |
| 🟡 MEDIUM | 5 |
| 🟢 LOW | 4 |

---

## 🔴 BLOCKER Issues

### B-01: No In-App Account Deletion

**Policy:** [User Data — Account Deletion](https://support.google.com/googleplay/android-developer/answer/13327111)

Google **mandates** that any app allowing account creation must provide:
1. An **in-app** path for users to delete their account and associated data
2. A **web URL** where users can request deletion (for users who already uninstalled)

**Current state:** Your Settings screen has a "Log Out" button but **zero** account deletion functionality. The `logout()` method in `UserRepository` only signs out Firebase and clears local prefs — it does **not** delete the user's:
- Firebase Auth account
- Supabase `user_profiles` row
- Supabase `posture_logs` rows
- Supabase `crash_reports` rows

**Fix:**
```kotlin
// Add to Settings screen — "Delete Account" button
// 1. Call Firebase Auth deleteUser()
// 2. Call Supabase DELETE /rest/v1/user_profiles?user_id=eq.{uid}
// 3. Call Supabase DELETE /rest/v1/posture_logs?user_id=eq.{uid}
// 4. Clear local Room DB
// 5. Clear SharedPreferences
// 6. Sign out and navigate to Unauthenticated
```
Also create a simple web page (e.g., `nudgeup.app/delete-account`) with an email form for deletion requests.

---

### B-02: Missing / Inadequate Privacy Policy

**Policy:** [Privacy, Deception and Device Abuse](https://support.google.com/googleplay/android-developer/answer/10787469)

**Current state:** Your "Privacy Policy" is a single-paragraph **in-app AlertDialog** that reads:

> "Your data is processed locally on your device where possible. Crash telemetry and analytics may be sent to Firebase, and profile settings sync to Supabase. None of your posture photos ever leave your device."

This is **not sufficient**. Google requires:
- A **hosted URL** (not just an in-app dialog) — you must provide this URL in Play Console
- Specific disclosure of **what data** is collected (name, email, age group, posture sensor data, crash traces, device info, Firebase analytics events)
- How data is **used**, **stored**, and **shared**
- Data **retention** and **deletion** policies
- Contact information
- Third-party services used (Firebase, Supabase, Google ML Kit, Rive, Lottie)

**Fix:**
1. Write a full privacy policy covering all the above points
2. Host it at a public URL (e.g., `https://nudgeup.app/privacy`)
3. Link to it from both the Play Console listing AND the in-app Settings
4. Keep the in-app dialog as a summary, but add a "Read full policy" link to the hosted URL

---

### B-03: Camera `required="true"` Blocks Device Compatibility

**Policy:** [uses-feature filtering](https://developer.android.com/google/play/filters)

```xml
<uses-feature android:name="android.hardware.camera" android:required="true" />
<uses-feature android:name="android.hardware.camera.front" android:required="true" />
```

Setting `required="true"` means **your app will be invisible** on the Play Store to any device without a front camera. While most phones have one, many tablets, Chromebooks, and foldables don't — which dramatically reduces your addressable market.

More critically, your app **works fine without the camera**. The camera is only used in `CheckPostureActivity` for the 3D face-pitch check, and that activity already handles missing camera permission gracefully (falls back to sensor-only pitch). So `required="true"` is overly restrictive.

**Fix:**
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.front" android:required="false" />
```
Then handle the missing-camera case in your posture check code (you already do — `fireFallbackNotification`).

---

### B-04: Health App Declaration Required in Play Console

**Policy:** [Health App Policies](https://support.google.com/googleplay/android-developer/answer/10787469)

Your app:
- Uses `foregroundServiceType="health"`
- Monitors user posture / cervical health
- Provides exercise recommendations for neck pain
- Uses health-related terminology ("posture score", "cervical flexion", "spinal imbalance")
- Makes claims like "High Risk Mode", "Moderate Risk", "Chronic / ongoing pain"

Google classifies this as a **health and fitness app**. You **must**:
1. Complete the **Health Apps declaration** form in Play Console (App content > Health apps)
2. Clearly state your app is **NOT a medical device** and does **NOT provide medical diagnoses**
3. Add an in-app disclaimer (splash or onboarding) that it is for wellness/informational purposes only

> [!CAUTION]
> Without the Health Apps declaration, your app will be rejected during review. Google is very strict about health claims.

---

## 🟠 HIGH Issues

### H-01: `SCHEDULE_EXACT_ALARM` Permission Will Be Denied by Default

**Policy:** [Exact Alarms — Android 14+](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

On Android 14+ (API 34), `SCHEDULE_EXACT_ALARM` is **denied by default** for new installs. Your code in `NeckGuardService.scheduleRestart()` already checks `canScheduleExactAlarms()` and falls back to `setAndAllowWhileIdle()`, which is correct. But:

1. Google Play **may flag** the permission during review and ask you to justify it
2. Apps targeting SDK 36 should use `USE_EXACT_ALARM` only for user-visible alarms (clocks/timers) — your use case (service restart) is not user-visible

**Fix:**
- Remove `SCHEDULE_EXACT_ALARM` from the manifest entirely
- Your fallback `setAndAllowWhileIdle()` already handles the restart — it just fires ~1-9 minutes later, which is fine for a service watchdog
- Alternatively, if you genuinely need it, be prepared to justify it in the Play Console Permissions declaration form

---

### H-02: Foreground Service Declaration Required in Play Console

**Policy:** [Foreground Service Types — Play Console](https://support.google.com/googleplay/android-developer/answer/13392821)

For Android 14+ (your target is 36), you **must** declare foreground service usage in the Play Console:

1. Go to **App content** > **Foreground service permissions**
2. For `health` type, provide:
   - **Description of functionality:** "Continuously monitors phone tilt via accelerometer and gyroscope to detect poor posture in real-time"
   - **User impact if deferred:** "Posture monitoring would stop completely, and the user would receive no posture correction nudges"
   - **A video link** demonstrating the user initiating the feature (record a video of toggling monitoring ON from the dashboard)

> [!IMPORTANT]
> This is a Play Console form you must fill out **before submitting**. Your code is correct — the issue is only on the console side.

---

### H-03: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Needs Justification

**Policy:** [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/10787469)

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Google restricts use of this permission. It's allowed for health/fitness apps that need continuous background operation, which you qualify for, but you must:
1. Justify it in the **Permissions declarations** form in Play Console
2. Ensure you only request it when the user explicitly opts in (your Settings "Fix" button flow is fine)

---

### H-04: Data Safety Section Must Be Completed

**Policy:** [Data Safety Form](https://support.google.com/googleplay/android-developer/answer/10787469)

You must complete the Data Safety form in Play Console disclosing:

| Data Type | Collected? | Shared? | Purpose |
|-----------|-----------|---------|---------|
| **Email address** | ✅ Yes | ❌ No (stays in Firebase/Supabase, not shared with 3rd parties) | Account authentication |
| **Name** | ✅ Yes | ❌ No | Personalization |
| **Age (range)** | ✅ Yes | ❌ No | Content customization |
| **Health info** (posture data) | ✅ Yes | ❌ No | App functionality |
| **Photos/videos** | ✅ Collected transiently | ❌ No (never leaves device, never stored) | Face detection for posture check |
| **Device info** | ✅ Yes (crash reports) | ❌ No | Crash diagnostics |
| **App activity** (analytics events) | ✅ Yes | With Firebase (Google) | Product improvement |
| **Crash logs** | ✅ Yes | With Firebase + Supabase | Bug fixing |
| **Sensor data** (accelerometer/gyro) | ✅ Processed locally | ❌ No | Core posture detection |
| **App interactions** | ✅ Yes | With Firebase | Analytics |

Also declare:
- Data is encrypted in transit (HTTPS only ✅)
- Data is encrypted at rest (EncryptedSharedPreferences ✅)
- Users can request data deletion (you need to implement this — see B-01)

---

### H-05: `google-services.json` Contains API Key — Ensure It's Not in Public Repo

**Security concern:** Your `google-services.json` is committed and contains:
- Firebase API key: `AIzaSyA1WO7iZ0-8M7HPUpHAgtLjunMZtCOV2zM`
- OAuth client IDs
- Project number

While `google-services.json` is designed to be in the app (it ships in the APK anyway), make sure:
1. Your GitHub repo is **private**
2. You've added `google-services.json` to `.gitignore` if the repo is or will become public
3. Firebase API key restrictions are configured in Google Cloud Console (restrict to your Android app's SHA-1 fingerprint)

---

## 🟡 MEDIUM Issues

### M-01: No Medical Disclaimer in App

Your app makes specific health claims:
- "High Risk Mode (X° flexion): You are heavily slouched"
- "Spinal imbalance • High severity"
- "Chronic / ongoing pain — already seeing or should see a physio"

Without a prominent disclaimer, Google may flag this as a misleading health claim.

**Fix:** Add a disclaimer during onboarding and in Settings:
> "NudgeUp is a wellness tool, not a medical device. It does not diagnose, treat, cure, or prevent any disease. Consult a healthcare professional for medical advice."

---

### M-02: System Icons Used for Notifications

```kotlin
.setSmallIcon(android.R.drawable.ic_dialog_info)  // Alert notification
.setSmallIcon(android.R.drawable.ic_menu_compass)  // Persistent notification
```

Using Android system icons is:
1. **Visually unprofessional** — they look generic and dated
2. **May cause rendering issues** on some OEMs that override system drawables

**Fix:** Create custom notification icons:
- A monochrome vector drawable in `res/drawable/` for the notification small icon
- Follow the [Material Design notification icon guidelines](https://developer.android.com/develop/ui/views/notifications/build-notification#icon)

---

### M-03: App Signing Key — Use Play App Signing

Make sure you:
1. Opt in to **Google Play App Signing** (enrollment is mandatory for new apps as of 2021)
2. Upload an **AAB (Android App Bundle)**, not an APK
3. Keep your upload key backed up securely

---

### M-04: Missing `kotlin-kapt` or `ksp` for Room Compiler

```kotlin
annotationProcessor("androidx.room:room-compiler:$room_version")
```

You're using `annotationProcessor` which is the Java annotation processing tool. For a Kotlin project, you should use `kapt` or `ksp`:

```kotlin
// Option A (recommended):
ksp("androidx.room:room-compiler:$room_version")

// Option B:
kapt("androidx.room:room-compiler:$room_version")
```

While this may work because your Room entities/DAOs are in Java files, it's a ticking time bomb if you ever convert them to Kotlin. Also, `ksp` is significantly faster than `kapt`.

---

### M-05: `kotlinOptions` Block Missing — Java 11 Target Not Set for Kotlin

Your `build.gradle.kts` sets `sourceCompatibility` and `targetCompatibility` to Java 11, but doesn't set `kotlinOptions.jvmTarget`:

```kotlin
// Missing:
kotlinOptions {
    jvmTarget = "11"
}
```

This mismatch can cause build failures or runtime issues on some configurations.

---

## 🟢 LOW Issues

### L-01: Namespace Still Uses `com.example.neckguard`

```kotlin
namespace = "com.example.neckguard"
```

While this is technically fine (the `namespace` is only used for R class generation and doesn't appear on Play Store), having `com.example.*` looks unprofessional in decompiled code. Your `applicationId` is correctly set to `app.nudgeup.android`.

Consider renaming the namespace to `app.nudgeup.android` too, though this requires refactoring all `import` statements.

---

### L-02: `versionCode = 1` — Confirm Before Upload

Your version code is `1`, which is correct for a first release. Just make sure every subsequent upload increments this. Play Console will reject uploads with duplicate or lower version codes.

---

### L-03: Content Rating Questionnaire

You must complete the **Content Rating Questionnaire** (IARC) in Play Console before publishing. For NudgeUp:
- No violence, sexual content, gambling, or controlled substances
- Health-related content (posture, exercises) — declare accurately
- Target audience: **likely 13+** since you collect age group data and process health information

---

### L-04: Store Listing Assets Required

Before submission, prepare:
- **App icon:** 512×512 PNG (you have launcher icons, but need the high-res store version)
- **Feature graphic:** 1024×500 PNG
- **Screenshots:** At least 2 per device type (phone), up to 8 recommended
- **Short description:** 80 characters max
- **Full description:** Up to 4000 characters
- **Category:** Health & Fitness

---

## Play Console Submission Checklist

Use this checklist when filling out the Play Console forms:

- [ ] **App Content > Privacy Policy:** Paste your hosted privacy policy URL
- [ ] **App Content > Data Safety:** Complete the form (see H-04 table above)
- [ ] **App Content > Health Apps:** Complete the Health Apps declaration (B-04)
- [ ] **App Content > Foreground Service Permissions:** Declare `health` type (H-02)
- [ ] **App Content > Permissions declarations:** Justify `SCHEDULE_EXACT_ALARM` (or remove it — H-01), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (H-03)
- [ ] **App Content > Content rating:** Complete IARC questionnaire (L-03)
- [ ] **App Content > Target audience:** Set to 13+ or appropriate range
- [ ] **App Content > Ads:** Declare no ads (assuming your app has none)
- [ ] **App Content > Government apps:** Mark as No
- [ ] **App Content > Financial features:** Mark as No
- [ ] **Store listing > Main store listing:** Title, descriptions, screenshots, icon (L-04)
- [ ] **Release > App signing:** Enroll in Play App Signing (M-03)
- [ ] **Release > Create release:** Upload AAB (not APK)

---

## Code Fixes Priority Order

Address these in order — each subsequent fix becomes easier once the previous ones are done:

```
1. [B-01] Implement account deletion  ← MUST DO
2. [B-02] Write & host privacy policy ← MUST DO
3. [B-03] Change camera required=false ← 30 seconds
4. [B-04] Prepare health app disclaimer ← MUST DO
5. [H-01] Remove SCHEDULE_EXACT_ALARM  ← 30 seconds
6. [M-02] Create custom notification icons
7. [M-04] Switch to ksp for Room
8. [M-05] Add kotlinOptions jvmTarget
```

---

## What Won't Block You (Things You're Already Doing Right)

✅ **HTTPS-only network security config** — cleartext traffic disabled globally
✅ **EncryptedSharedPreferences** with fallback handling — production-grade
✅ **Proper foreground service type** (`health`) with correct permissions
✅ **Telemetry consent toggle** — user can opt out of Crashlytics + Analytics
✅ **No PII in analytics** — name/email never logged to Firebase events
✅ **Backup exclusion rules** — sensitive data excluded from cloud backup
✅ **ProGuard/R8 enabled** for release builds
✅ **Firebase Crashlytics** for production crash monitoring
✅ **No cleartext API keys** in source — loaded from `local.properties`
✅ **Dynamic receiver export flags** — Android 14 compatible
✅ **Auth token refresh** with mutex-guarded single-flight pattern
✅ **Camera images never saved/transmitted** — only processed in-memory for face detection

---

> [!NOTE]
> This audit covers the **code-level** and **Play Console** requirements. You should also do a final QA pass on a physical device running Android 14+ to check for any runtime crashes, ANRs, or permission dialogs that don't appear.
