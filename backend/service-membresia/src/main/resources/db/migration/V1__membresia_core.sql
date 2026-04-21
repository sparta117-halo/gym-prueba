create extension if not exists pgcrypto;

create table if not exists memberships (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  branch_id uuid not null,
  member_user_id uuid not null,
  plan_id uuid,
  member_code varchar(40) not null,
  status varchar(30) not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  billing_day integer,
  snapshot_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, member_code)
);

create table if not exists payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  branch_id uuid not null,
  membership_id uuid not null,
  amount numeric(12,2) not null,
  currency varchar(10) not null default 'GTQ',
  paid_at timestamptz not null,
  payment_method varchar(40),
  metadata_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists attendance_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null,
  branch_id uuid not null,
  membership_id uuid not null,
  occurred_at timestamptz not null,
  source varchar(40) not null,
  context_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_memberships_branch_status on memberships (branch_id, status);
create index if not exists idx_payments_membership_paid_at on payments (membership_id, paid_at desc);
create index if not exists idx_attendance_membership_occurred_at on attendance_events (membership_id, occurred_at desc);
create index if not exists idx_memberships_snapshot_jsonb on memberships using gin (snapshot_jsonb);
create index if not exists idx_payments_metadata_jsonb on payments using gin (metadata_jsonb);