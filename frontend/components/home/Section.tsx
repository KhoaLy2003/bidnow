import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface SectionProps {
  id?:       string
  /** Full-bleed tinted band with hairlines above and below. */
  tinted?:   boolean
  className?: string
  children:  ReactNode
}

/** A full-width homepage band with the shared container and vertical rhythm. */
export function Section({ id, tinted = false, className, children }: SectionProps) {
  return (
    <section
      id={id}
      className={cn(
        'scroll-mt-20 py-16 md:py-24',
        tinted && 'border-y border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]',
        className,
      )}
    >
      <div className="mx-auto w-full max-w-[var(--container-xl)] px-4 sm:px-6">{children}</div>
    </section>
  )
}

interface SectionHeadingProps {
  eyebrow:      string
  title:        string
  description?: string
  /** Right-aligned control, e.g. a "see all" link. */
  action?:      ReactNode
  className?:   string
}

/** Mono eyebrow with an amber dot, a display title and an optional lead-in. */
export function SectionHeading({ eyebrow, title, description, action, className }: SectionHeadingProps) {
  return (
    <div className={cn('mb-8 flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between md:mb-10', className)}>
      <div>
        <p className="flex items-center gap-2.5 font-mono text-[length:var(--font-size-xs)] font-medium uppercase text-[var(--color-text-brand)]">
          <span className="size-2 rounded-full bg-[var(--color-warning-default)]" aria-hidden="true" />
          {eyebrow}
        </p>
        <h2 className="mt-3.5 max-w-3xl font-display text-[length:clamp(1.875rem,3.4vw,2.75rem)] font-medium leading-[1.08]">
          {title}
        </h2>
        {description && (
          <p className="mt-3.5 max-w-xl text-[length:var(--font-size-md)] leading-relaxed text-muted-foreground">
            {description}
          </p>
        )}
      </div>
      {action}
    </div>
  )
}
