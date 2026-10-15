import { Lock } from 'lucide-react'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { resolveImageUrl } from '@/lib/image-utils'
import { cn } from '@/lib/utils'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const WHEN: Intl.DateTimeFormatOptions = {
  month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
}

function Row({ label, children, mono }: { label: string; children: React.ReactNode; mono?: boolean }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn('min-w-0 break-words', mono && 'font-mono')}>{children}</dd>
    </>
  )
}

interface AuctionFactsProps {
  auction: AuctionDetail
  /** Live auctions are locked; ended ones are simply read-only. */
  locked:  boolean
}

export function AuctionFacts({ auction, locked }: AuctionFactsProps) {
  const depositPct = auction.startingPrice > 0
    ? Math.round((auction.depositAmount / auction.startingPrice) * 100)
    : null

  return (
    <section aria-labelledby="facts-heading" className="overflow-hidden rounded-xl border border-[var(--color-border-default)]">
      <div className="flex items-center justify-between border-b border-[var(--color-border-default)] px-5 py-3.5">
        <h2 id="facts-heading" className="text-sm font-medium">Auction details</h2>
        {locked ? (
          <Badge variant="closed" className="gap-1"><Lock className="size-3" aria-hidden />Locked</Badge>
        ) : (
          <span className="text-xs text-muted-foreground">Read-only</span>
        )}
      </div>

      <dl className="grid grid-cols-[120px_minmax(0,1fr)] gap-x-3 gap-y-2.5 px-5 pb-4 pt-3 text-[13px]">
        <Row label="Starting price" mono>{formatCurrency(auction.startingPrice)}</Row>
        <Row label="Bid increment" mono>{formatCurrency(auction.bidIncrement)}</Row>
        {auction.buyNowPrice !== undefined && <Row label="Buy It Now" mono>{formatCurrency(auction.buyNowPrice)}</Row>}
        <Row label="Deposit">
          <span className="font-mono">{formatCurrency(auction.depositAmount)}</span>
          {depositPct !== null && <span className="text-muted-foreground"> ({depositPct}% of starting price)</span>}
        </Row>
        <Row label="Starts" mono>{auction.startsAt.toLocaleString('en-US', WHEN)}</Row>
        <Row label="Ends" mono>
          {auction.endsAt.toLocaleString('en-US', WHEN)}
          {auction.extensionCount > 0 && (
            <span className="block font-sans text-xs text-muted-foreground">
              Originally {auction.originalEndAt.toLocaleString('en-US', WHEN)}
            </span>
          )}
        </Row>
        <Row label="Anti-snipe">
          Extends 5 min on a bid in the final 5 min · {auction.extensionCount}{' '}
          {auction.extensionCount === 1 ? 'extension' : 'extensions'} so far
        </Row>
        <Row label="Category">{auction.categoryName}</Row>
      </dl>

      <div className="border-t border-[var(--color-border-default)] px-5 pb-4 pt-3.5">
        <p className="text-xs uppercase text-muted-foreground">Description</p>
        <p className="mt-1.5 whitespace-pre-line text-[13px] leading-relaxed text-[var(--color-text-secondary)]">
          {auction.description}
        </p>
      </div>
    </section>
  )
}

export function PhotosCard({ auction }: { auction: AuctionDetail }) {
  const images = [...auction.images].sort((a, b) => a.displayOrder - b.displayOrder)

  return (
    <section aria-labelledby="photos-heading" className="rounded-xl border border-[var(--color-border-default)] px-5 py-4">
      <h2 id="photos-heading" className="mb-3 text-sm font-medium">
        Photos <span className="font-mono text-xs text-muted-foreground">{images.length}</span>
      </h2>
      {images.length === 0 ? (
        <p className="text-sm text-muted-foreground">No photos.</p>
      ) : (
        <ul className="grid grid-cols-4 gap-2">
          {images.map((img) => {
            const src = resolveImageUrl(img.thumbnailUrl ?? img.imageUrl)
            return (
              <li
                key={img.id}
                className={cn(
                  'relative aspect-square overflow-hidden rounded-md border',
                  img.isPrimary ? 'border-[var(--color-border-focus)]' : 'border-[var(--color-border-default)]',
                )}
              >
                {src && <ImageThumbnail src={src} alt="" fill sizes="96px" className="object-cover" />}
                {img.isPrimary && (
                  <span className="absolute bottom-1 left-1 rounded-sm bg-background/90 px-1 text-[10px] font-medium text-[var(--color-text-brand)]">
                    COVER
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
