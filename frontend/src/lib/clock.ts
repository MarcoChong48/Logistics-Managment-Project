// The sample clock. The sample world was recorded at 2026-11-12 09:14 (browser time) and the clock runs
// on from there in real time, so ages such as "Returned 7 days ago" stay true while you use the app.

const SAMPLE_START = new Date(2026, 10, 12, 9, 14, 0, 0).getTime();
const REAL_START = Date.now();

/** The current sample time in milliseconds. */
export function now(): number {
  return SAMPLE_START + (Date.now() - REAL_START);
}

/** The moment the sample world was recorded. */
export const SAMPLE_MOMENT = SAMPLE_START;

/** The start of the sample day (00:00 of the current sample date). */
export function startOfToday(): number {
  const d = new Date(now());
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}
