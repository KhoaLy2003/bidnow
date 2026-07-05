import { formatCurrency } from '@/lib/format'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface ItemSpecsProps {
  readonly auction:       AuctionDetail
  readonly categoryLabel: string
}

function SpecRow({
  label,
  value,
  mono = false,
  last = false,
}: {
  readonly label: string
  readonly value: React.ReactNode
  readonly mono?: boolean
  readonly last?: boolean
}) {
  return (
    <div
      className={`flex items-center justify-between py-[11px] ${last ? '' : 'border-b border-[var(--color-border-default)]'}`}
    >
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={`text-xs font-medium ${mono ? 'font-mono' : ''}`}>{value}</span>
    </div>
  )
}

export function ItemSpecs({ auction, categoryLabel }: ItemSpecsProps) {
  const rows: { label: string; value: React.ReactNode; mono?: boolean }[] = [
    { label: 'Category',        value: categoryLabel },
    { label: 'Starting price',  value: formatCurrency(auction.startingPrice), mono: true },
    { label: 'Bid increment',   value: formatCurrency(auction.bidIncrement), mono: true },
    { label: 'Deposit required', value: formatCurrency(auction.depositAmount), mono: true },
    ...(auction.buyNowPrice !== undefined
      ? [{ label: 'Buy now price', value: formatCurrency(auction.buyNowPrice), mono: true }]
      : []),
  ]

  const half      = Math.ceil(rows.length / 2)
  const leftRows  = rows.slice(0, half)
  const rightRows = rows.slice(half)

  return (
    <section>
      <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
        Specifications
      </p>
      <div className="grid grid-cols-1 md:grid-cols-2 md:gap-x-9 border border-[var(--color-border-default)] rounded-lg px-4 py-1">
        <div>
          {leftRows.map((r, i) => (
            <SpecRow key={r.label} label={r.label} value={r.value} mono={r.mono} last={i === leftRows.length - 1} />
          ))}
        </div>
        <div className="md:border-l md:border-[var(--color-border-default)] md:pl-9">
          {rightRows.map((r, i) => (
            <SpecRow key={r.label} label={r.label} value={r.value} mono={r.mono} last={i === rightRows.length - 1} />
          ))}
        </div>
      </div>
    </section>
  )
}
