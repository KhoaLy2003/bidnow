import type { Metadata } from 'next'
import Image from 'next/image'
import { notFound } from 'next/navigation'
import { Header }              from '@/components/layout/Header'
import { Footer }              from '@/components/layout/Footer'
import { BottomNav }           from '@/components/layout/BottomNav'
import { StatusBadge }         from '@/components/auction/StatusBadge'
import { CountdownTimer }      from '@/components/auction/CountdownTimer'
import { CurrentBidDisplay }   from '@/components/auction/CurrentBidDisplay'
import { BidForm }             from '@/components/auction/BidForm'
import { BidHistory }          from '@/components/auction/BidHistory'
import { AuctionStatus } from '@/lib/design-tokens'
import type { Auction, BidHistoryItem } from '@/types/auction'

import { auctionService } from '@/services/auction.service'

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
  const auction = await auctionService.getAuctionById(id)
  if (!auction) notFound()

  const status = auction.status
  const bids = await auctionService.getBidHistory(id)

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-auction-detail)] px-4 py-8 pb-14 md:pb-8">
        {/* Tablet+: 60/40 grid */}
        <div className="grid grid-cols-1 gap-8 md:grid-cols-[60%_1fr]">
          {/* Left — image + details */}
          <div className="flex flex-col gap-6">
            {/* Image placeholder */}
            <div className="relative aspect-[4/3] w-full overflow-hidden rounded-xl bg-muted flex items-center justify-center">
              <Image 
                src={auction.imageUrls[0] ?? '/placeholder-auction.png'} 
                alt={auction.title} 
                fill 
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 60vw"
              />
            </div>

            <div className="flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <h1 className="font-display font-medium text-[length:var(--font-size-2xl)] leading-snug">
                  {auction.title}
                </h1>
                <StatusBadge status={status} className="shrink-0 mt-1" />
              </div>
              <p className="text-muted-foreground text-sm leading-relaxed">
                {auction.description}
              </p>
            </div>

            {/* Bid history */}
            <div className="flex flex-col gap-3">
              <h2 className="font-medium text-sm uppercase text-muted-foreground">
                Bid History ({auction.totalBids})
              </h2>
              <BidHistory items={bids} />
            </div>
          </div>

          {/* Right — sticky bid panel */}
          <div className="md:sticky md:top-[80px] md:self-start flex flex-col gap-4 rounded-xl border bg-card p-5">
            <CurrentBidDisplay
              amount={auction.currentBid}
              size="md"
              isCurrentUserWinning={false}
            />

            <CountdownTimer endsAt={auction.endsAt} size="md" />

            <BidForm
              auctionId={auction.id}
              currentBid={auction.currentBid}
              minIncrement={1_00}
            />

            <p className="text-xs text-muted-foreground text-center">
              {auction.watchers} people watching
            </p>
          </div>
        </div>
      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
