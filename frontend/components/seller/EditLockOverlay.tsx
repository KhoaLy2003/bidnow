import { Lock } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface EditLockOverlayProps {
  readonly reason?: string
}

export function EditLockOverlay({ reason }: EditLockOverlayProps) {
  return (
    <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 rounded-md bg-white/94 p-6 text-center backdrop-blur-sm">
      <div className="flex size-10 items-center justify-center rounded-lg bg-[var(--color-bg-elevated)] text-muted-foreground">
        <Lock className="size-5" />
      </div>
      <div className="flex flex-col gap-1">
        <p className="font-medium text-sm">Editing locked</p>
        <p className="max-w-sm text-sm text-muted-foreground">
          {reason ?? 'You can edit or delete only while status = DRAFT or before start time.'}
        </p>
      </div>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" className="text-[var(--color-danger-text)] border-[var(--color-danger-border)] hover:bg-[var(--color-danger-subtle)]">
          Request to end early
        </Button>
        <Button variant="ghost" size="sm">
          View change log
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Editable only when{' '}
        <code className="font-mono text-xs">status = DRAFT</code> or{' '}
        <code className="font-mono text-xs">startTime &gt; now</code>
      </p>
    </div>
  )
}
