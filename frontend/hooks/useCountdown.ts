'use client'

import { useCallback, useSyncExternalStore } from 'react'
import type { TimerState } from '@/lib/auction-utils'

const WARNING_THRESHOLD_SECONDS  = 5 * 60
const CRITICAL_THRESHOLD_SECONDS = 60

const calc = (endsAt: Date) =>
  Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000))

// null until first client-side snapshot (avoids SSR/CSR mismatch)
const getServerSnapshot = () => null

export function useCountdown(endsAt: Date): {
  secondsLeft: number | null
  timerState:  TimerState
  isExpired:   boolean
} {
  const subscribe = useCallback((onChange: () => void) => {
    const id = setInterval(() => {
      onChange()
      if (calc(endsAt) <= 0) clearInterval(id)
    }, 1000)
    return () => clearInterval(id)
  }, [endsAt])

  const getSnapshot = useCallback(() => calc(endsAt), [endsAt])

  const secondsLeft = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

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
