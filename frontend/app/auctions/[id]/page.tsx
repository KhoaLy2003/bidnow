import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { BadgeCheck } from 'lucide-react'
import { Header }   from '@/components/layout/Header'
import { Footer }   from '@/components/layout/Footer'
import { BottomNav } from '@/components/layout/BottomNav'
import { AuctionGallery }   from '@/components/auction/AuctionGallery'
import { BidPanel }         from '@/components/auction/BidPanel'
import { ItemSpecs }        from '@/components/auction/ItemSpecs'
import { SellerCard }       from '@/components/auction/SellerCard'
import { AuctionLiveBridge } from '@/components/auction/AuctionLiveBridge'
import { LiveBidHistory }   from '@/components/auction/LiveBidHistory'
import { auctionService }   from '@/services/auction.service'
import { bidService, EMPTY_BID_PAGE, BID_PAGE_SIZE } from '@/services/bid.service'
import { getAuctionStatus } from '@/lib/auction-utils'
import { formatRelativeTime } from '@/lib/format'

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
  const [auction, firstBidPage] = await Promise.all([
    auctionService.getAuctionById(id),
    bidService.getAuctionBids(id, { page: 0, size: BID_PAGE_SIZE }).catch(() => EMPTY_BID_PAGE),
  ])
  if (!auction) notFound()

  const effectiveStatus = getAuctionStatus(auction)
  const displayAuction  = { ...auction, status: effectiveStatus }

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-detail)] px-4 py-5 pb-24 md:px-6 md:pb-14">
        <AuctionLiveBridge auction={auction} initialBids={firstBidPage.items} />

        {/* Breadcrumb */}
        <nav className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground mb-5" aria-label="Breadcrumb">
          <Link href="/auctions" className="hover:text-foreground">Browse</Link>
          <span aria-hidden>›</span>
          <Link href={`/auctions?category=${auction.categoryId}`} className="hover:text-foreground">
            {auction.categoryName}
          </Link>
          <span aria-hidden>›</span>
          <span className="text-foreground truncate max-w-[240px]">{auction.title}</span>
        </nav>

        {/* Photo on the left, title and bidding on the right */}
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,460px)] lg:items-start lg:gap-14">
          <AuctionGallery images={auction.images} title={auction.title} />

          <div className="flex min-w-0 flex-col">
            <p className="mb-2.5 text-[11px] font-mono uppercase tracking-wide text-muted-foreground">
              {auction.categoryName} · Listed {formatRelativeTime(auction.startsAt)}
            </p>
            <h1 className="font-display font-semibold text-[length:var(--font-size-2xl)] md:text-[2rem] leading-[1.15] tracking-tight">
              {auction.title}
            </h1>

            {auction.seller ? (
              <p className="mt-3.5 mb-5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-muted-foreground">
                <span>
                  Sold by{' '}
                  <a href="#seller" className="font-semibold text-foreground hover:underline">
                    {auction.seller.name}
                  </a>
                </span>
                <span className="flex items-center gap-1 font-medium text-[var(--color-success-text)]">
                  <BadgeCheck className="size-3.5" aria-hidden />
                  Verified Seller
                </span>
              </p>
            ) : (
              <div className="h-5" />
            )}

            <BidPanel auction={auction} />
          </div>
        </div>

        {/* Details on the left, bid history on the right */}
        <div className="mt-14 grid gap-12 border-t pt-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,460px)] lg:items-start lg:gap-14">
          <div className="flex flex-col gap-12">
            <section id="details">
              <h2 className="font-display text-2xl font-semibold tracking-tight mb-3.5">About this item</h2>
              <p className="max-w-prose whitespace-pre-line text-[15px] leading-[1.7] text-muted-foreground">
                {auction.description}
              </p>
            </section>

            <ItemSpecs auction={displayAuction} categoryLabel={auction.categoryName} />
            <SellerCard seller={auction.seller} />
          </div>

          <LiveBidHistory auction={auction} initialBids={firstBidPage.items} initialHasMore={firstBidPage.hasNext} />
        </div>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
