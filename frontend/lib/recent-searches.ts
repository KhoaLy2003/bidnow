export const RECENT_SEARCHES_KEY = 'bidnow:recent-searches'
export const MAX_RECENT_SEARCHES = 5

/** Puts `term` first, drops case-insensitive duplicates and caps the list. */
export function addRecentSearch(list: readonly string[], term: string): string[] {
  const clean = term.trim()
  if (!clean) return [...list]
  const rest = list.filter((t) => t.toLowerCase() !== clean.toLowerCase())
  return [clean, ...rest].slice(0, MAX_RECENT_SEARCHES)
}

export function parseRecentSearches(raw: string | null): string[] {
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((t): t is string => typeof t === 'string' && t.trim() !== '')
      .slice(0, MAX_RECENT_SEARCHES)
  } catch {
    return []
  }
}
