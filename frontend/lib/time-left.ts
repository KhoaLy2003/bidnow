/** Compact countdown for tight spaces: "02:14" under an hour, "1h 12m" above, "Ended" at zero. */
export function formatTimeLeft(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  if (seconds === 0) return 'Ended'

  if (seconds >= 3600) {
    const hours   = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    return `${hours}h ${String(minutes).padStart(2, '0')}m`
  }

  const minutes = Math.floor(seconds / 60)
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
