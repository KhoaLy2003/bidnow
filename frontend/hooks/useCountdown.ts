'use client'

import { useState, useEffect } from 'react'
import type { TimerState } from '@/lib/auction-utils'

const WARNING_THRESHOLD_SECONDS  = 5 * 60
const CRITICAL_THRESHOLD_SECONDS = 60

const calc = (endsAt: Date) =>
  Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000))

export function useCountdown(endsAt: Date): {
  secondsLeft: number | null  // null until first client-side tick (avoids SSR/CSR mismatch)
  timerState:  TimerState
  isExpired:   boolean
} {
  // Start as null so server and client render the same initial value
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null)

  useEffect(() => {
    setSecondsLeft(calc(endsAt))
    const id = setInterval(() => {
      const s = calc(endsAt)
      setSecondsLeft(s)
      if (s === 0) clearInterval(id)
    }, 1000)
    return () => clearInterval(id)
  }, [endsAt])

  const timerState: TimerState =
    secondsLeft === null || secondsLeft > WARNING_THRESHOLD_SECONDS  ? 'normal'
    : secondsLeft <= CRITICAL_THRESHOLD_SECONDS                      ? 'critical'
    : 'warning'

  return {
    secondsLeft,
    timerState,
    isExpired: secondsLeft !== null && secondsLeft <= 0,
  }
}
