import { formatCurrency } from '@/lib/format'
import type { CreateAuctionFormData } from '@/types/ui/seller.ui'

const CATEGORIES: Record<string, string> = {
  watches:     'Watches & Jewelry',
  electronics: 'Electronics',
  art:         'Art & Collectibles',
  furniture:   'Home & Furniture',
  fashion:     'Fashion',
  vehicles:    'Vehicles',
  music:       'Music & Instruments',
}

interface AuctionReviewSummaryProps {
  data:          CreateAuctionFormData
  endsAt:        Date
  onEditStep(n: number): void
}

interface ReviewRow {
  label: string
  value: string
  step:  number
  mono?: boolean
}

export function AuctionReviewSummary({ data, endsAt, onEditStep }: AuctionReviewSummaryProps) {
  const rows: ReviewRow[] = [
    { label: 'Title',          value: data.title || '—',                                 step: 1 },
    { label: 'Category',       value: CATEGORIES[data.categoryId] ?? data.categoryId ?? '—', step: 1 },
    { label: 'Description',    value: data.description || '—',                           step: 1 },
    { label: 'Images',         value: `${data.images.length} uploaded`,                  step: 2 },
    { label: 'Starting price', value: data.startingPrice > 0 ? formatCurrency(data.startingPrice) : '—', step: 3, mono: true },
    { label: 'Bid increment',  value: data.bidIncrement  > 0 ? formatCurrency(data.bidIncrement)  : '—', step: 3, mono: true },
    { label: 'Buy it now',     value: data.buyNowPrice   > 0 ? formatCurrency(data.buyNowPrice)   : 'Not set', step: 3, mono: true },
    { label: 'Deposit',        value: data.depositAmount > 0 ? `${formatCurrency(data.depositAmount)} (${((data.depositAmount / data.startingPrice) * 100).toFixed(0)}%)` : '—', step: 3, mono: true },
    { label: 'Duration',       value: `${data.durationDays} day${data.durationDays !== 1 ? 's' : ''} · ends ${endsAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })} ICT`, step: 3 },
    { label: 'Anti-snipe',     value: 'Enabled — +5 min if bid in last 5 min',           step: 3 },
  ]

  return (
    <div className="flex flex-col divide-y divide-dashed divide-border">
      {rows.map(row => (
        <div key={row.label} className="flex items-start gap-4 py-2.5">
          <span className="w-32 shrink-0 text-sm text-muted-foreground">{row.label}</span>
          <span className={`flex-1 text-sm line-clamp-2 ${row.mono ? 'font-mono' : ''}`}>
            {row.value}
          </span>
          <button
            type="button"
            onClick={() => onEditStep(row.step)}
            className="shrink-0 text-xs text-muted-foreground underline underline-offset-2 hover:text-foreground transition-colors duration-[var(--duration-tesla)]"
          >
            edit ↗ Step {row.step}
          </button>
        </div>
      ))}
    </div>
  )
}
