import Link from 'next/link'
import { ArrowRight, ShieldCheck, Timer, Zap } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ROUTES } from '@/lib/navigation'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'
import { HeroLive } from './HeroLive'

const FACTS = [
  { Icon: Zap,         label: 'Auto-bid up to your max' },
  { Icon: Timer,       label: '+5:00 on late bids' },
  { Icon: ShieldCheck, label: 'Deposit refunded when you lose' },
] as const

// Staggered entrance for the copy column (tw-animate-css); disabled for reduced motion.
const RISE = 'animate-in fade-in slide-in-from-bottom-4 fill-mode-both duration-700 motion-reduce:animate-none'

interface HeroSectionProps {
  /** The soonest-ending running auction; the watch counts it down. */
  lot: AuctionBrowseItem | null
}

/**
 * Homepage hero. Always dark (the `dark` class re-scopes the design tokens), full bleed, with a
 * 3D watch whose hands, LED ring and shock rings are driven by a real lot's countdown and bids.
 */
export function HeroSection({ lot }: HeroSectionProps) {
  return (
    <section className="dark relative isolate overflow-hidden bg-[var(--color-brand-900)] text-foreground">
      {/* Oversized dial behind everything: tick ring, rings, and a stepping second hand. */}
      <div className="hero-dial pointer-events-none absolute right-[-14rem] top-1/2 -z-10 size-[56rem] -translate-y-1/2 lg:right-[-9rem]" aria-hidden="true">
        <div className="hero-dial-ring inset-0" />
        <div className="hero-dial-ticks" />
        <div className="hero-dial-ring inset-[7.5rem] opacity-60" />
        <div className="hero-dial-ring inset-[14rem] opacity-40" />
        <div className="hero-dial-hand motion-reduce:animate-none" />
      </div>

      <div className="mx-auto grid w-full max-w-[var(--container-xl)] items-center gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16 lg:py-20">
        <div className="flex flex-col">
          <div
            className={`${RISE} inline-flex w-fit items-center gap-2.5 rounded-full border border-white/20 bg-white/[0.08] py-1.5 pl-3 pr-4 text-sm`}
          >
            <span className="size-2 rounded-full bg-[var(--color-warning-default)] animate-pulse" />
            Live now · every bid lands in real time
          </div>

          <h1
            className={`${RISE} delay-100 mt-6 font-display text-[length:clamp(2.75rem,5.2vw,4.75rem)] font-medium leading-[0.98]`}
          >
            The <span className="text-[var(--color-brand-300)]">clock</span> is
            <br />
            the auctioneer.
          </h1>

          <p className={`${RISE} delay-200 mt-6 max-w-[32.5rem] text-[length:var(--font-size-md)] leading-relaxed text-muted-foreground`}>
            Real-time auctions for watches, instruments and collectibles. Place a bid in the final two minutes and
            the clock adds five, so nobody wins by timing a click.
          </p>

          <div className={`${RISE} delay-300 mt-8 flex flex-wrap gap-3`}>
            <Button
              size="xl"
              className="h-12 bg-white px-6 text-[var(--color-brand-700)] hover:bg-white/90"
              render={<Link href={ROUTES.browse} />}
              nativeButton={false}
            >
              Browse live auctions
              <ArrowRight className="size-4" />
            </Button>
            <Button
              variant="outline"
              size="xl"
              className="h-12 border-white/35 bg-transparent px-6 text-white hover:bg-white/10"
              render={<Link href={ROUTES.sell} />}
              nativeButton={false}
            >
              Start selling
            </Button>
          </div>

          <ul className={`${RISE} delay-500 mt-10 flex flex-wrap gap-x-7 gap-y-3 border-t border-white/15 pt-6`}>
            {FACTS.map(({ Icon, label }) => (
              <li key={label} className="flex items-center gap-2.5 text-sm text-muted-foreground">
                <Icon className="size-[18px] text-[var(--color-warning-default)]" aria-hidden="true" />
                {label}
              </li>
            ))}
          </ul>
        </div>

        <div className={`${RISE} delay-300 relative`}>
          <HeroLive lot={lot} />
        </div>
      </div>
    </section>
  )
}
