'use client'

import { Zap } from 'lucide-react'
import { useCountdown } from '@/hooks/useCountdown'
import { auctionProgress, formatEndsAt, splitCountdown } from '@/lib/auction-display'
import { cn } from '@/lib/utils'
import type { TimerState } from '@/lib/auction-utils'

interface BidCountdownProps {
  startsAt: Date
  endsAt:   Date
}

const TILE_STATE: Record<TimerState, string> = {
  normal:   'bg-muted',
  warning:  'bg-[var(--color-auction-ending-bg)]',
  critical: 'bg-[var(--color-auction-critical-bg)]',
}

const NUMBER_STATE: Record<TimerState, string> = {
  normal:   'text-foreground',
  warning:  'text-[var(--color-warning-text)]',
  critical: 'text-[var(--color-danger-text)]',
}

const BAR_STATE: Record<TimerState, string> = {
  normal:   'bg-[var(--color-text-brand)]',
  warning:  'bg-[var(--color-auction-ending-accent)]',
  critical: 'bg-[var(--color-auction-critical-accent)]',
}

/** Time-remaining tile for the live bid panel: units, end time and an elapsed-time bar. */
export function BidCountdown({ startsAt, endsAt }: BidCountdownProps) {
  const { secondsLeft, timerState } = useCountdown(endsAt)

  // `secondsLeft` is null until the first client tick, so anything that depends on the current time or the
  // viewer's time zone waits for it. "Now" is read back from the countdown, which keeps render pure.
  const now = secondsLeft === null ? null : endsAt.getTime() - secondsLeft * 1000

  const units    = splitCountdown(secondsLeft ?? 0)
  const progress = now === null ? 0 : auctionProgress(startsAt, endsAt, now)

  return (
    <div className={cn('rounded-[14px] px-4 py-3.5 transition-colors duration-[var(--duration-tesla)]', TILE_STATE[timerState])}>
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span className="font-medium">Time remaining</span>
        <span>{now === null ? '' : formatEndsAt(endsAt, now)}</span>
      </div>

      <div
        role="timer"
        aria-label="Time remaining"
        className={cn('mt-2 flex items-baseline gap-2 font-mono', NUMBER_STATE[timerState])}
      >
        {units.map(({ value, unit }) => (
          <span key={unit} className="flex items-baseline gap-1">
            <span
              className={cn(
                'text-[length:var(--font-size-timer-sm)] font-semibold tracking-tight',
                // Same dimensions before the first client tick, so nothing jumps.
                secondsLeft === null && 'opacity-0',
              )}
            >
              {value}
            </span>
            <span className="mr-2 text-xs font-normal text-muted-foreground">{unit}</span>
          </span>
        ))}
      </div>

      {timerState === 'critical' && (
        <p className="mt-1.5 flex items-center gap-1 text-[length:var(--font-size-2xs)] text-[var(--color-danger-text)]">
          <Zap className="size-2.5" aria-hidden />
          Time extends if you bid
        </p>
      )}

      <div aria-hidden className="mt-3 h-1 overflow-hidden rounded-full bg-border">
        <div
          className={cn('h-full rounded-full transition-[width] duration-[var(--duration-tesla)]', BAR_STATE[timerState])}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  )
}
