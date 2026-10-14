/**
 * Money helpers. Amounts are dollars (number) everywhere, matching the API;
 * arithmetic and comparison go through integer cents to avoid float drift.
 */
export function toCents(dollars: number): number {
  return Math.round(dollars * 100)
}

export function fromCents(cents: number): number {
  return cents / 100
}

export function addDollars(a: number, b: number): number {
  return fromCents(toCents(a) + toCents(b))
}

export function isLessThan(a: number, b: number): boolean {
  return toCents(a) < toCents(b)
}

const DOLLAR_INPUT = /^\d+(\.\d{1,2})?$/

/** Parses a positive dollar amount typed by the user; null when invalid. */
export function parseDollarInput(raw: string): number | null {
  const trimmed = raw.trim()
  if (!DOLLAR_INPUT.test(trimmed)) return null
  const value = Number(trimmed)
  return value > 0 ? value : null
}
