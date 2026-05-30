import type { Metadata } from 'next'
import { Header }         from '@/components/layout/Header'
import { Footer }         from '@/components/layout/Footer'
import { BottomNav }      from '@/components/layout/BottomNav'
import { BrowseClient }   from '@/components/auction/browse'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = { title: 'Browse Auctions' }

interface BrowsePageProps {
  searchParams: Promise<{ q?: string }>
}

export default async function BrowsePage({ searchParams }: BrowsePageProps) {
  const { q } = await searchParams
  const items = await auctionService.getBrowseAuctions({ q })

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-grid)] px-4 py-8 pb-14 md:pb-8">
        <BrowseClient items={items} searchQuery={q} />
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
