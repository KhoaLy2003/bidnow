import Link from 'next/link'
import { TrendingUp } from 'lucide-react'
import { formatCurrency } from '@/lib/format'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

interface LiveTapeProps {
  items: AuctionBrowseItem[]
}

/** Entries in one half of the loop; enough to overflow a wide screen even with a single lot. */
const MIN_ENTRIES = 8

interface Entry {
  item:   AuctionBrowseItem
  /** Repeats of a lot are decoration: hidden from assistive tech and the tab order. */
  repeat: boolean
}

function TapeHalf({ entries, hidden = false }: { entries: Entry[]; hidden?: boolean }) {
  return (
    <ul className="flex shrink-0" aria-hidden={hidden || undefined} inert={hidden || undefined}>
      {entries.map(({ item, repeat }, i) => (
        <li key={`${item.id}-${i}`} aria-hidden={repeat || undefined} inert={repeat || undefined}>
          <Link
            href={`/auctions/${item.id}`}
            className="flex items-center gap-2.5 px-7 py-3.5 text-sm text-white/80 transition-colors duration-[var(--duration-tesla)] hover:text-white"
          >
            <TrendingUp className="size-4 text-[var(--color-warning-default)]" aria-hidden="true" />
            <span className="max-w-[16rem] truncate">{item.title}</span>
            <span className="font-mono font-medium text-white">{formatCurrency(item.currentPrice)}</span>
            <span className="text-white/55">{item.totalBids} {item.totalBids === 1 ? 'bid' : 'bids'}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

/** A watch-bezel ruler and a marquee of lots under the hero; pauses on hover and for reduced motion. */
export function LiveTape({ items }: LiveTapeProps) {
  if (items.length === 0) return null

  const copies = Math.ceil(MIN_ENTRIES / items.length)
  const entries: Entry[] = Array.from({ length: copies }, (_, copy) =>
    items.map((item) => ({ item, repeat: copy > 0 })),
  ).flat()

  return (
    <div className="dark bg-[var(--color-brand-900)]">
      <div className="home-ruler" aria-hidden="true" />
      <div className="overflow-hidden border-t border-white/10 bg-[var(--color-brand-800)]" aria-label="Live lots">
        <div className="home-tape-track flex w-max">
          <TapeHalf entries={entries} />
          <TapeHalf entries={entries} hidden />
        </div>
      </div>
    </div>
  )
}
