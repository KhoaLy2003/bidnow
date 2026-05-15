'use client'

import { useState, useEffect } from 'react'
import { deriveTimerState, type TimerState } from '@/lib/auction-utils'

export function useCountdown(endsAt: Date): {
  secondsLeft: number
  timerState:  TimerState
  isExpired:   boolean
} {
  const [secondsLeft, setSecondsLeft] = useState(() =>
    Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000))
  )

  useEffect(() => {
    const tick = () =>
      setSecondsLeft(Math.max(0, Math.floor((endsAt.getTime() - Date.now()) / 1000)))

    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [endsAt])

  return {
    secondsLeft,
    timerState: deriveTimerState(endsAt),
    isExpired:  secondsLeft <= 0,
  }
}
