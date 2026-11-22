// Formatting helpers. Every page writes money, times, counts and stored words through these,
// so the same value always looks the same (design system MASTER.md, Section 13.5).

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** Money with its currency code and two decimals, for example "HKD 1,234.50". */
export function formatMoney(amount: number): string {
  const fixed = Math.abs(amount).toFixed(2);
  const [whole, cents] = fixed.split(".");
  const withCommas = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${amount < 0 ? "-" : ""}HKD\u00a0${withCommas}.${cents}`;
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/** A time as "2026-11-12 09:14" (the browser's own time, no zone label). Empty values become "-". */
export function formatTime(time: number | null | undefined): string {
  if (time === null || time === undefined || Number.isNaN(time)) return "-";
  const d = new Date(time);
  return `${formatDate(time)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** A date as "2026-11-12". */
export function formatDate(time: number): string {
  const d = new Date(time);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Hours and minutes only, for example "09:14". */
export function formatClock(time: number): string {
  const d = new Date(time);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** The value for a datetime attribute, for example "2026-11-12T09:14". */
export function isoAttribute(time: number): string {
  const d = new Date(time);
  return `${formatDate(time)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Builds a local time from its parts (month 1 to 12). */
export function at(year: number, month: number, day: number, hour = 0, minute = 0): number {
  return new Date(year, month - 1, day, hour, minute, 0, 0).getTime();
}

/** Parses "2026-11-12 09:14" or "2026-11-12" into a local time. Returns null for anything else. */
export function parseTime(text: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?$/.exec(text.trim());
  if (!m) return null;
  return at(Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4] ?? 0), Number(m[5] ?? 0));
}

/** Full days (periods of 24 hours) between two times. */
export function fullDays(from: number, to: number): number {
  return Math.max(0, Math.floor((to - from) / DAY));
}

/** A short age such as "2 days 3 hours", "5 hours" or "40 minutes". */
export function formatAge(from: number, to: number): string {
  const span = Math.max(0, to - from);
  const days = Math.floor(span / DAY);
  const hours = Math.floor((span % DAY) / HOUR);
  const minutes = Math.floor((span % HOUR) / MINUTE);
  const part = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;
  if (days > 0) return hours > 0 ? `${part(days, "day")} ${part(hours, "hour")}` : part(days, "day");
  if (hours > 0) return part(hours, "hour");
  return part(Math.max(1, minutes), "minute");
}

/** "1 time", "2 times": a count with its word in the right form. */
export function plural(count: number, word: string, many?: string): string {
  return `${count.toLocaleString("en")} ${count === 1 ? word : (many ?? `${word}s`)}`;
}

const KEEP_CAPITALS = new Set(["ID", "QR", "SKU", "IP", "CSV", "HKD", "LMS"]);

/**
 * The plain-word rule: a stored word such as READY_FOR_PICKING is shown as "Ready for picking".
 * ID, QR, SKU, IP, CSV, HKD and LMS keep their capitals.
 */
export function plainWord(stored: string): string {
  const words = stored.split("_").filter(Boolean);
  return words
    .map((word, index) => {
      if (KEEP_CAPITALS.has(word)) return word;
      const small = word.toLowerCase();
      return index === 0 ? small.charAt(0).toUpperCase() + small.slice(1) : small;
    })
    .join(" ");
}

/** "3 of 5". */
export function ofCount(part: number, whole: number): string {
  return `${part} of ${whole}`;
}

export const TIME = { MINUTE, HOUR, DAY };

const dateOnly = /^\d{4}-\d{2}-\d{2}$/;

function dateTimeBoundary(value: string, endOfDay: boolean): string {
  return dateOnly.test(value) ? `${value}T${endOfDay ? "23:59:59.999" : "00:00"}` : value;
}

/** Preserve an exact instant from a bookmark and the inclusive bounds of an older date-only filter. */
export function filterDateTime(value: string, endOfDay = false): string {
  if (!value) return "";
  const date = new Date(dateTimeBoundary(value, endOfDay));
  return Number.isNaN(date.getTime()) ? value : date.toISOString();
}

export function dateTimeInputValue(value: string, endOfDay = false): string {
  const time = Date.parse(dateTimeBoundary(value, endOfDay));
  return Number.isNaN(time) ? "" : isoAttribute(time);
}
