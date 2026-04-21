import type {
  AuthSession,
  BootstrapResponse,
  ClientConfig,
  MediaAsset,
  MemberRecord,
  PushSyncResponse,
  QueueOperation,
  RoutineCatalogItem,
  RoutineUpsertInput,
  VersionInfo
} from "@/lib/types";

const API_BASE_KEY = "force_gym.api_base.v1";

function normalizeApiBase(value: string | null | undefined) {
  if (!value) {
    return null;
  }

  const trimmed = value.trim().replace(/\/$/, "");
  if (!trimmed) {
    return null;
  }

  if (trimmed.startsWith("/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
    return trimmed;
  }

  return null;
}

export function readStoredApiBase() {
  if (typeof window === "undefined") {
    return null;
  }

  return normalizeApiBase(window.localStorage.getItem(API_BASE_KEY));
}

export function persistApiBase(value: string | null) {
  if (typeof window === "undefined") {
    return;
  }

  const normalized = normalizeApiBase(value);
  if (!normalized) {
    window.localStorage.removeItem(API_BASE_KEY);
    return;
  }

  window.localStorage.setItem(API_BASE_KEY, normalized);
}

export function resolveApiBase() {
  return normalizeApiBase(process.env.NEXT_PUBLIC_FORCE_GYM_API_BASE) ?? "/api";
}

async function requestJson<T>(path: string, init: RequestInit = {}, session?: AuthSession) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10_000);
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (session?.accessToken) {
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }

  try {
    const response = await fetch(`${resolveApiBase()}${path}`, {
      ...init,
      headers,
      signal: controller.signal
    });

    if (!response.ok) {
      const fallback = response.status === 401 ? "Credenciales invalidas." : "No se pudo completar la solicitud.";
      const detail = await response.text();
      throw new Error(detail || fallback);
    }

    if (response.status === 204) {
      return null as T;
    }

    const raw = await response.text();
    return (raw ? JSON.parse(raw) : null) as T;
  } finally {
    window.clearTimeout(timeout);
  }
}

export function login(username: string, password: string) {
  return requestJson<AuthSession>("/gateway/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
}

export function bootstrap(session: AuthSession) {
  return requestJson<BootstrapResponse>("/membresia/bootstrap", {}, session);
}

export async function pullMembers(session: AuthSession) {
  const response = await requestJson<{
    upserts?: Array<{
      entityType?: string;
      payload?: MemberRecord;
    }>;
  }>("/membresia/sync/pull", {}, session);

  return (response.upserts ?? [])
    .filter((entry) => entry.entityType === "member" && entry.payload)
    .map((entry) => entry.payload as MemberRecord);
}

export function pushOperations(operations: QueueOperation[], session: AuthSession) {
  return requestJson<PushSyncResponse>("/membresia/sync/push", {
    method: "POST",
    body: JSON.stringify({ operations })
  }, session);
}

export function fetchClientConfig(session: AuthSession) {
  return requestJson<ClientConfig>("/config/public/client", {}, session);
}

export function fetchVersionInfo(session: AuthSession) {
  return requestJson<VersionInfo>("/config/public/version", {}, session);
}

export function fetchRoutineCatalog(session: AuthSession) {
  return requestJson<RoutineCatalogItem[]>("/rutinas/catalog", {}, session);
}

export function createRoutine(input: RoutineUpsertInput, session: AuthSession) {
  return requestJson<RoutineCatalogItem>("/rutinas/catalog", {
    method: "POST",
    body: JSON.stringify(input)
  }, session);
}

export function updateRoutine(routineId: string, input: RoutineUpsertInput, session: AuthSession) {
  return requestJson<RoutineCatalogItem>(`/rutinas/catalog/${routineId}`, {
    method: "PUT",
    body: JSON.stringify(input)
  }, session);
}

export async function deleteRoutine(routineId: string, session: AuthSession) {
  await requestJson<null>(`/rutinas/catalog/${routineId}`, {
    method: "DELETE"
  }, session);
}

export function fetchMediaAssets(session: AuthSession) {
  return requestJson<MediaAsset[]>("/media/assets", {}, session);
}