export const MIN_PASSWORD_LENGTH = 8

export type PasswordScore = 0 | 1 | 2 | 3 | 4

export interface PasswordStrength {
  /** 0 = nothing typed, 1-4 = filled segments of the meter. */
  score: PasswordScore
  /** Short guidance shown under the meter. */
  hint: string
}

const IDLE_HINT = `Use at least ${MIN_PASSWORD_LENGTH} characters. Mix letters, numbers and symbols.`
const LEVEL_HINTS = ['Weak. Add length or mix in numbers.', 'Fair', 'Good', 'Strong'] as const

/**
 * Client-side nudge only: the server enforces the real password rules.
 * A password under the minimum length never scores above 1.
 */
export function scorePassword(password: string): PasswordStrength {
  if (password.length === 0) return { score: 0, hint: IDLE_HINT }

  if (password.length < MIN_PASSWORD_LENGTH) {
    const missing = MIN_PASSWORD_LENGTH - password.length
    return { score: 1, hint: `${missing} more character${missing === 1 ? '' : 's'} needed` }
  }

  let points = 1
  if (password.length >= 12) points++
  if (/[a-z]/.test(password) && /[A-Z]/.test(password) && /\d/.test(password)) points++
  if (/[^A-Za-z0-9]/.test(password)) points++

  const score = Math.min(points, 4) as 1 | 2 | 3 | 4
  return { score, hint: LEVEL_HINTS[score - 1] }
}
