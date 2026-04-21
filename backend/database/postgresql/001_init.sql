create extension if not exists pgcrypto;

create table if not exists tenants (
  id uuid primary key default gen_random_uuid(),
  code varchar(50) not null unique,
  name varchar(120) not null,
  settings_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists branches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code varchar(50) not null,
  name varchar(120) not null,
  metadata_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table if not exists roles (
  id uuid primary key default gen_random_uuid(),
  code varchar(50) not null unique,
  name varchar(120) not null,
  permissions_jsonb jsonb not null default '[]'::jsonb
);

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid references branches(id),
  email varchar(180),
  phone varchar(30),
  username varchar(80) not null,
  password_hash varchar(255) not null,
  user_type varchar(40) not null,
  profile_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, username)
);

create table if not exists user_roles (
  user_id uuid not null references users(id),
  role_id uuid not null references roles(id),
  primary key (user_id, role_id)
);

create table if not exists membership_plans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid references branches(id),
  code varchar(50) not null,
  name varchar(120) not null,
  months_duration integer not null,
  price numeric(12,2) not null,
  attributes_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table if not exists memberships (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid not null references branches(id),
  member_user_id uuid not null references users(id),
  plan_id uuid references membership_plans(id),
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
  tenant_id uuid not null references tenants(id),
  branch_id uuid not null references branches(id),
  membership_id uuid not null references memberships(id),
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
  tenant_id uuid not null references tenants(id),
  branch_id uuid not null references branches(id),
  membership_id uuid not null references memberships(id),
  occurred_at timestamptz not null,
  source varchar(40) not null,
  context_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists routines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid references branches(id),
  code varchar(50) not null,
  name varchar(120) not null,
  definition_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, code)
);

create table if not exists progress_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid not null references branches(id),
  membership_id uuid not null references memberships(id),
  captured_at timestamptz not null,
  metrics_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists media_assets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid not null references branches(id),
  membership_id uuid references memberships(id),
  asset_type varchar(40) not null,
  storage_path varchar(255) not null,
  metadata_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists sync_operations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid references branches(id),
  user_id uuid references users(id),
  device_id varchar(120) not null,
  entity_type varchar(60) not null,
  entity_id varchar(120) not null,
  operation_type varchar(30) not null,
  payload_jsonb jsonb not null default '{}'::jsonb,
  operation_status varchar(30) not null default 'PENDING',
  requested_at timestamptz not null,
  processed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists configuration_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid references tenants(id),
  branch_id uuid references branches(id),
  config_key varchar(120) not null,
  value_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, branch_id, config_key)
);

create table if not exists app_versions (
  id uuid primary key default gen_random_uuid(),
  channel varchar(40) not null,
  version_name varchar(40) not null,
  minimum_supported_version varchar(40) not null,
  rollout_jsonb jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique (channel, version_name)
);

create index if not exists idx_memberships_branch_status on memberships (branch_id, status);
create index if not exists idx_payments_membership_paid_at on payments (membership_id, paid_at desc);
create index if not exists idx_attendance_membership_occurred_at on attendance_events (membership_id, occurred_at desc);
create index if not exists idx_sync_operations_status_requested_at on sync_operations (operation_status, requested_at);
create index if not exists idx_memberships_snapshot_jsonb on memberships using gin (snapshot_jsonb);
create index if not exists idx_payments_metadata_jsonb on payments using gin (metadata_jsonb);
create index if not exists idx_sync_operations_payload_jsonb on sync_operations using gin (payload_jsonb);