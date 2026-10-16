'use client'

import Link from 'next/link'
import { useAuthStore } from '@/store/authStore'
import { ROUTES } from '@/lib/navigation'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Section } from './Section'

/** Closing call to action: the tagline, then one card to bid and one to sell. Signed-in users only see the seller card. */
export function CTASection() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  return (
    <Section id="sell" tinted>
      <p className="font-display text-[length:clamp(3rem,7vw,6.5rem)] font-medium leading-[0.95]">
        Bid. Win.
        <br />
        <span className="text-[var(--color-text-brand)]">Repeat.</span>
      </p>

      <div className={cn('mt-10 grid gap-4 md:mt-12', !isAuthenticated && 'md:grid-cols-2')}>
        {!isAuthenticated && (
          <div className="dark flex min-h-64 flex-col justify-between gap-6 rounded-2xl bg-[var(--color-brand-900)] p-6 text-foreground md:p-9">
            <div>
              <h2 className="font-display text-[length:var(--font-size-2xl)] font-medium leading-tight">Ready to bid?</h2>
              <p className="mt-2.5 max-w-md leading-relaxed text-muted-foreground">
                Create an account, add funds to your wallet and join any live auction. Your deposit is only locked on your
                first bid.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button
                size="xl"
                className="h-12 bg-white px-6 text-[var(--color-brand-700)] hover:bg-white/90"
                render={<Link href={ROUTES.register} />}
                nativeButton={false}
              >
                Join BidNow
              </Button>
              <Button
                variant="outline"
                size="xl"
                className="h-12 border-white/35 bg-transparent px-6 text-white hover:bg-white/10"
                render={<Link href={ROUTES.browse} />}
                nativeButton={false}
              >
                Browse first
              </Button>
            </div>
          </div>
        )}

        <div className="flex min-h-64 flex-col justify-between gap-6 rounded-2xl border border-[var(--color-border-default)] bg-card p-6 md:p-9">
          <div>
            <h2 className="font-display text-[length:var(--font-size-2xl)] font-medium leading-tight">
              Got something worth bidding on?
            </h2>
            <p className="mt-2.5 max-w-md leading-relaxed text-muted-foreground">
              List it, set the starting price and watch real bidders compete for it live. Every seller is reviewed before
              listing.
            </p>
          </div>
          <div>
            <Button size="xl" variant="brand" className="h-12 px-6" render={<Link href={ROUTES.sell} />} nativeButton={false}>
              Start selling
            </Button>
          </div>
        </div>
      </div>
    </Section>
  )
}
