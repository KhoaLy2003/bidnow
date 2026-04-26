import type { Metadata } from 'next'
import { Header }      from '@/components/layout/Header'
import { Footer }      from '@/components/layout/Footer'
import { BottomNav }   from '@/components/layout/BottomNav'
import { AuctionGrid } from '@/components/auction/AuctionGrid'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction } from '@/types/auction'

export const metadata: Metadata = { title: 'Browse Auctions' }

import { auctionService } from '@/services/auction.service'

interface BrowsePageProps {
  searchParams: Promise<{ q?: string; category?: string }>
}

export default async function BrowsePage({ searchParams }: BrowsePageProps) {
  const { q, category } = await searchParams
  const auctions = await auctionService.getAuctions({ q, category })

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-grid)] px-4 py-8 pb-14 md:pb-8">
        <div className="mb-6">
          <h1 className="font-display font-bold text-[length:var(--font-size-2xl)]">
            {q ? `Results for "${q}"` : 'All Auctions'}
          </h1>
          <p className="text-sm text-muted-foreground">{auctions.length} listings</p>
        </div>
        <AuctionGrid auctions={auctions} />
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
