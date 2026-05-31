import type { Metadata } from 'next'
import { Header }         from '@/components/layout/Header'
import { Footer }         from '@/components/layout/Footer'
import { BottomNav }      from '@/components/layout/BottomNav'
import { BrowseClient }   from '@/components/auction/browse'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = { title: 'Browse Auctions' }

interface BrowsePageProps {
  searchParams: Promise<{
    q?:          string
    category?:   string
    minPrice?:   string
    maxPrice?:   string
    endingSoon?: string
    buyNow?:     string
    sort?:       string
    page?:       string
  }>
}

export default async function BrowsePage({ searchParams }: BrowsePageProps) {
  const params      = await searchParams
  const currentPage = Math.max(0, parseInt(params.page ?? '0', 10) || 0)

  const [{ items, total, totalPages }, categoryCounts] = await Promise.all([
    auctionService.getBrowseAuctions({
      keyword:         params.q,
      categorySlug:    params.category && params.category !== 'all' ? params.category : undefined,
      minPrice:        params.minPrice  ? Number(params.minPrice)  : undefined,
      maxPrice:        params.maxPrice  ? Number(params.maxPrice)  : undefined,
      endingSoon:      params.endingSoon === 'true' || undefined,
      buyNowAvailable: params.buyNow    === 'true' || undefined,
      sortBy:          params.sort,
      page:            currentPage,
      size:            20,
    }),
    auctionService.getCategoryCounts(),
  ])

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-grid)] px-4 py-8 pb-14 md:pb-8">
        <BrowseClient
          key={JSON.stringify(params)}
          items={items}
          categoryCounts={categoryCounts}
          total={total}
          totalPages={totalPages}
          currentPage={currentPage}
          searchParams={params as Record<string, string | undefined>}
        />
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
