import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { Header }   from '@/components/layout/Header'
import { Footer }   from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { Separator } from '@/components/ui/separator'
import { StatusBadge }      from '@/components/auction/StatusBadge'
import { AuctionGallery }   from '@/components/auction/AuctionGallery'
import { BidPanel }         from '@/components/auction/BidPanel'
import { ItemSpecs }        from '@/components/auction/ItemSpecs'
import { SellerCard }       from '@/components/auction/SellerCard'
import { BidHistory }       from '@/components/auction/BidHistory'
import { auctionService }   from '@/services/auction.service'
import { getAuctionStatus } from '@/lib/auction-utils'
import { formatRelativeTime } from '@/lib/format'

const CATEGORY_LABELS: Record<string, string> = {
  watches:  'Watches',
  music:    'Music',
  books:    'Books & Literature',
  art:      'Fine Art',
  sneakers: 'Sneakers',
  cameras:  'Cameras',
}

interface AuctionDetailPageProps {
  params: Promise<{ id: string }>
}

export async function generateMetadata({ params }: AuctionDetailPageProps): Promise<Metadata> {
  const { id } = await params
  const auction = await auctionService.getAuctionById(id)
  return { title: auction?.title ?? 'Auction' }
}

export default async function AuctionDetailPage({ params }: AuctionDetailPageProps) {
  const { id } = await params
  const [auction, bids] = await Promise.all([
    auctionService.getAuctionById(id),
    auctionService.getBidHistory(id),
  ])
  if (!auction) notFound()

  const effectiveStatus = getAuctionStatus(auction)
  const categoryLabel   = CATEGORY_LABELS[auction.categoryId] ?? auction.categoryId

  // Pass effective status to BidPanel so it picks the right sub-panel
  const auctionWithStatus = { ...auction, status: effectiveStatus }

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-detail)] px-4 py-8 pb-14 md:pb-8">

        {/* Breadcrumb */}
        <nav className="flex items-center gap-1.5 text-xs text-muted-foreground mb-6" aria-label="Breadcrumb">
          <span>Browse</span>
          <span>›</span>
          <span>{categoryLabel}</span>
          <span>›</span>
          <span className="text-foreground truncate max-w-[200px]">{auction.title}</span>
        </nav>

        {/* Title row */}
        <div className="flex items-start justify-between gap-4 mb-2">
          <h1 className="font-display font-medium text-[length:var(--font-size-2xl)] md:text-[2.25rem] leading-tight tracking-tight">
            {auction.title}
          </h1>
          <StatusBadge status={effectiveStatus} className="shrink-0 mt-1" />
        </div>

        {/* Meta row */}
        <p className="text-[11px] font-mono uppercase tracking-wide text-muted-foreground mb-6">
          {categoryLabel} · #{auction.id} · Listed {formatRelativeTime(auction.startsAt)}
        </p>

        {/* Gallery — full width */}
        <div className="mb-8">
          <AuctionGallery images={auction.imageUrls} title={auction.title} />
        </div>

        {/* Two-column body */}
        <div className="flex flex-col gap-6 md:grid md:grid-cols-[1fr_360px] md:gap-8 md:items-start mb-8">

          {/* Left — details (order-2 on mobile so BidPanel shows first) */}
          <div className="order-2 md:order-1 flex flex-col gap-8">
            {/* Description */}
            <section>
              <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
                About this item
              </p>
              <p className="text-sm leading-relaxed text-muted-foreground max-w-prose">
                {auction.description}
              </p>
            </section>

            <ItemSpecs auction={auction} categoryLabel={categoryLabel} />
            <SellerCard seller={auction.seller} />
          </div>

          {/* Right — bid panel (order-1 on mobile = appears before details) */}
          <div className="order-1 md:order-2 md:sticky md:top-20 md:self-start">
            <BidPanel auction={auctionWithStatus} />
          </div>
        </div>

        <Separator className="mb-8" />

        {/* Bid history — full width */}
        <section>
          <h2 className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-4">
            Bid History · {auction.totalBids}
          </h2>
          <BidHistory items={bids} />
        </section>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
