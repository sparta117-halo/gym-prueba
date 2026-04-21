"use client";

import { FormEvent, useDeferredValue, useEffect, useMemo, useState, useTransition } from "react";

import { InstallBanner } from "@/components/install-banner";
import {
  bootstrap,
  createRoutine,
  deleteRoutine,
  fetchClientConfig,
  fetchMediaAssets,
  fetchRoutineCatalog,
  fetchVersionInfo,
  login,
  pullMembers,
  pushOperations,
  updateRoutine
} from "@/lib/api";
import { addMonthsIso, formatDateLabel, getDaysRemaining, isMemberActive, MONTHLY_PRICE, toDateInputValue } from "@/lib/member-utils";
import { clearQueue, enqueueOperation, getAllMembers, getQueueCount, getQueuedOperations, removeMember, replaceMembers, upsertMember } from "@/lib/offline-store";
import type {
  AttendanceSummary,
  AuthSession,
  BootstrapMeta,
  ClientConfig,
  MediaAsset,
  MemberRecord,
  PaymentSummary,
  RoutineCatalogItem,
  RoutineUpsertInput,
  VersionInfo
} from "@/lib/types";

const SESSION_KEY = "force_gym.session.v1";
const DEVICE_KEY = "force_gym.device_id.v1";
const LAST_SYNC_KEY = "force_gym.last_sync.v1";

type DraftState = {
  code: string;
  nombre: string;
  apellido: string;
  telefono: string;
  password: string;
  fechaInicio: string;
  meses: string;
};

type RoutineDraftState = {
  code: string;
  name: string;
  level: string;
  daysPerWeek: string;
  durationMinutes: string;
  objective: string;
  notes: string;
  exercises: string;
};

const emptyDraft = (): DraftState => ({
  code: "",
  nombre: "",
  apellido: "",
  telefono: "",
  password: "123456",
  fechaInicio: toDateInputValue(),
  meses: "1"
});

const emptyRoutineDraft = (): RoutineDraftState => ({
  code: "",
  name: "",
  level: "starter",
  daysPerWeek: "3",
  durationMinutes: "45",
  objective: "",
  notes: "",
  exercises: ""
});

function readSession() {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

function writeSession(session: AuthSession | null) {
  if (typeof window === "undefined") {
    return;
  }

  if (!session) {
    window.localStorage.removeItem(SESSION_KEY);
    return;
  }

  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

function getDeviceId() {
  if (typeof window === "undefined") {
    return "browser-device";
  }

  const existing = window.localStorage.getItem(DEVICE_KEY);
  if (existing) {
    return existing;
  }

  const created = crypto.randomUUID();
  window.localStorage.setItem(DEVICE_KEY, created);
  return created;
}

function toMemberRecord(draft: DraftState, existing?: MemberRecord): MemberRecord {
  const startIso = new Date(`${draft.fechaInicio}T00:00:00.000Z`).toISOString();
  const months = Math.max(1, Number.parseInt(draft.meses, 10) || 1);

  return {
    id: existing?.id ?? crypto.randomUUID(),
    code: draft.code.trim().toUpperCase(),
    password: draft.password.trim() || "123456",
    nombre: draft.nombre.trim(),
    apellido: draft.apellido.trim(),
    telefono: draft.telefono.trim(),
    fechaInicio: startIso,
    fechaFin: addMonthsIso(startIso, months),
    meses: months,
    diaPago: new Date(startIso).getUTCDate(),
    asistencias: existing?.asistencias ?? [],
    pagos: existing?.pagos ?? [],
    progreso: existing?.progreso ?? []
  };
}

function toDraft(member: MemberRecord): DraftState {
  return {
    code: member.code,
    nombre: member.nombre,
    apellido: member.apellido,
    telefono: member.telefono,
    password: member.password,
    fechaInicio: toDateInputValue(member.fechaInicio),
    meses: `${member.meses}`
  };
}

function toRoutineDraft(routine: RoutineCatalogItem): RoutineDraftState {
  return {
    code: routine.code,
    name: routine.name,
    level: routine.level,
    daysPerWeek: `${routine.daysPerWeek}`,
    durationMinutes: `${routine.durationMinutes}`,
    objective: routine.objective,
    notes: routine.notes,
    exercises: routine.exercises.join("\n")
  };
}

function toRoutinePayload(draft: RoutineDraftState): RoutineUpsertInput {
  return {
    code: draft.code.trim().toUpperCase(),
    name: draft.name.trim(),
    level: draft.level.trim() || "starter",
    daysPerWeek: Math.max(1, Math.min(7, Number.parseInt(draft.daysPerWeek, 10) || 3)),
    durationMinutes: Math.max(10, Number.parseInt(draft.durationMinutes, 10) || 45),
    objective: draft.objective.trim(),
    notes: draft.notes.trim(),
    exercises: draft.exercises
      .split(/\r?\n|,/) 
      .map((exercise) => exercise.trim())
      .filter(Boolean)
  };
}

function memberFullName(member: MemberRecord) {
  return `${member.nombre} ${member.apellido}`.trim();
}

export function DashboardShell() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [members, setMembers] = useState<MemberRecord[]>([]);
  const [queueCount, setQueueCount] = useState(0);
  const [tenantMeta, setTenantMeta] = useState<BootstrapMeta | null>(null);
  const [clientConfig, setClientConfig] = useState<ClientConfig | null>(null);
  const [versionInfo, setVersionInfo] = useState<VersionInfo | null>(null);
  const [routineCatalog, setRoutineCatalog] = useState<RoutineCatalogItem[]>([]);
  const [mediaAssets, setMediaAssets] = useState<MediaAsset[]>([]);
  const [serverPayments, setServerPayments] = useState<PaymentSummary[]>([]);
  const [serverAttendances, setServerAttendances] = useState<AttendanceSummary[]>([]);
  const [online, setOnline] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [loginBusy, setLoginBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [routineBusy, setRoutineBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastSyncAt, setLastSyncAt] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [draft, setDraft] = useState<DraftState>(emptyDraft);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [routineDraft, setRoutineDraft] = useState<RoutineDraftState>(emptyRoutineDraft);
  const [editingRoutineId, setEditingRoutineId] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const deferredSearch = useDeferredValue(search);

  const filteredMembers = useMemo(() => {
    const query = deferredSearch.trim().toLowerCase();
    if (!query) {
      return members;
    }

    return members.filter((member) => `${member.nombre} ${member.apellido} ${member.code}`.toLowerCase().includes(query));
  }, [deferredSearch, members]);

  const activeMembers = useMemo(() => members.filter(isMemberActive).length, [members]);
  const localPaymentTotal = useMemo(() => members.reduce((sum, member) => sum + member.pagos.reduce((acc, payment) => acc + payment.monto, 0), 0), [members]);
  const localAttendanceCount = useMemo(() => members.reduce((sum, member) => sum + member.asistencias.length, 0), [members]);

  const latestPayments = useMemo(
    () =>
      members
        .flatMap((member) =>
          member.pagos.map((payment) => ({
            ...payment,
            memberCode: member.code,
            memberName: memberFullName(member)
          }))
        )
        .sort((left, right) => right.fecha.localeCompare(left.fecha))
        .slice(0, 8),
    [members]
  );

  const paymentStatus = useMemo(
    () =>
      members
        .map((member) => {
          const dueMs = new Date(member.fechaFin).getTime() - Date.now();
          return {
            id: member.id,
            code: member.code,
            name: memberFullName(member),
            dueAt: member.fechaFin,
            daysUntilDue: Math.ceil(dueMs / 86_400_000),
            amountPaid: member.pagos.reduce((sum, payment) => sum + payment.monto, 0),
            paymentCount: member.pagos.length,
            active: isMemberActive(member)
          };
        })
        .sort((left, right) => new Date(left.dueAt).getTime() - new Date(right.dueAt).getTime()),
    [members]
  );

  const overdueMembers = useMemo(() => paymentStatus.filter((member) => member.daysUntilDue < 0).slice(0, 6), [paymentStatus]);
  const dueSoonMembers = useMemo(() => paymentStatus.filter((member) => member.daysUntilDue >= 0 && member.daysUntilDue <= 7).slice(0, 6), [paymentStatus]);

  const monthlyCollection = useMemo(() => {
    const currentMonth = new Date().toISOString().slice(0, 7);
    return members.reduce(
      (sum, member) => sum + member.pagos.filter((payment) => payment.fecha.startsWith(currentMonth)).reduce((acc, payment) => acc + payment.monto, 0),
      0
    );
  }, [members]);

  const latestAttendances = useMemo(() => [...serverAttendances].sort((left, right) => right.occurredAt.localeCompare(left.occurredAt)).slice(0, 8), [serverAttendances]);

  async function refreshLocalState() {
    const [storedMembers, pendingCount] = await Promise.all([getAllMembers(), getQueueCount()]);
    setMembers(storedMembers);
    setQueueCount(pendingCount);
  }

  async function refreshRoutineCatalog(currentSession = session) {
    if (!currentSession) {
      return;
    }

    const catalog = await fetchRoutineCatalog(currentSession);
    setRoutineCatalog(catalog);
  }

  async function syncNow(currentSession = session) {
    if (!currentSession || !navigator.onLine) {
      return;
    }

    setSyncing(true);
    setError(null);

    try {
      const queued = await getQueuedOperations();
      if (queued.length > 0) {
        const pushed = await pushOperations(queued, currentSession);
        if (pushed.accepted === queued.length && pushed.rejected === 0) {
          await clearQueue();
        }
      }

      const [metaResponse, remoteMembers, nextClientConfig, nextVersionInfo, nextRoutineCatalog, nextMediaAssets] = await Promise.all([
        bootstrap(currentSession),
        pullMembers(currentSession),
        fetchClientConfig(currentSession),
        fetchVersionInfo(currentSession),
        fetchRoutineCatalog(currentSession),
        fetchMediaAssets(currentSession)
      ]);

      setTenantMeta(metaResponse.meta);
      setClientConfig(nextClientConfig);
      setVersionInfo(nextVersionInfo);
      setRoutineCatalog(nextRoutineCatalog);
      setMediaAssets(nextMediaAssets);
      setServerPayments(metaResponse.payments);
      setServerAttendances(metaResponse.attendances);
      await replaceMembers(remoteMembers);
      await refreshLocalState();

      const syncedAt = new Date().toISOString();
      setLastSyncAt(syncedAt);
      window.localStorage.setItem(LAST_SYNC_KEY, syncedAt);
    } catch (syncError) {
      const message = syncError instanceof Error ? syncError.message : "Fallo la sincronizacion.";
      setError(message);
    } finally {
      setSyncing(false);
    }
  }

  async function queueMember(member: MemberRecord) {
    await upsertMember(member);
    await enqueueOperation({
      id: crypto.randomUUID(),
      entityType: "member",
      entityId: member.id,
      operationType: "UPSERT",
      payload: member,
      deviceId: getDeviceId(),
      requestedAt: new Date().toISOString(),
      queuedAt: new Date().toISOString()
    });

    await refreshLocalState();

    if (navigator.onLine && session) {
      void syncNow(session);
    }
  }

  async function deleteQueuedMember(member: MemberRecord) {
    await removeMember(member.id);
    await enqueueOperation({
      id: crypto.randomUUID(),
      entityType: "member",
      entityId: member.id,
      operationType: "DELETE",
      payload: { id: member.id, code: member.code },
      deviceId: getDeviceId(),
      requestedAt: new Date().toISOString(),
      queuedAt: new Date().toISOString()
    });

    await refreshLocalState();

    if (navigator.onLine && session) {
      void syncNow(session);
    }
  }

  useEffect(() => {
    const storedSession = readSession();
    setSession(storedSession);
    setOnline(window.navigator.onLine);
    setLastSyncAt(window.localStorage.getItem(LAST_SYNC_KEY));
    void refreshLocalState();

    if (storedSession && window.navigator.onLine) {
      void syncNow(storedSession);
    }

    const handleOnline = () => {
      setOnline(true);
      const currentSession = readSession();
      if (currentSession) {
        void syncNow(currentSession);
      }
    };

    const handleOffline = () => {
      setOnline(false);
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoginBusy(true);
    setError(null);

    try {
      const authenticated = await login(username, password);
      writeSession(authenticated);
      setSession(authenticated);
      await syncNow(authenticated);
    } catch (loginError) {
      const message = loginError instanceof Error ? loginError.message : "No se pudo iniciar sesion.";
      setError(message);
    } finally {
      setLoginBusy(false);
    }
  }

  async function handleSaveMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const existing = editingId ? members.find((member) => member.id === editingId) : undefined;
    const member = toMemberRecord(draft, existing);

    await queueMember(member);
    startTransition(() => {
      setEditingId(null);
      setDraft(emptyDraft());
    });
  }

  async function handleAttendance(member: MemberRecord) {
    await queueMember({
      ...member,
      asistencias: [new Date().toISOString(), ...member.asistencias]
    });
  }

  async function handlePayment(member: MemberRecord) {
    const baseline = new Date(member.fechaFin).getTime() > Date.now() ? member.fechaFin : new Date().toISOString();

    await queueMember({
      ...member,
      fechaFin: addMonthsIso(baseline, 1),
      pagos: [
        {
          id: crypto.randomUUID(),
          fecha: new Date().toISOString(),
          monto: MONTHLY_PRICE,
          meses: 1
        },
        ...member.pagos
      ]
    });
  }

  async function handleSaveRoutine(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }

    setRoutineBusy(true);
    setError(null);

    try {
      const payload = toRoutinePayload(routineDraft);
      if (editingRoutineId) {
        await updateRoutine(editingRoutineId, payload, session);
      } else {
        await createRoutine(payload, session);
      }

      await refreshRoutineCatalog(session);
      setEditingRoutineId(null);
      setRoutineDraft(emptyRoutineDraft());
    } catch (routineError) {
      const message = routineError instanceof Error ? routineError.message : "No se pudo guardar la rutina.";
      setError(message);
    } finally {
      setRoutineBusy(false);
    }
  }

  async function handleDeleteRoutine(routineId: string) {
    if (!session) {
      return;
    }

    setRoutineBusy(true);
    setError(null);

    try {
      await deleteRoutine(routineId, session);
      await refreshRoutineCatalog(session);
      if (editingRoutineId === routineId) {
        setEditingRoutineId(null);
        setRoutineDraft(emptyRoutineDraft());
      }
    } catch (routineError) {
      const message = routineError instanceof Error ? routineError.message : "No se pudo eliminar la rutina.";
      setError(message);
    } finally {
      setRoutineBusy(false);
    }
  }

  /* ── VIEW state ── */
  type DashView = "home" | "members" | "register" | "expirations" | "stats" | "routines";
  const [view, setView] = useState<DashView>("home");

  function navigate(next: DashView) {
    setView(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  if (!session) {
    return (
      <main className="login-shell">
        <InstallBanner />

        <div className="login-brand">
          <div className="login-logo">
            <svg viewBox="0 0 64 64" width="54" height="54" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="6" y="24" width="8" height="16" rx="2" fill="#fff"/>
              <rect x="50" y="24" width="8" height="16" rx="2" fill="#fff"/>
              <rect x="14" y="20" width="6" height="24" rx="2" fill="#fff"/>
              <rect x="44" y="20" width="6" height="24" rx="2" fill="#fff"/>
              <rect x="20" y="28" width="24" height="8" rx="2" fill="#fff"/>
            </svg>
          </div>
          <h1 className="login-title">Force Gym Fitness</h1>
          <p className="login-subtitle">Panel de Administrador</p>
        </div>

        <section className="login-card">
          <h2 className="login-card__heading">Iniciar sesión</h2>

          <form className="login-form" onSubmit={handleLogin}>
            <div className="login-field">
              <span className="login-field__icon">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M16 3h-4a2 2 0 0 0-2 2v2h8V5a2 2 0 0 0-2-2Z"/></svg>
              </span>
              <input
                autoCapitalize="none"
                autoComplete="username"
                className="login-field__input"
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Código admin"
                value={username}
              />
            </div>

            <div className="login-field">
              <span className="login-field__icon">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/><circle cx="12" cy="16" r="1"/></svg>
              </span>
              <input
                autoComplete="current-password"
                className="login-field__input"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Contraseña"
                type={showPassword ? "text" : "password"}
                value={password}
              />
              <button
                className="login-field__toggle"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                type="button"
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                ) : (
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z"/><circle cx="12" cy="12" r="3"/></svg>
                )}
              </button>
            </div>

            {error ? <p className="login-error">{error}</p> : null}

            <button className="login-btn" disabled={loginBusy} type="submit">
              {loginBusy ? "CONECTANDO..." : "INICIAR SESIÓN"}
            </button>

            <button
              className="login-link"
              onClick={() => {
                const storedSession = readSession();
                if (!storedSession) {
                  setError("Todavia no hay una sesion guardada para usar sin conexion.");
                  return;
                }
                setSession(storedSession);
                setError(null);
              }}
              type="button"
            >
              Acceso de miembro
            </button>
          </form>
        </section>

        <div className="login-sync-status">
          {error ? (
            <span className="login-sync-pill login-sync-pill--error">No se pudo sincronizar</span>
          ) : syncing ? (
            <span className="login-sync-pill login-sync-pill--busy">Sincronizando...</span>
          ) : null}
        </div>
      </main>
    );
  }

  /* ── Shared back button ── */
  const BackBtn = () => (
    <button className="dash-back" onClick={() => navigate("home")} type="button">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 12H5"/><path d="m12 5-7 7 7 7"/></svg>
      Inicio
    </button>
  );

  /* ── MEMBERS list view ── */
  if (view === "members") {
    return (
      <main className="dash-shell">
        <InstallBanner />
        <header className="dash-topbar">
          <BackBtn />
          <div className="dash-topbar__right">
            <span className={`dash-online-dot ${online ? "is-online" : "is-offline"}`} />
            <button className="dash-sync-btn" disabled={syncing} onClick={() => void syncNow()} type="button">
              {syncing ? "Sincronizando..." : "Sincronizar"}
            </button>
          </div>
        </header>

        <section className="dash-section">
          <h2 className="dash-section-title">Miembros</h2>
          <input
            className="dash-search"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Buscar por nombre o código..."
            value={search}
          />
          {error ? <p className="dash-error">{error}</p> : null}
          <div className="dash-member-list">
            {filteredMembers.length === 0 ? <p className="dash-empty">Sin miembros registrados.</p> : null}
            {filteredMembers.map((member) => (
              <article className="dash-member-card" key={member.id}>
                <div className="dash-member-card__top">
                  <div>
                    <strong className="dash-member-name">{memberFullName(member)}</strong>
                    <span className="dash-member-code">{member.code}</span>
                  </div>
                  <span className={`dash-badge ${isMemberActive(member) ? "dash-badge--green" : "dash-badge--red"}`}>
                    {isMemberActive(member) ? "Activo" : "Vencido"}
                  </span>
                </div>
                <dl className="dash-member-meta">
                  <div><dt>Teléfono</dt><dd>{member.telefono || "—"}</dd></div>
                  <div><dt>Vence</dt><dd>{formatDateLabel(member.fechaFin)}</dd></div>
                  <div><dt>Restan</dt><dd>{getDaysRemaining(member)} días</dd></div>
                  <div><dt>Pagos</dt><dd>{member.pagos.length}</dd></div>
                </dl>
                <div className="dash-member-actions">
                  <button className="dash-action-btn dash-action-btn--outline" onClick={() => void handleAttendance(member)} type="button">Asistencia</button>
                  <button className="dash-action-btn dash-action-btn--primary" onClick={() => void handlePayment(member)} type="button">Cobrar</button>
                  <button className="dash-action-btn dash-action-btn--outline" onClick={() => { setEditingId(member.id); setDraft(toDraft(member)); navigate("register"); }} type="button">Editar</button>
                  <button className="dash-action-btn dash-action-btn--danger" onClick={() => void deleteQueuedMember(member)} type="button">Eliminar</button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    );
  }

  /* ── REGISTER member view ── */
  if (view === "register") {
    return (
      <main className="dash-shell">
        <InstallBanner />
        <header className="dash-topbar">
          <BackBtn />
        </header>
        <section className="dash-section">
          <h2 className="dash-section-title">{editingId ? "Editar miembro" : "Registrar miembro"}</h2>
          {error ? <p className="dash-error">{error}</p> : null}
          <form className="dash-form" onSubmit={async (e) => { await handleSaveMember(e); navigate("members"); }}>
            <div className="dash-form-grid">
              <label className="dash-field">
                <span>Código</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, code: event.target.value }))} required value={draft.code} />
              </label>
              <label className="dash-field">
                <span>Nombre</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, nombre: event.target.value }))} required value={draft.nombre} />
              </label>
              <label className="dash-field">
                <span>Apellido</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, apellido: event.target.value }))} required value={draft.apellido} />
              </label>
              <label className="dash-field">
                <span>Teléfono</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, telefono: event.target.value }))} value={draft.telefono} />
              </label>
              <label className="dash-field">
                <span>Clave del socio</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, password: event.target.value }))} value={draft.password} />
              </label>
              <label className="dash-field">
                <span>Fecha inicio</span>
                <input onChange={(event) => setDraft((c) => ({ ...c, fechaInicio: event.target.value }))} required type="date" value={draft.fechaInicio} />
              </label>
              <label className="dash-field">
                <span>Meses</span>
                <input min="1" onChange={(event) => setDraft((c) => ({ ...c, meses: event.target.value }))} required type="number" value={draft.meses} />
              </label>
            </div>
            <div className="dash-form-actions">
              <button className="dash-action-btn dash-action-btn--primary dash-action-btn--full" disabled={isPending} type="submit">
                {editingId ? "Actualizar" : "Guardar miembro"}
              </button>
              <button className="dash-action-btn dash-action-btn--outline dash-action-btn--full" onClick={() => { setEditingId(null); setDraft(emptyDraft()); navigate("home"); }} type="button">
                Cancelar
              </button>
            </div>
          </form>
        </section>
      </main>
    );
  }

  /* ── EXPIRATIONS view ── */
  if (view === "expirations") {
    return (
      <main className="dash-shell">
        <InstallBanner />
        <header className="dash-topbar"><BackBtn /></header>
        <section className="dash-section">
          <h2 className="dash-section-title">Vencimientos</h2>
          <div className="dash-kpi-grid">
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--red"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg></div>
              <div><p className="dash-kpi-label">Vencidos</p><strong className="dash-kpi-value dash-kpi-value--red">{overdueMembers.length}</strong></div>
            </article>
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--amber"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></div>
              <div><p className="dash-kpi-label">Vencen en 7 días</p><strong className="dash-kpi-value dash-kpi-value--amber">{dueSoonMembers.length}</strong></div>
            </article>
          </div>
          <div className="dash-member-list">
            {overdueMembers.length === 0 && dueSoonMembers.length === 0 ? <p className="dash-empty">Sin vencimientos próximos.</p> : null}
            {[...overdueMembers, ...dueSoonMembers].map((m) => (
              <article className="dash-member-card" key={m.id}>
                <div className="dash-member-card__top">
                  <div>
                    <strong className="dash-member-name">{m.name}</strong>
                    <span className="dash-member-code">{m.code}</span>
                  </div>
                  <span className={`dash-badge ${m.daysUntilDue < 0 ? "dash-badge--red" : "dash-badge--amber"}`}>
                    {m.daysUntilDue < 0 ? `${Math.abs(m.daysUntilDue)} días vencido` : `${m.daysUntilDue} días`}
                  </span>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    );
  }

  /* ── STATS view ── */
  if (view === "stats") {
    return (
      <main className="dash-shell">
        <InstallBanner />
        <header className="dash-topbar"><BackBtn /></header>
        <section className="dash-section">
          <h2 className="dash-section-title">Estadísticas</h2>
          <div className="dash-kpi-grid">
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--blue"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="7" width="4" height="14"/><rect x="10" y="3" width="4" height="18"/><rect x="18" y="11" width="4" height="10"/></svg></div>
              <div><p className="dash-kpi-label">Asistencias totales</p><strong className="dash-kpi-value">{localAttendanceCount}</strong></div>
            </article>
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--green"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg></div>
              <div><p className="dash-kpi-label">Ingresos totales</p><strong className="dash-kpi-value dash-kpi-value--green">Q{localPaymentTotal.toFixed(0)}</strong></div>
            </article>
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--blue"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg></div>
              <div><p className="dash-kpi-label">Ingresos del mes</p><strong className="dash-kpi-value dash-kpi-value--blue">Q{monthlyCollection.toFixed(0)}</strong></div>
            </article>
            <article className="dash-kpi-card">
              <div className="dash-kpi-icon dash-kpi-icon--purple"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg></div>
              <div><p className="dash-kpi-label">Rutinas</p><strong className="dash-kpi-value">{routineCatalog.length}</strong></div>
            </article>
          </div>
          <h3 className="dash-subsection-title">Últimos pagos</h3>
          <div className="dash-timeline">
            {latestPayments.length === 0 ? <p className="dash-empty">Sin pagos registrados.</p> : null}
            {latestPayments.map((payment) => (
              <div className="dash-timeline-item" key={payment.id}>
                <div><strong>{payment.memberName}</strong><span>{payment.memberCode}</span></div>
                <div className="dash-timeline-item__right"><strong className="dash-kpi-value--green">Q{payment.monto.toFixed(0)}</strong><span>{formatDateLabel(payment.fecha)}</span></div>
              </div>
            ))}
          </div>
        </section>
      </main>
    );
  }

  /* ── ROUTINES view ── */
  if (view === "routines") {
    return (
      <main className="dash-shell">
        <InstallBanner />
        <header className="dash-topbar"><BackBtn /></header>
        <section className="dash-section">
          <h2 className="dash-section-title">Rutinas</h2>
          {error ? <p className="dash-error">{error}</p> : null}
          <form className="dash-form" onSubmit={handleSaveRoutine}>
            <div className="dash-form-grid">
              <label className="dash-field"><span>Código</span><input onChange={(e) => setRoutineDraft((c) => ({ ...c, code: e.target.value }))} required value={routineDraft.code} /></label>
              <label className="dash-field"><span>Nombre</span><input onChange={(e) => setRoutineDraft((c) => ({ ...c, name: e.target.value }))} required value={routineDraft.name} /></label>
              <label className="dash-field"><span>Nivel</span><input onChange={(e) => setRoutineDraft((c) => ({ ...c, level: e.target.value }))} required value={routineDraft.level} /></label>
              <label className="dash-field"><span>Días/semana</span><input max="7" min="1" onChange={(e) => setRoutineDraft((c) => ({ ...c, daysPerWeek: e.target.value }))} required type="number" value={routineDraft.daysPerWeek} /></label>
              <label className="dash-field"><span>Duración (min)</span><input min="10" onChange={(e) => setRoutineDraft((c) => ({ ...c, durationMinutes: e.target.value }))} required type="number" value={routineDraft.durationMinutes} /></label>
              <label className="dash-field"><span>Objetivo</span><input onChange={(e) => setRoutineDraft((c) => ({ ...c, objective: e.target.value }))} value={routineDraft.objective} /></label>
            </div>
            <label className="dash-field"><span>Notas</span><textarea onChange={(e) => setRoutineDraft((c) => ({ ...c, notes: e.target.value }))} value={routineDraft.notes} /></label>
            <label className="dash-field"><span>Ejercicios</span><textarea onChange={(e) => setRoutineDraft((c) => ({ ...c, exercises: e.target.value }))} placeholder="Una línea por ejercicio" value={routineDraft.exercises} /></label>
            <div className="dash-form-actions">
              <button className="dash-action-btn dash-action-btn--primary" disabled={routineBusy} type="submit">{routineBusy ? "Guardando..." : editingRoutineId ? "Actualizar" : "Crear rutina"}</button>
              <button className="dash-action-btn dash-action-btn--outline" onClick={() => { setEditingRoutineId(null); setRoutineDraft(emptyRoutineDraft()); }} type="button">Limpiar</button>
            </div>
          </form>
          <div className="dash-member-list">
            {routineCatalog.length === 0 ? <p className="dash-empty">Sin rutinas cargadas.</p> : null}
            {routineCatalog.map((routine) => (
              <article className="dash-member-card" key={routine.id}>
                <div className="dash-member-card__top">
                  <div><strong className="dash-member-name">{routine.name}</strong><span className="dash-member-code">{routine.code} · {routine.level}</span></div>
                  <span className="dash-badge dash-badge--blue">{routine.daysPerWeek} días</span>
                </div>
                <p style={{ color: "#aaa", fontSize: "0.88rem", margin: "8px 0 0" }}>{routine.objective || routine.notes || "Sin descripción."}</p>
                <div className="dash-member-actions">
                  <button className="dash-action-btn dash-action-btn--outline" onClick={() => { setEditingRoutineId(routine.id); setRoutineDraft(toRoutineDraft(routine)); }} type="button">Editar</button>
                  <button className="dash-action-btn dash-action-btn--danger" onClick={() => void handleDeleteRoutine(routine.id)} type="button">Eliminar</button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    );
  }

  /* ── HOME dashboard (main screen matching screenshot) ── */
  return (
    <main className="dash-shell">
      <InstallBanner />

      {/* Top bar */}
      <header className="dash-header">
        <div className="dash-header__brand">
          <div className="dash-header__logo">
            <svg viewBox="0 0 64 64" width="26" height="26" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="6" y="24" width="8" height="16" rx="2" fill="#e74c3c"/>
              <rect x="50" y="24" width="8" height="16" rx="2" fill="#e74c3c"/>
              <rect x="14" y="20" width="6" height="24" rx="2" fill="#e74c3c"/>
              <rect x="44" y="20" width="6" height="24" rx="2" fill="#e74c3c"/>
              <rect x="20" y="28" width="24" height="8" rx="2" fill="#e74c3c"/>
            </svg>
          </div>
          <div>
            <p className="dash-header__name">Force Gym Fitness</p>
            <p className="dash-header__role">{session.user.userType === "ADMIN" ? "Administrador" : session.user.userType}</p>
          </div>
        </div>
        <button
          className="dash-logout"
          onClick={() => { writeSession(null); setSession(null); setTenantMeta(null); setError(null); }}
          title="Cerrar sesión"
          type="button"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </button>
      </header>

      {error ? <p className="dash-error">{error}</p> : null}

      {/* ── Ingresos ── */}
      <section className="dash-section">
        <h2 className="dash-section-title">Ingresos</h2>
        <div className="dash-kpi-grid">
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--green">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Ingresos totales</p>
              <strong className="dash-kpi-value dash-kpi-value--green">Q{localPaymentTotal.toFixed(0)}</strong>
            </div>
          </article>
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--blue">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Ingresos del mes</p>
              <strong className="dash-kpi-value dash-kpi-value--blue">Q{monthlyCollection.toFixed(0)}</strong>
            </div>
          </article>
        </div>
      </section>

      {/* ── Miembros ── */}
      <section className="dash-section">
        <h2 className="dash-section-title">Miembros</h2>
        <div className="dash-kpi-grid">
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--muted">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Total</p>
              <strong className="dash-kpi-value">{members.length}</strong>
            </div>
          </article>
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--green">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Activos</p>
              <strong className="dash-kpi-value dash-kpi-value--green">{activeMembers}</strong>
            </div>
          </article>
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--red">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Vencidos</p>
              <strong className="dash-kpi-value dash-kpi-value--red">{overdueMembers.length}</strong>
            </div>
          </article>
          <article className="dash-kpi-card">
            <div className="dash-kpi-icon dash-kpi-icon--amber">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
            <div>
              <p className="dash-kpi-label">Vencen en 7 días</p>
              <strong className="dash-kpi-value dash-kpi-value--amber">{dueSoonMembers.length}</strong>
            </div>
          </article>
        </div>
      </section>

      {/* ── Acciones ── */}
      <section className="dash-section">
        <h2 className="dash-section-title">Acciones</h2>
        <div className="dash-actions-grid">
          <button className="dash-action-tile" onClick={() => { setEditingId(null); setDraft(emptyDraft()); navigate("register"); }} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--red">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg>
            </div>
            <span>Registrar miembro</span>
          </button>

          <button className="dash-action-tile" onClick={() => navigate("members")} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--teal">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
            </div>
            <span>Ver miembros</span>
          </button>

          <button className="dash-action-tile" onClick={() => navigate("expirations")} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--blue">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            </div>
            <span>Vencimientos</span>
          </button>

          <button className="dash-action-tile" onClick={() => void 0} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--purple">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
            </div>
            <span>Escanear QR</span>
          </button>

          <button className="dash-action-tile" onClick={() => navigate("stats")} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--amber">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="7" width="4" height="14"/><rect x="10" y="3" width="4" height="18"/><rect x="18" y="11" width="4" height="10"/></svg>
            </div>
            <span>Estadísticas</span>
          </button>

          <button className="dash-action-tile" onClick={() => navigate("routines")} type="button">
            <div className="dash-action-tile__icon dash-action-tile__icon--teal">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 20V10"/><path d="M12 20V4"/><path d="M6 20v-6"/></svg>
            </div>
            <span>Rutinas</span>
          </button>
        </div>

        {/* sync status row */}
        <div className="dash-sync-row">
          <span className={`dash-online-dot ${online ? "is-online" : "is-offline"}`} />
          <span className="dash-sync-label">{online ? "En línea" : "Sin conexión"}{queueCount > 0 ? ` · ${queueCount} pendientes` : ""}</span>
          {online ? (
            <button className="dash-sync-btn" disabled={syncing} onClick={() => void syncNow()} type="button">
              {syncing ? "Sincronizando..." : "Sincronizar"}
            </button>
          ) : null}
        </div>
      </section>
    </main>
  );
}
