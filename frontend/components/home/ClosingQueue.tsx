'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useCountdown } from '@/hooks/useCountdown'
import { formatCurrency } from '@/lib/format'
import { formatQueueClock, queueState, type QueueTone } from '@/lib/home-queue'
import { resolveImageUrl } from '@/lib/image-utils'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

const TONE_TEXT: Record<QueueTone, string> = {
  live:     'text-[var(--color-text-brand)]',
  warning:  'text-[var(--color-auction-ending-text)]',
  critical: 'text-[var(--color-auction-critical-text)]',
  closed:   'text-muted-foreground',
}

function QueueRow({ item }: { item: AuctionBrowseItem }) {
  const { secondsLeft } = useCountdown(item.endTime)
  const known = secondsLeft !== null
  const state = queueState(secondsLeft ?? Number.POSITIVE_INFINITY)
  const imageUrl = resolveImageUrl(item.primaryImageUrl) ?? (item.primaryImageUrl ? undefined : '/placeholder-auction.png')
  const closed = state.tone === 'closed'

  return (
    <li className="grid grid-cols-[3.25rem_minmax(0,1fr)_auto] items-start gap-x-3.5 gap-y-3 border-t border-[var(--color-border-default)] p-4 transition-colors duration-[var(--duration-tesla)] first:border-t-0 hover:bg-[var(--color-bg-elevated)] md:grid-cols-[4rem_minmax(0,1fr)_9.5rem_12.5rem_8.25rem] md:items-center md:gap-x-6 md:px-6">
      <div className="relative row-span-2 size-[3.25rem] overflow-hidden rounded-xl bg-muted md:row-span-1 md:size-16">
        {imageUrl && <Image src={imageUrl} alt="" fill sizes="64px" className={cn('object-cover', closed && 'grayscale')} />}
      </div>

      <div className="min-w-0">
        <Link
          href={`/auctions/${item.id}`}
          className="block truncate font-display text-[length:var(--font-size-md)] font-medium leading-snug transition-colors duration-[var(--duration-tesla)] hover:text-[var(--color-text-brand)]"
        >
          {item.title}
        </Link>
        <p className="mt-1 text-[length:var(--font-size-xs)] text-muted-foreground">
          {item.categoryName} · {item.totalBids} {item.totalBids === 1 ? 'bid' : 'bids'}
        </p>
      </div>

      <div className={cn('min-w-24 text-right md:order-4 md:min-w-0 md:text-left', known ? TONE_TEXT[state.tone] : 'text-muted-foreground')}>
        <p className="font-mono text-[length:var(--font-size-md)] font-medium leading-none md:text-[length:var(--font-size-lg)]" role="timer" aria-label="Time left">
          {known ? formatQueueClock(secondsLeft) : '--:--:--'}
        </p>
        <div className="mt-2 hidden h-1 overflow-hidden rounded-full bg-muted md:block" aria-hidden="true">
          <div
            className="h-full rounded-full bg-current transition-[width] duration-1000 ease-linear"
            style={{ width: `${known ? state.progress : 0}%` }}
          />
        </div>
        <p className="mt-1.5 text-[length:var(--font-size-xs)]">{known ? state.label : 'Loading'}</p>
      </div>

      <div className="md:order-3">
        <p className="hidden text-[length:var(--font-size-xs)] uppercase text-muted-foreground md:block">Current bid</p>
        <p className="font-mono text-[length:var(--font-size-md)] font-medium md:mt-0.5 md:text-[length:var(--font-size-lg)]">
          {formatCurrency(item.currentPrice)}
        </p>
      </div>

      <div className="col-start-3 row-start-2 flex justify-end md:order-5 md:col-auto md:row-auto">
        <Button
          variant={closed ? 'outline' : 'brand'}
          size="lg"
          className="h-9 px-4"
          render={<Link href={`/auctions/${item.id}`} />}
          nativeButton={false}
        >
          {closed ? 'See result' : 'Bid now'}
        </Button>
      </div>
    </li>
  )
}

/** Lots ranked by time left. Each row runs its own live countdown. */
export function ClosingQueue({ items }: { items: AuctionBrowseItem[] }) {
  return (
    <ul className="overflow-hidden rounded-xl border border-[var(--color-border-default)] bg-card">
      {items.map((item) => (
        <QueueRow key={item.id} item={item} />
      ))}
    </ul>
  )
}
