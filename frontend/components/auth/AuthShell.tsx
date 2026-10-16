import Link from 'next/link'
import type { ReactNode } from 'react'
import { BidNowMark } from '@/components/shared/BidNowMark'

interface AuthShellProps {
  /** Large headline on the brand panel. */
  headline: ReactNode
  /** Supporting line under the headline (hidden on small screens). */
  description: string
  /** Extra content under the description, e.g. a facts list or steps (hidden on small screens). */
  aside?: ReactNode
  children: ReactNode
}

// Staggered entrance (tw-animate-css); disabled for reduced motion.
const RISE = 'animate-in fade-in slide-in-from-bottom-4 fill-mode-both duration-700 motion-reduce:animate-none'

/**
 * Split-screen frame shared by the auth pages. The left panel is always dark (the `dark` class
 * re-scopes the design tokens) and echoes the homepage hero; the form sits on the right.
 * Below `lg` the panel collapses to a compact header so the form comes first.
 */
export function AuthShell({ headline, description, aside, children }: AuthShellProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <section className="dark relative isolate flex flex-col overflow-hidden bg-[var(--color-brand-900)] px-5 py-6 text-foreground sm:px-8 lg:px-14 lg:py-10">
        <div
          className="pointer-events-none absolute -bottom-64 -right-72 -z-10 hidden size-[50rem] lg:block"
          aria-hidden="true"
        >
          <div className="hero-dial-ring inset-0" />
          <div className="hero-dial-ticks" />
          <div className="hero-dial-ring inset-[7.5rem] opacity-60" />
          <div className="hero-dial-ring inset-[14rem] opacity-40" />
          <div className="hero-dial-hand motion-reduce:animate-none" />
        </div>

        <Link href="/" className="flex w-fit items-center gap-2.5" aria-label="BidNow home">
          <BidNowMark size={32} />
          <span className="font-display text-2xl font-bold leading-none tracking-[-0.06em]">bidnow</span>
        </Link>

        <div className={`${RISE} mt-8 flex max-w-xl flex-col lg:mt-auto lg:pb-12 lg:pt-16`}>
          <div className="hidden w-fit items-center gap-2.5 rounded-full border border-white/20 bg-white/[0.08] py-1.5 pl-3 pr-4 text-sm lg:inline-flex">
            <span className="size-2 animate-pulse rounded-full bg-[var(--color-warning-default)]" />
            Live now · every bid lands in real time
          </div>

          <h1 className="font-display text-[length:clamp(2rem,4.2vw,3.75rem)] font-medium leading-[1.02] lg:mt-6">
            {headline}
          </h1>

          <p className="mt-4 hidden max-w-md text-[length:var(--font-size-md)] leading-relaxed text-muted-foreground lg:block">
            {description}
          </p>

          {aside && <div className="mt-10 hidden border-t border-white/15 pt-6 lg:block">{aside}</div>}
        </div>
      </section>

      <main className="flex items-center justify-center bg-background px-5 py-10 sm:px-8 lg:px-14 lg:py-12">
        <div className="w-full max-w-[25rem]">{children}</div>
      </main>
    </div>
  )
}
