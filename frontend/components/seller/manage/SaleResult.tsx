import { formatCurrency } from '@/lib/format'
import type { AuctionDetail } from '@/types/ui/auction.ui'

export type SaleVariant = 'sold' | 'forfeited' | 'unsold'

const ENDED: Intl.DateTimeFormatOptions = {
  month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
}

const TONES = {
  won:    { bg: 'var(--color-auction-won-bg)',    border: 'var(--color-auction-won-border)',    label: 'var(--color-auction-won-text)',    value: 'var(--color-auction-won-text)' },
  closed: { bg: 'var(--color-auction-closed-bg)', border: 'var(--color-auction-closed-border)', label: 'var(--color-auction-closed-text)', value: 'var(--color-text-primary)' },
} as const

interface SaleResultProps {
  auction:       AuctionDetail
  variant:       SaleVariant
  bidders:       number
  biddersApprox: boolean
  winnerName?:   string
}

/** The outcome strip at the top of an ended auction: the price, how contested it was, and the anti-snipe history. */
export function SaleResult({ auction, variant, bidders, biddersApprox, winnerName }: SaleResultProps) {
  const tone = variant === 'sold' ? TONES.won : TONES.closed
  const endedAt = (auction.completedAt ?? auction.endsAt).toLocaleString('en-US', ENDED)
  const who = winnerName ? `@${winnerName}` : 'the winner'
  const hasBids = auction.totalBids > 0
  const overPct = hasBids && auction.startingPrice > 0
    ? Math.round(((auction.currentBid - auction.startingPrice) / auction.startingPrice) * 100)
    : null

  const heading =
    variant === 'sold' ? 'Final price' : variant === 'forfeited' ? 'Winning bid, not paid' : 'Ended without a sale'
  const sub =
    variant === 'sold' ? `Won by ${who} · ended ${endedAt}`
    : variant === 'forfeited' ? `${who} did not pay · ended ${endedAt}`
    : `Ended ${endedAt}`

  const cell = 'flex min-w-[140px] flex-[1_1_140px] flex-col gap-1.5 p-5'
  const divider = { borderColor: tone.border }

  return (
    <section
      aria-label="Result"
      className="flex flex-wrap overflow-hidden rounded-xl border"
      style={{ background: tone.bg, borderColor: tone.border }}
    >
      <div className={`${cell} flex-[1.4_1_300px] border-r`} style={divider}>
        <p className="text-xs uppercase" style={{ color: tone.label }}>{heading}</p>
        <p className="font-mono text-[length:var(--font-size-3xl)] font-medium leading-tight" style={{ color: tone.value }}>
          {hasBids ? formatCurrency(auction.currentBid) : '—'}
        </p>
        <p className="text-xs" style={{ color: tone.label }}>{sub}</p>
      </div>

      <div className={`${cell} border-r`} style={divider}>
        <p className="text-xs uppercase" style={{ color: tone.label }}>Activity</p>
        <div className="flex gap-6">
          <div>
            <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">{auction.totalBids}</p>
            <p className="text-xs" style={{ color: tone.label }}>bids</p>
          </div>
          <div>
            <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">
              {bidders}{biddersApprox ? '+' : ''}
            </p>
            <p className="text-xs" style={{ color: tone.label }}>bidders</p>
          </div>
        </div>
      </div>

      <div className={`${cell} border-r`} style={divider}>
        <p className="text-xs uppercase" style={{ color: tone.label }}>Over starting price</p>
        <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">
          {overPct === null ? '—' : `${overPct > 0 ? '+' : ''}${overPct}%`}
        </p>
        <p className="text-xs" style={{ color: tone.label }}>Started at {formatCurrency(auction.startingPrice)}</p>
      </div>

      <div className={cell}>
        <p className="text-xs uppercase" style={{ color: tone.label }}>Anti-snipe</p>
        <p className="font-mono text-[length:var(--font-size-2xl)] font-medium leading-tight">{auction.extensionCount}×</p>
        <p className="text-xs" style={{ color: tone.label }}>
          {auction.extensionCount > 0
            ? `extended from ${auction.originalEndAt.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}`
            : 'never extended'}
        </p>
      </div>
    </section>
  )
}
