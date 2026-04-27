# NudgeUp — Production Launch Setup

> Migrate from the dev's personal Supabase + Firebase to the company-owned ones, in **about 45 minutes total**, end to end. This file is a runbook — work through it top-to-bottom, do every step in order.

**You will need:**
- Company email (already have it)
- Card on file for Supabase + Firebase Blaze plan if you ever want pay-as-you-go (free tier is enough for launch — you can skip this)
- Firebase CLI (already installed; verify with `firebase --version`)
- The new package name decision: `app.nudgeup.android` (locked in)

---

## Phase 1 — Supabase production project (≈ 15 min)

### 1.1 Create the project

1. Go to https://supabase.com/dashboard/projects.
2. Sign in with the **company email**.
3. Click **New project**.
4. Fill in:
   - **Name**: `nudgeup-prod`
   - **Database password**: generate a strong one with the dice icon and **save it in your password manager** — losing it costs you a project rebuild.
   - **Region**: pick the one closest to your users. India launch → `Mumbai (ap-south-1)`. Default `Singapore (ap-southeast-1)` is fine if unsure.
   - **Pricing plan**: Free is enough.
5. Click **Create new project** and wait ~2 minutes for provisioning.

### 1.2 Run the schema migration

1. In the new project's left nav: **SQL Editor → New query**.
2. Open `supabase/schema.sql` from this repo.
3. Copy its entire contents → paste into the SQL Editor → click **Run** (or `Ctrl+Enter`).
4. You should see a result table at the bottom listing every policy that was created. If the run fails, paste the error in chat and stop here.

### 1.3 Disable email confirmation

The Android client's auth flow does signup-then-login with the same credentials. If email confirmation is enabled, signup succeeds with no `access_token` in the response and the user gets stuck.

1. **Authentication → Providers → Email**.
2. **Confirm email** toggle: OFF.
3. **Save**.

### 1.4 Whitelist the Android deep-link callback

Without this, "Sign in with Google" ends up at `localhost:3000`.

1. **Authentication → URL Configuration**.
2. Under **Redirect URLs**, add this exact value (one line, no scheme prefix):
   ```
   neckguard://callback
   ```
3. Set **Site URL** to anything that will exist on the public web (e.g. your future landing page or just `https://nudgeup.app` as a placeholder). It's used in password-reset emails — `localhost:3000` will look broken to users.
4. **Save**.

### 1.5 Note your project's URL and anon key

1. **Project Settings → API** (the gear icon at bottom-left → API).
2. Copy these two values into a scratch file — you'll paste them into `local.properties` later:
   - **Project URL**: looks like `https://abcdefghijklmno.supabase.co`
   - **anon public key**: long string starting with `sb_publishable_...` or `eyJ...`

---

## Phase 2 — Google OAuth client (≈ 10 min)

> Skip this section entirely if you only want email/password login at launch. Only needed for "Sign in with Google".

### 2.1 Create / use a Google Cloud project

1. Go to https://console.cloud.google.com.
2. Top bar → project picker → **New Project** → Name: `NudgeUp` → Create.
3. Make sure the picker now shows `NudgeUp` selected.

### 2.2 Configure the OAuth consent screen

1. Left nav → **APIs & Services → OAuth consent screen**.
2. Choose **External** → Create.
3. Fill in:
   - **App name**: `NudgeUp`
   - **User support email**: your company email
   - **App logo**: skip for now (can add later before public launch)
   - **App domain**: skip
   - **Developer contact email**: your company email
4. Save and continue. On **Scopes** → Save and continue (no extra scopes needed). On **Test users** → add the team's emails so you can sign in during testing → Save and continue → Back to dashboard.

### 2.3 Create the OAuth client ID

1. **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
2. **Application type**: `Web application` *(yes, web — Supabase exchanges the token server-side; this is not the Android type)*.
3. **Name**: `NudgeUp Supabase Backend`.
4. Under **Authorized redirect URIs**, click **Add URI** and paste:
   ```
   https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback
   ```
   Replace `YOUR_PROJECT_REF` with the alphanumeric portion of the Supabase Project URL from step 1.5 (e.g. if URL is `https://abcdefghijklmno.supabase.co`, paste `https://abcdefghijklmno.supabase.co/auth/v1/callback`).
5. Click **Create**.
6. A modal pops up with the **Client ID** and **Client secret**. Copy both into your scratch file.

### 2.4 Wire it into Supabase

1. Back to the Supabase dashboard for `nudgeup-prod`.
2. **Authentication → Providers → Google**.
3. Toggle **Enable**.
4. Paste:
   - **Client ID (for OAuth)**: from 2.3.6
   - **Client Secret (for OAuth)**: from 2.3.6
5. Save.

---

## Phase 3 — Firebase project via CLI (≈ 15 min)

### 3.1 Authenticate with the company email

```powershell
firebase logout                 # clear any old session from the dev's account
firebase login                  # browser opens — sign in with the company email
firebase projects:list          # confirm you see no nudgeup-* projects yet
```

### 3.2 Create the new project

```powershell
firebase projects:create nudgeup-prod --display-name "NudgeUp"
```

If `nudgeup-prod` is taken globally (project IDs are global across all Google), pick another like `nudgeup-prod-app` or `nudgeup-android-prod` and use that everywhere `nudgeup-prod` appears below.

### 3.3 Add the Android app

```powershell
firebase apps:create ANDROID `
  --project nudgeup-prod `
  --package-name app.nudgeup.android `
  "NudgeUp Android"
```

Take note of the resulting **App ID** in the output — looks like `1:1234567890:android:abcdef0123456789`. Save it.

### 3.4 Get your debug SHA-1 fingerprint

In a separate terminal, from inside the `NeckGuardApp/` directory:

```powershell
cd NeckGuardApp
.\gradlew :app:signingReport
cd ..
```

Scroll the output for the block titled `Variant: debug`. Copy the `SHA1:` line value (long colon-separated hex).

Add it to the Firebase Android app:

```powershell
firebase apps:android:sha:create <APP_ID_FROM_3_3> <SHA1_FROM_ABOVE> --project nudgeup-prod
```

(For example `firebase apps:android:sha:create 1:1234567890:android:abcdef0123456789 AB:CD:...`)

### 3.5 Download the new google-services.json

```powershell
firebase apps:sdkconfig ANDROID <APP_ID_FROM_3_3> --project nudgeup-prod --out NeckGuardApp/app/google-services.json
```

This **overwrites** the local file with the real one. The placeholder edit I made earlier is now gone — that's the goal.

### 3.6 Point this repo's Firebase tooling at the new project

```powershell
firebase use --add
```

Pick `nudgeup-prod` from the list, then enter `default` as the alias name. This rewrites `.firebaserc` for you.

### 3.7 Deploy the Remote Config template

The values your app reads at runtime (nudge messages, slouch threshold, points-per-exercise) are stored in `remote_config.json`. Push them up:

```powershell
firebase deploy --only remoteconfig --project nudgeup-prod
```

Verify in the console: https://console.firebase.google.com/project/nudgeup-prod/config — you should see all 16 parameters listed.

### 3.8 Enable Crashlytics + Performance Monitoring

These are auto-enabled on first SDK call from a real device, but you should explicitly verify them in the console once you do your first test build:

- https://console.firebase.google.com/project/nudgeup-prod/crashlytics
- https://console.firebase.google.com/project/nudgeup-prod/performance

If either says "Setup required", click through the setup wizard — for both, the only requirement is that the SDK has phoned home from a real device once.

---

## Phase 4 — Local repo updates (≈ 5 min)

### 4.1 Update `local.properties`

Open `NeckGuardApp/local.properties` and replace the Supabase block with the values from Phase 1.5:

```properties
sdk.dir=C\:\\Users\\Naiteek\\AppData\\Local\\Android\\Sdk

SUPABASE_URL=https://YOUR_NEW_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_NEW_ANON_KEY
```

(`local.properties` is gitignored, never commits — that's correct.)

### 4.2 Confirm `google-services.json` is the new one

Open `NeckGuardApp/app/google-services.json`. The `package_name` field should be `app.nudgeup.android`, and the `project_id` should be `nudgeup-prod`. If those match → you're good. If not → re-run step 3.5.

### 4.3 Confirm `.firebaserc` was rewritten

Open `.firebaserc` at the repo root — should say `"default": "nudgeup-prod"`. If it still says `nudgeup-4aa6e`, re-run step 3.6.

### 4.4 Clean and rebuild

```powershell
cd NeckGuardApp
.\gradlew clean
.\gradlew assembleDebug
cd ..
```

If the build fails with `No matching client found for package name`, your `google-services.json` is stale — re-run 3.5.

---

## Phase 5 — End-to-end smoke test (≈ 10 min)

### 5.1 Email/password signup

1. Install the new debug APK on a real device (`adb install NeckGuardApp/app/build/outputs/apk/debug/app-debug.apk`).
2. Tap the email field, enter a brand-new email + 8-char password.
3. Tap Continue.
4. Expected: lands on the Onboarding screen.
5. Verify on Supabase dashboard → **Authentication → Users** — your user should appear.

### 5.2 Onboarding → Supabase profile

1. Complete onboarding (6 questions).
2. Tap "Finish Setup".
3. Expected: lands on Home dashboard.
4. Verify: Supabase → **Table Editor → user_profiles** — one row with your name, age group, etc.

### 5.3 Google OAuth

1. From Settings, tap "Log Out".
2. On Auth screen, tap "Sign in with Google".
3. Browser opens → pick a Google account that's in your OAuth consent screen test-users list.
4. Expected: redirects back into the app, lands on Home.
5. If you see `localhost:3000` instead → step 1.4 wasn't done, go back and add the redirect URL.

### 5.4 Crashlytics

1. From a debug build only, force a crash to verify the pipe:
   ```kotlin
   throw RuntimeException("Test crash from launch smoke test")
   ```
   from any button you can tap.
2. Reopen the app.
3. Within ~5 minutes, https://console.firebase.google.com/project/nudgeup-prod/crashlytics should show 1 fatal.
4. **Don't ship the test-crash button** — strip it out before release build.

### 5.5 Remote Config live update

1. https://console.firebase.google.com/project/nudgeup-prod/config.
2. Edit `slouch_angle_threshold` from `25` → `30` → Publish.
3. Force-close + reopen the app.
4. Open `adb logcat | grep RemoteConfig` and you should see "Remote Config fetched and activated".
5. Set it back to 25.

### 5.6 Posture engine

1. Stand the phone roughly upright, screen visible, leave it for ~30 seconds with the service running.
2. Verify the home dashboard's score recomputes (initially 0% until first session ends).
3. Tilt phone forward (head-down posture) for ~2 minutes during a Settings → Check Interval = 15-second window.
4. Expect the alert notification to fire.

---

## Phase 6 — Pre-release polish (do before Play Store upload)

Not blocking the smoke test, but you must clear these before pushing the release APK to Play Console:

- [ ] **Generate a release keystore** and configure `signingConfigs.release` in `app/build.gradle.kts`. Without this, Gradle will refuse to produce a release APK.
- [ ] **Add the release SHA-1** to the Firebase Android app (same flow as 3.4 + the `firebase apps:android:sha:create` command, with the release variant's SHA1).
- [ ] **Strip any test-crash buttons** added during 5.4.
- [ ] **Bump `versionCode` and `versionName`** in `app/build.gradle.kts` (currently `versionCode = 1, versionName = "1.0"` — keep `versionCode = 1` for the very first upload, then increment for every subsequent upload).
- [ ] **Privacy policy URL** — required by Google Play data-safety form. Even a one-page Notion or GitHub-Pages site is fine for v1.
- [ ] **Run `:app:assembleRelease`** with `isMinifyEnabled = true` (already set) and confirm the APK boots on a clean install — ProGuard sometimes strips reflection that Room or Firebase need; if it crashes on launch, paste the logcat to me.

---

## Things that are NOT automated (and why)

| Task | Why manual | Time cost |
|---|---|---|
| Create the Supabase project | Supabase doesn't expose project creation in the public CLI; the Management API requires a personal access token + curl. Manual is ~2 minutes. | 2 min |
| Disable email confirmation | Same — Auth provider settings only via dashboard. | 30 sec |
| Configure Google OAuth consent screen | Google Cloud doesn't accept this via CLI either. One-time. | 5 min |

---

## What to send me when something fails

If any step errors out, paste **the literal error text** plus **which numbered step you're on** (e.g. "Phase 3, step 3.5 failed with: …"). I can usually fix it inside one round trip.

---

## Quick reference — What got swapped

| Item | Old | New |
|---|---|---|
| Supabase project | `zxzfhzjdrwerovlurbkh.supabase.co` (dev's account) | from Phase 1.5 |
| Firebase project | `nudgeup-4aa6e` (dev's account) | `nudgeup-prod` (company) |
| Android applicationId | `com.example.neckguard` | `app.nudgeup.android` |
| OAuth Google Cloud project | dev's | new (Phase 2) |
| `local.properties` | dev's keys | company's keys (Phase 4.1) |
| `google-services.json` | placeholder | real (Phase 3.5) |
| `.firebaserc` | `nudgeup-4aa6e` | `nudgeup-prod` (Phase 3.6) |
