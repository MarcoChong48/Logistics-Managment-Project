// Privacy by omission (Admin plan, adminHidePrivateValue). An Admin never sees a client's address,
// phone number or recipient name. Log values and free notes pass through hidePrivateValue first.

const PRIVATE_KEY = /phone|address|recipient|email|proof/i;

function maskKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(maskKeys);
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [key, inner] of Object.entries(value as Record<string, unknown>)) {
      out[key] = PRIVATE_KEY.test(key) ? "[hidden]" : maskKeys(inner);
    }
    return out;
  }
  return value;
}

/**
 * Rule 1: in JSON text, the value of every private key becomes "[hidden]".
 * Rule 2: in any text, a run of 8 or more digits (one space or hyphen may stand between two digits,
 * a "+" may stand in front) becomes "[hidden number]". Dates such as 2026-11-10 stay as they are.
 */
export function hidePrivateValue(text: string | null): string | null {
  if (text === null) return null;
  let out = text;
  const trimmed = out.trim();
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    try {
      out = JSON.stringify(maskKeys(JSON.parse(trimmed)));
    } catch {
      // Not JSON: rule 2 still applies.
    }
  }
  const dates: string[] = [];
  out = out.replace(/\d{4}-\d{2}-\d{2}/g, (match) => {
    dates.push(match);
    return `\u0000${dates.length - 1}\u0000`;
  });
  out = out.replace(/\+?\d(?:[ -]?\d){7,}/g, "[hidden number]");
  out = out.replace(/\u0000(\d+)\u0000/g, (_, index: string) => dates[Number(index)]);
  return out;
}

/**
 * True when a text still holds one of the private test values that the test tools plant: the three marker
 * texts of the Admin plan and the test phone number 5550 0000 1234 (used by the privacy check tool).
 * Device texts such as "Gecko/20100101" are not private, so a plain run of digits is not enough.
 */
export function looksPrivate(text: string): boolean {
  return /PRIVATE-(RECIPIENT|PHONE|ADDRESS)-MARKER/.test(text) || /5550[ -]?0000[ -]?1234/.test(text);
}
