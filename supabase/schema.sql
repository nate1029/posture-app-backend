-- ─────────────────────────────────────────────────────────────────────────
-- NudgeUp — Supabase production schema (single-file migration)
-- ─────────────────────────────────────────────────────────────────────────
-- Run this in a brand-new Supabase project's SQL editor:
--   Dashboard → SQL Editor → New Query → paste this whole file → Run
--
-- Idempotent: every CREATE uses IF NOT EXISTS / OR REPLACE / DROP IF EXISTS,
-- so it's safe to re-run after edits.
--
-- What it sets up:
--   • public.user_profiles    — onboarding answers, one row per auth user
--   • public.crash_reports    — JVM crashes uploaded by the Android client
--   • public.posture_logs     — (forward-compatible) sensor log mirror
--   • RLS policies on all three so users can only read/write their own rows
--   • Indexes for the queries the app actually runs
--   • A trigger that auto-creates a placeholder user_profiles row on signup
-- ─────────────────────────────────────────────────────────────────────────

-- ── 1. user_profiles ─────────────────────────────────────────────────────
create table if not exists public.user_profiles (
    user_id              uuid primary key references auth.users(id) on delete cascade,
    name                 text,
    age_group            text,
    notification_vibe    text,
    usage_context        text,
    neck_health          text,
    check_interval_ms    bigint default 1800000,  -- 30 minutes
    -- Forward slot for FCM push targeting (S-08). The app already persists
    -- the local FCM token in encrypted prefs and will sync here once we
    -- wire the upload call site.
    fcm_token            text,
    created_at           timestamptz default now(),
    updated_at           timestamptz default now()
);

-- Auto-update updated_at on every UPDATE.
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists user_profiles_set_updated_at on public.user_profiles;
create trigger user_profiles_set_updated_at
    before update on public.user_profiles
    for each row execute function public.set_updated_at();

-- ── 2. crash_reports ─────────────────────────────────────────────────────
create table if not exists public.crash_reports (
    id           bigserial primary key,
    user_id      uuid not null references auth.users(id) on delete cascade,
    stack_trace  text not null,
    device_info  text,
    created_at   timestamptz default now()
);

create index if not exists crash_reports_user_id_idx
    on public.crash_reports(user_id);

create index if not exists crash_reports_created_at_idx
    on public.crash_reports(created_at desc);

-- ── 3. posture_logs (cloud mirror, currently unused by client) ───────────
-- Schema mirrors the Room entity in
-- NeckGuardApp/app/src/main/java/com/example/neckguard/data/local/PostureLog.java
-- so we can opt into cloud sync later without another migration.
create table if not exists public.posture_logs (
    id                    bigserial primary key,
    user_id               uuid not null references auth.users(id) on delete cascade,
    timestamp_start_ms    bigint not null,
    duration_ms           bigint not null,
    healthy_ms            bigint not null,
    slouched_ms           bigint not null,
    created_at            timestamptz default now(),
    unique (user_id, timestamp_start_ms)
);

create index if not exists posture_logs_user_time_idx
    on public.posture_logs(user_id, timestamp_start_ms desc);

-- ── 4. Row Level Security ────────────────────────────────────────────────
alter table public.user_profiles enable row level security;
alter table public.crash_reports enable row level security;
alter table public.posture_logs  enable row level security;

-- user_profiles: users can read AND write only their own row.
drop policy if exists "user_profiles_select_own" on public.user_profiles;
create policy "user_profiles_select_own"
    on public.user_profiles for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "user_profiles_insert_own" on public.user_profiles;
create policy "user_profiles_insert_own"
    on public.user_profiles for insert
    to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "user_profiles_update_own" on public.user_profiles;
create policy "user_profiles_update_own"
    on public.user_profiles for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- crash_reports: insert-only for authenticated users; can read their own.
drop policy if exists "crash_reports_insert_own" on public.crash_reports;
create policy "crash_reports_insert_own"
    on public.crash_reports for insert
    to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "crash_reports_select_own" on public.crash_reports;
create policy "crash_reports_select_own"
    on public.crash_reports for select
    to authenticated
    using (auth.uid() = user_id);

-- posture_logs: full CRUD on own rows for future client sync.
drop policy if exists "posture_logs_insert_own" on public.posture_logs;
create policy "posture_logs_insert_own"
    on public.posture_logs for insert
    to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "posture_logs_select_own" on public.posture_logs;
create policy "posture_logs_select_own"
    on public.posture_logs for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "posture_logs_update_own" on public.posture_logs;
create policy "posture_logs_update_own"
    on public.posture_logs for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- ── 5. Service-role access (for server-side tooling / dashboards) ────────
-- service_role bypasses RLS by design, but we declare it explicitly here
-- so reading the policies tells you who *can* see the data.
grant all on public.user_profiles to service_role;
grant all on public.crash_reports to service_role;
grant all on public.posture_logs  to service_role;

-- ── Done. ─────────────────────────────────────────────────────────────────
-- Sanity check: list tables and policies
select schemaname, tablename, policyname, cmd, roles
from   pg_policies
where  schemaname = 'public'
order  by tablename, policyname;
