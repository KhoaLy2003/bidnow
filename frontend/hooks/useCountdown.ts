'use client'

import { useState, useEffect } from 'react'
import type { TimerState } from '@/lib/auction-utils'

const WARNING_THRESHOLD_SECONDS  = 5 * 60
const CRITICAL_THRESHOLD_SECONDS = 60

export function useCountdown(endsAt: Date): {
  secondsLeft: number
  timerState:  TimerState
  isExpired:   boolean
} {
  const [secondsLeft, setSecondsLeft] = useState(() =>
    Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000))
  )

  useEffect(() => {
    const id = setInterval(() => {
      const s = Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000))
      setSecondsLeft(s)
      if (s === 0) clearInterval(id)
    }, 1000)
    return () => clearInterval(id)
  }, [endsAt])

  const timerState: TimerState =
    secondsLeft <= 0                          ? 'normal'
    : secondsLeft <= CRITICAL_THRESHOLD_SECONDS ? 'critical'
    : secondsLeft <= WARNING_THRESHOLD_SECONDS  ? 'warning'
    : 'normal'

  return {
    secondsLeft,
    timerState,
    isExpired: secondsLeft <= 0,
  }
}
