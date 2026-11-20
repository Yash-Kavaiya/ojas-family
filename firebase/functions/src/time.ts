// "Today" for Moksha Points is the calendar day in India, whatever the device or server timezone
export function istDay(at: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(at);
}

/** India's offset from UTC in minutes: the day apps that send no offset are counted in. */
export const IST_OFFSET_MIN = 330;

/**
 * A phone's UTC offset in minutes (India 330, New York -300 or -240), as the app sends it.
 * Anything missing or impossible (real zones run from -12 h to +14 h, in quarter hours) counts as India.
 */
export function offsetMinutes(raw: unknown): number {
  const n = Number(raw);
  if (raw === undefined || raw === null || raw === "" || !Number.isInteger(n) || n % 15 !== 0 || n < -720 || n > 840) return IST_OFFSET_MIN;
  return n;
}

/** The calendar day ("yyyy-MM-dd") on a phone [offsetMin] minutes ahead of UTC at [at]. */
export function localDay(at: Date, offsetMin: number): string {
  return new Date(at.getTime() + offsetMin * 60_000).toISOString().slice(0, 10);
}
