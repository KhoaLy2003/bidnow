import { formatCurrency } from '@/lib/format'
import type { AuctionDetail } from '@/types/ui/auction.ui'

interface ItemSpecsProps {
  readonly auction:       AuctionDetail
  readonly categoryLabel: string
}

export function ItemSpecs({ auction, categoryLabel }: ItemSpecsProps) {
  const rows: { label: string; value: React.ReactNode; mono?: boolean }[] = [
    { label: 'Category',         value: categoryLabel },
    { label: 'Starting price',   value: formatCurrency(auction.startingPrice), mono: true },
    { label: 'Bid increment',    value: formatCurrency(auction.bidIncrement), mono: true },
    { label: 'Deposit required', value: formatCurrency(auction.depositAmount), mono: true },
    ...(auction.buyNowPrice !== undefined
      ? [{ label: 'Buy now price', value: formatCurrency(auction.buyNowPrice), mono: true }]
      : []),
  ]

  return (
    <section id="specs">
      <h2 className="font-display text-2xl font-semibold tracking-tight mb-3.5">Specifications</h2>
      <dl className="overflow-hidden rounded-2xl border">
        {rows.map((r, i) => (
          <div key={r.label} className={`flex ${i === 0 ? '' : 'border-t'}`}>
            <dt className="w-2/5 shrink-0 bg-muted px-[18px] py-3.5 text-[13px] text-muted-foreground">{r.label}</dt>
            <dd className={`flex-1 px-[18px] py-3.5 text-sm font-medium ${r.mono ? 'font-mono' : ''}`}>{r.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
