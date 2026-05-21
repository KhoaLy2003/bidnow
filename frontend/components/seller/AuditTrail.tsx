import type { AuditEvent } from '@/types/seller'

interface AuditTrailProps {
  events: AuditEvent[]
}

export function AuditTrail({ events }: AuditTrailProps) {
  return (
    <div className="flex flex-col gap-1">
      {events.map((ev, i) => (
        <div key={i} className="flex items-start gap-3 text-sm text-muted-foreground">
          <span className="shrink-0 font-mono text-xs text-[var(--color-text-tertiary)]">
            {ev.timestamp.toLocaleDateString('en-US', {
              month: 'short',
              day:   'numeric',
              hour:  '2-digit',
              minute:'2-digit',
            })}
          </span>
          <span>{ev.message}</span>
        </div>
      ))}
    </div>
  )
}
