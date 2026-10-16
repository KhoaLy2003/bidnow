import { scorePassword } from '@/lib/password-strength'
import { cn } from '@/lib/utils'

const SEGMENT_COLORS = [
  'bg-[var(--color-danger-default)]',
  'bg-[var(--color-warning-default)]',
  'bg-[var(--color-border-focus)]',
  'bg-[var(--color-success-default)]',
] as const

interface PasswordStrengthMeterProps {
  password: string
  id: string
}

/** Four-segment meter plus a hint line; `id` is the hint's id so the input can reference it via aria-describedby. */
export function PasswordStrengthMeter({ password, id }: PasswordStrengthMeterProps) {
  const { score, hint } = scorePassword(password)
  const fill = score > 0 ? SEGMENT_COLORS[score - 1] : ''

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-1" aria-hidden="true">
        {[1, 2, 3, 4].map((n) => (
          <span
            key={n}
            className={cn('h-1 flex-1 rounded-full bg-[var(--color-neutral-200)] transition-colors duration-300', n <= score && fill)}
          />
        ))}
      </div>
      <p id={id} className="text-[0.8125rem] text-muted-foreground" aria-live="polite">
        {hint}
      </p>
    </div>
  )
}
