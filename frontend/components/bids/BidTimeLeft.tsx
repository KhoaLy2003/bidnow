'use client'

import { useCountdown } from '@/hooks/useCountdown'
import { formatCountdown } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BidTimeLeftProps {
  endsAt:     Date
  className?: string
}

const STATE_CLASSES = {
  normal:   'text-foreground',
  warning:  'text-[var(--color-warning-text)] font-medium',
  critical: 'text-[var(--color-danger-text)] font-medium',
} as const

/** Row countdown: amber under 5 minutes, red under 1 minute. Plain text, unlike the pill-shaped CountdownTimer. */
export function BidTimeLeft({ endsAt, className }: BidTimeLeftProps) {
  const { secondsLeft, timerState, isExpired } = useCountdown(endsAt)

  return (
    <span className={cn('font-mono tabular-nums', STATE_CLASSES[timerState], className)}>
      {secondsLeft === null ? ' ' : isExpired ? 'Ended' : formatCountdown(secondsLeft)}
    </span>
  )
}
