'use client'

import { Zap } from 'lucide-react'
import { useCountdown } from '@/hooks/useCountdown'
import { formatCountdown } from '@/lib/format'
import { cn } from '@/lib/utils'

interface CountdownTimerProps {
  endsAt:     Date
  size?:      'sm' | 'md' | 'lg'
  className?: string
}

const SIZE_CLASSES = {
  sm: 'text-[length:var(--font-size-timer-sm)]',
  md: 'text-[length:var(--font-size-timer-md)]',
  lg: 'text-[length:var(--font-size-timer-lg)]',
} as const

const STATE_CLASSES = {
  normal:   'font-mono font-bold text-muted-foreground',
  warning:  'font-mono font-bold text-[var(--color-warning-text)] bg-[var(--color-auction-ending-bg)] rounded-full px-2 py-0.5',
  critical: 'font-mono font-extrabold text-[var(--color-danger-text)] bg-[var(--color-auction-critical-bg)] border-2 border-[var(--color-auction-critical-border)] rounded-full px-2 py-0.5',
} as const

export function CountdownTimer({ endsAt, size = 'md', className }: CountdownTimerProps) {
  const { secondsLeft, timerState, isExpired } = useCountdown(endsAt)

  if (isExpired) return null

  return (
    <div className={cn('inline-flex flex-col items-center gap-0.5', className)}>
      {/* key forces animation replay on each tick */}
      <span
        key={secondsLeft}
        style={{ animation: 'countdown-tick 150ms ease-in-out' }}
        className={cn(SIZE_CLASSES[size], STATE_CLASSES[timerState])}
      >
        {formatCountdown(secondsLeft)}
      </span>
      {timerState === 'critical' && (
        <span className="flex items-center gap-0.5 text-[length:var(--font-size-2xs)] text-[var(--color-danger-text)]">
          <Zap className="size-2.5" />
          Time extends if you bid
        </span>
      )}
    </div>
  )
}
