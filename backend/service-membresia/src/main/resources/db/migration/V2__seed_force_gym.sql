insert into tenants (id, code, name, settings_jsonb)
values (
  '11111111-1111-1111-1111-111111111111',
  'FORCEGYM',
  'Force Gym',
  '{"currency":"GTQ","syncIntervalMinutes":60}'::jsonb
)
on conflict (code) do nothing;

insert into branches (id, tenant_id, code, name, metadata_jsonb)
values (
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  'CENTRAL',
  'Sede Central',
  '{"timezone":"America/Guatemala"}'::jsonb
)
on conflict (tenant_id, code) do nothing;

insert into users (id, tenant_id, branch_id, email, phone, username, password_hash, user_type, profile_jsonb)
values
  (
    '44444444-4444-4444-4444-444444444441',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'admin@forcegym.local',
    '55550000',
    'admin',
    'admin',
    'ADMIN',
    '{"nombre":"Admin","apellido":"Force"}'::jsonb
  ),
  (
    '44444444-4444-4444-4444-444444444442',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'ana@forcegym.local',
    '55551111',
    'ana.lopez',
    'fit123',
    'MEMBER',
    '{"nombre":"Ana","apellido":"Lopez"}'::jsonb
  )
on conflict (tenant_id, username) do nothing;

insert into membership_plans (id, tenant_id, branch_id, code, name, months_duration, price, attributes_jsonb)
values (
  '55555555-5555-5555-5555-555555555551',
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  'PLAN-MENSUAL',
  'Plan Mensual',
  1,
  125.00,
  '{"graceDays":0}'::jsonb
)
on conflict (tenant_id, code) do nothing;

insert into memberships (id, tenant_id, branch_id, member_user_id, plan_id, member_code, status, starts_at, ends_at, billing_day, snapshot_jsonb)
values (
  '66666666-6666-6666-6666-666666666661',
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  '44444444-4444-4444-4444-444444444442',
  '55555555-5555-5555-5555-555555555551',
  'GYMANA01',
  'active',
  now() - interval '5 day',
  now() + interval '25 day',
  17,
  '{"id":"66666666-6666-6666-6666-666666666661","code":"GYMANA01","password":"fit123","nombre":"Ana","apellido":"Lopez","telefono":"55551111","fechaInicio":"2026-03-12T00:00:00Z","fechaFin":"2026-04-11T00:00:00Z","meses":1,"diaPago":17,"asistencias":["2026-03-15T06:00:00Z","2026-03-16T06:00:00Z"],"pagos":[{"id":"77777777-7777-7777-7777-777777777771","fecha":"2026-03-12T12:00:00Z","monto":125.0,"meses":1}],"progreso":[{"imagePath":"https://images.unsplash.com/photo-1517836357463-d25dfeac3438?auto=format&fit=crop&w=900&q=80","fecha":"2026-03-12T12:00:00Z"}]}'::jsonb
)
on conflict (tenant_id, member_code) do nothing;

insert into payments (id, tenant_id, branch_id, membership_id, amount, currency, paid_at, payment_method, metadata_jsonb)
values (
  '77777777-7777-7777-7777-777777777771',
  '11111111-1111-1111-1111-111111111111',
  '22222222-2222-2222-2222-222222222222',
  '66666666-6666-6666-6666-666666666661',
  125.00,
  'GTQ',
  now() - interval '5 day',
  'cash',
  '{"reference":"INIT-001"}'::jsonb
)
on conflict do nothing;

insert into attendance_events (id, tenant_id, branch_id, membership_id, occurred_at, source, context_jsonb)
values
  ('88888888-8888-8888-8888-888888888881', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '66666666-6666-6666-6666-666666666661', now() - interval '2 day', 'qr', '{"scanner":"front-desk"}'::jsonb),
  ('88888888-8888-8888-8888-888888888882', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '66666666-6666-6666-6666-666666666661', now() - interval '1 day', 'qr', '{"scanner":"front-desk"}'::jsonb)
on conflict do nothing;