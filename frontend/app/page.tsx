import type { Metadata } from 'next'
import Link from 'next/link'
import { Flame, Star, ArrowRight } from 'lucide-react'
import { Header }      from '@/components/layout/Header'
import { Footer }      from '@/components/layout/Footer'
import { BottomNav }   from '@/components/layout/BottomNav'
import { AuctionGrid } from '@/components/auction/AuctionGrid'
import { Button }      from '@/components/ui/button'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction }  from '@/types/auction'

export const metadata: Metadata = {
  title: 'BidNow — Bid. Win. Repeat.',
  description: 'Live auctions for rare collectibles, luxury watches, vintage instruments, and more.',
}

import { auctionService } from '@/services/auction.service'

export default async function HomePage() {
  const allAuctions = await auctionService.getAuctions()
  const featuredPicks = await auctionService.getAuctions({ featured: true })
  const hotAuctions = allAuctions.filter(
    (a) => a.status === AuctionStatus.Critical || a.status === AuctionStatus.EndingSoon
  )
  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-xl)] px-4 pb-14 md:pb-8">
        {/* Hero */}
        <section className="relative overflow-hidden rounded-2xl mt-8 mb-12 bg-gradient-to-br from-[var(--color-brand-600)] to-[var(--color-brand-900)] px-8 py-16 text-white sm:px-16">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--color-brand-400)_0%,_transparent_60%)] opacity-40" />
          <div className="relative flex flex-col gap-6 max-w-xl">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-sm font-medium backdrop-blur-sm">
              <span className="size-2 rounded-full bg-[var(--color-auction-active-accent)] animate-pulse" />
              Live auctions happening now
            </div>
            <h1 className="font-display font-bold text-[length:var(--font-size-5xl)] leading-none tracking-tight">
              Bid. Win.<br />Repeat.
            </h1>
            <p className="text-base text-white/80 max-w-sm">
              Rare collectibles, luxury watches, vintage instruments — all in one place. Real-time bidding, zero compromise.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button variant="brand" className="h-11 bg-white text-[var(--color-brand-700)] hover:bg-white/90" render={<Link href="/auctions" />} nativeButton={false}>
                Browse Auctions
                <ArrowRight className="size-4" />
              </Button>
              <Button variant="outline" className="h-11 border-white/30 bg-transparent text-white hover:bg-white/10" render={<Link href="/auctions/new" />} nativeButton={false}>
                Start Selling
              </Button>
            </div>
          </div>
        </section>

        {/* Hot auctions */}
        {hotAuctions.length > 0 && (
          <section className="flex flex-col gap-4 mb-12">
            <div className="flex items-center justify-between">
              <h2 className="font-display font-bold text-[length:var(--font-size-xl)] flex items-center gap-2">
                <Flame className="size-5 text-[var(--color-danger-default)]" />
                Ending Soon
              </h2>
              <Link href="/auctions?sort=ending" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
                View all <ArrowRight className="size-3.5" />
              </Link>
            </div>
            <AuctionGrid auctions={hotAuctions} />
          </section>
        )}

        {/* Featured picks */}
        <section className="flex flex-col gap-4 mb-12">
          <div className="flex items-center justify-between">
            <h2 className="font-display font-bold text-[length:var(--font-size-xl)] flex items-center gap-2">
              <Star className="size-5 text-[var(--color-warning-text)]" />
              Featured Picks
            </h2>
            <Link href="/auctions?featured=true" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
              View all <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <AuctionGrid auctions={featuredPicks} />
        </section>

        {/* All active */}
        <section className="flex flex-col gap-4 mb-12">
          <div className="flex items-center justify-between">
            <h2 className="font-display font-bold text-[length:var(--font-size-xl)]">All Active Auctions</h2>
            <Link href="/auctions" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
              View all <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <AuctionGrid auctions={allAuctions} />
        </section>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
