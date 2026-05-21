import { Check } from 'lucide-react'
import { cn }    from '@/lib/utils'

interface StepIndicatorProps {
  steps:   string[]
  current: number   // 1-based
}

export function StepIndicator({ steps, current }: StepIndicatorProps) {
  return (
    <div className="flex items-center gap-0 w-full">
      {steps.map((label, i) => {
        const n    = i + 1
        const done = n < current
        const curr = n === current
        return (
          <div key={i} className="flex items-center flex-1 min-w-0 last:flex-none">
            <div className={cn(
              'flex items-center gap-2 shrink-0 text-sm',
              done ? 'text-foreground' : curr ? 'text-foreground font-medium' : 'text-muted-foreground',
            )}>
              <span className={cn(
                'flex size-6 shrink-0 items-center justify-center rounded-full border font-mono text-xs font-medium transition-[background-color,border-color,color] duration-[var(--duration-tesla)]',
                done
                  ? 'border-foreground bg-foreground text-background'
                  : curr
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-border bg-background',
              )}>
                {done ? <Check className="size-3" /> : n}
              </span>
              <span className="hidden sm:inline whitespace-nowrap">{label}</span>
            </div>
            {i < steps.length - 1 && (
              <div className={cn(
                'mx-3 h-px flex-1 transition-colors duration-[var(--duration-tesla)]',
                done ? 'bg-foreground' : 'bg-border',
              )} />
            )}
          </div>
        )
      })}
    </div>
  )
}
