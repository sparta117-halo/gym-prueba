import type { MemberRecord } from "@/lib/types";

export const MONTHLY_PRICE = 125;

export function addMonthsIso(baseIso: string, months: number) {
  const date = new Date(baseIso);
  const next = new Date(date);
  next.setUTCMonth(next.getUTCMonth() + months);
  return next.toISOString();
}

export function getDaysRemaining(member: MemberRecord) {
  const diff = new Date(member.fechaFin).getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / 86_400_000));
}

export function isMemberActive(member: MemberRecord) {
  return new Date(member.fechaFin).getTime() > Date.now();
}

export function formatDateLabel(value: string) {
  return new Intl.DateTimeFormat("es-GT", {
    dateStyle: "medium",
    timeZone: "UTC"
  }).format(new Date(value));
}

export function toDateInputValue(value?: string) {
  const source = value ? new Date(value) : new Date();
  const year = source.getUTCFullYear();
  const month = `${source.getUTCMonth() + 1}`.padStart(2, "0");
  const day = `${source.getUTCDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function sortMembers(members: MemberRecord[]) {
  return [...members].sort((left, right) => {
    const leftActive = isMemberActive(left) ? 0 : 1;
    const rightActive = isMemberActive(right) ? 0 : 1;
    if (leftActive !== rightActive) {
      return leftActive - rightActive;
    }

    return left.nombre.localeCompare(right.nombre, "es");
  });
}