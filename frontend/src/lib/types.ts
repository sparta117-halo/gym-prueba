export type UserProfile = {
  id: string;
  username: string;
  userType: string;
  roles: string[];
};

export type AuthSession = {
  accessToken: string;
  expiresAt: string;
  user: UserProfile;
};

export type PaymentRecord = {
  id: string;
  fecha: string;
  monto: number;
  meses: number;
};

export type ProgressRecord = {
  imagePath: string;
  fecha: string;
};

export type MemberRecord = {
  id: string;
  code: string;
  password: string;
  nombre: string;
  apellido: string;
  telefono: string;
  fechaInicio: string;
  fechaFin: string;
  meses: number;
  diaPago: number;
  asistencias: string[];
  pagos: PaymentRecord[];
  progreso: ProgressRecord[];
};

export type QueueOperation = {
  id: string;
  entityType: "member";
  entityId: string;
  operationType: "UPSERT" | "DELETE";
  payload: MemberRecord | Pick<MemberRecord, "id" | "code">;
  deviceId: string;
  requestedAt: string;
  queuedAt: string;
};

export type BootstrapMeta = {
  tenantId: string;
  branchId: string;
  generatedAt: string;
};

export type BootstrapResponse = {
  meta: BootstrapMeta;
  members: Array<Record<string, unknown>>;
  payments: PaymentSummary[];
  attendances: AttendanceSummary[];
};

export type PushSyncResponse = {
  accepted: number;
  rejected: number;
  processedAt: string;
  conflicts: Array<Record<string, unknown>>;
};

export type ClientConfig = {
  appName: string;
  syncIntervalMinutes: number;
  promptOnOnline: boolean;
  updateMode: string;
  timestamp: string;
};

export type VersionInfo = {
  currentVersion: string;
  minimumSupportedVersion: string;
  updateAvailable: boolean;
  timestamp: string;
};

export type RoutineCatalogItem = {
  id: string;
  code: string;
  name: string;
  level: string;
  daysPerWeek: number;
  durationMinutes: number;
  objective: string;
  notes: string;
  exercises: string[];
  createdAt: string;
  updatedAt: string;
};

export type RoutineUpsertInput = {
  code: string;
  name: string;
  level: string;
  daysPerWeek: number;
  durationMinutes: number;
  objective: string;
  notes: string;
  exercises: string[];
};

export type MediaAsset = {
  id: string;
  memberId: string;
  type: string;
  status: string;
};

export type PaymentSummary = {
  id: string;
  memberId: string;
  amount: number;
  paidAt: string;
};

export type AttendanceSummary = {
  id: string;
  memberId: string;
  occurredAt: string;
};