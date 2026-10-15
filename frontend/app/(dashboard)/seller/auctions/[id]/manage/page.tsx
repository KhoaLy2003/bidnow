'use client'

import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DeleteAuctionDialog } from '@/components/seller/DeleteAuctionDialog'
import { DraftView } from '@/components/seller/manage/DraftView'
import { LiveAuctionContainer } from '@/components/seller/manage/LiveAuctionContainer'
import { StaticEndedView } from '@/components/seller/manage/StaticEndedView'
import { AuctionStatus } from '@/lib/design-tokens'
import { getManageMode } from '@/lib/seller-manage'
import { getErrorMessage } from '@/lib/utils'
import { auctionService } from '@/services/auction.service'
import { bidService, EMPTY_BID_PAGE, type BidPage } from '@/services/bid.service'
import type { AuctionDetail } from '@/types/ui/auction.ui'

function CenteredMessage({ children }: { children: React.ReactNode }) {
  return <div className="flex flex-col items-center justify-center gap-4 py-32 text-center">{children}</div>
}

function BackToAuctions() {
  return (
    <Button variant="outline" size="sm" render={<Link href="/seller/auctions" />} nativeButton={false}>
      ← Back to my auctions
    </Button>
  )
}

export default function ManageAuctionPage() {
  const { id } = useParams<{ id: string }>()

  const [auction, setAuction] = useState<AuctionDetail | null>(null)
  const [bidPage, setBidPage] = useState<BidPage>(EMPTY_BID_PAGE)
  /** Bumped on every successful load, so views that seed local state from props remount with the fresh data. */
  const [loadCount, setLoadCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleted, setDeleted] = useState(false)
  const [published, setPublished] = useState(false)

  const loadAuction = useCallback((): Promise<void> => {
    return auctionService.getAuctionById(id)
      .then(async (detail) => {
        if (!detail) {
          setFetchError('Auction not found.')
          return
        }
        // Drafts have no bids; everything else shows the bid history, so fetch its first page with the auction.
        const bids = getManageMode(detail.status, detail.winnerId) === 'draft'
          ? EMPTY_BID_PAGE
          : await bidService.getAuctionBids(id).catch(() => EMPTY_BID_PAGE)
        setAuction(detail)
        setBidPage(bids)
        setLoadCount((n) => n + 1)
        setFetchError(null)
      })
      .catch(() => setFetchError('Failed to load auction.'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => { void loadAuction() }, [loadAuction])

  const reload = useCallback(() => {
    setLoading(true)
    return loadAuction()
  }, [loadAuction])

  // The live container reloads without blanking the page: keeping it mounted keeps the socket subscribed.
  const refresh = useCallback(() => loadAuction(), [loadAuction])

  async function handlePublished() {
    await reload()
    setPublished(true)
  }

  async function handleDelete() {
    if (!auction) return
    try {
      await auctionService.deleteAuction(auction.id)
      setDeleteOpen(false)
      setDeleted(true)
    } catch (error) {
      toast.error(getErrorMessage(error, 'Failed to delete auction. Please try again.'))
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-32 text-muted-foreground" role="status">
        <Loader2 className="size-4 animate-spin" aria-hidden />
        <span className="text-sm">Loading auction…</span>
      </div>
    )
  }

  if (fetchError || !auction) {
    return (
      <CenteredMessage>
        <p className="text-sm text-muted-foreground">{fetchError ?? 'Auction not found.'}</p>
        <BackToAuctions />
      </CenteredMessage>
    )
  }

  if (deleted) {
    return (
      <CenteredMessage>
        <p className="text-sm font-medium">Auction deleted.</p>
        <BackToAuctions />
      </CenteredMessage>
    )
  }

  if (published) {
    return (
      <CenteredMessage>
        <div className="flex size-12 items-center justify-center rounded-full bg-[var(--color-success-subtle)]">
          <CheckCircle2 className="size-6 text-[var(--color-success-text)]" aria-hidden />
        </div>
        <div className="flex flex-col gap-1">
          <p className="font-medium">Auction published</p>
          <p className="text-sm text-muted-foreground">
            {auction.status === AuctionStatus.Scheduled
              ? `Scheduled to go live on ${auction.startsAt.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}.`
              : 'Your auction is now live.'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" render={<Link href={`/auctions/${auction.id}`} />} nativeButton={false}>
            View auction
          </Button>
          <Button variant="outline" size="sm" onClick={() => setPublished(false)}>
            Manage auction
          </Button>
          <BackToAuctions />
        </div>
      </CenteredMessage>
    )
  }

  const mode = getManageMode(auction.status, auction.winnerId)

  return (
    <>
      {mode === 'draft' && (
        <DraftView
          auction={auction}
          onSaved={() => void reload()}
          onPublished={() => void handlePublished()}
          onDelete={() => setDeleteOpen(true)}
        />
      )}

      {mode === 'live' && (
        <LiveAuctionContainer
          key={auction.id}
          base={auction}
          initialBids={bidPage.items}
          initialHasMore={bidPage.hasNext}
          onChanged={refresh}
        />
      )}

      {(mode === 'sold' || mode === 'ended') && (
        <StaticEndedView
          key={`${auction.id}:${loadCount}`}
          auction={auction}
          initialBids={bidPage.items}
          initialHasMore={bidPage.hasNext}
        />
      )}

      <DeleteAuctionDialog
        open={deleteOpen}
        auctionTitle={auction.title}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void handleDelete()}
      />
    </>
  )
}
