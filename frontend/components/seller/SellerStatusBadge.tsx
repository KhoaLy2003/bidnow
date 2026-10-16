import {
  Radio, CalendarClock, CheckCircle2, AlertCircle, X, FileText, ShieldX,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import { cn } from '@/lib/utils'

type BadgeVariant = 'default' | 'secondary' | 'scheduled' | 'active' | 'ending-soon' | 'critical' | 'closed' | 'won' | 'lost' | 'outbid'

/** How close a live auction is to closing. Written out in the label as well as coloured. */
export type LiveTone = 'live' | 'warning' | 'critical'

const CONFIG: Record<SellerAuctionStatus, {
  label:   string
  variant: BadgeVariant
  Icon:    React.ComponentType<{ className?: string }>
  pulse?:  boolean
}> = {
  [SellerAuctionStatus.Draft]:      { label: 'Draft',     variant: 'secondary', Icon: FileText },
  [SellerAuctionStatus.Scheduled]:  { label: 'Scheduled', variant: 'scheduled', Icon: CalendarClock },
  [SellerAuctionStatus.Active]:     { label: 'Live',      variant: 'active',    Icon: Radio,         pulse: true },
  [SellerAuctionStatus.Completed]:  { label: 'Sold',      variant: 'won',       Icon: CheckCircle2 },
  [SellerAuctionStatus.Failed]:     { label: 'No Sale',   variant: 'closed',    Icon: AlertCircle },
  [SellerAuctionStatus.Cancelled]:  { label: 'Cancelled', variant: 'closed',    Icon: X },
  [SellerAuctionStatus.Rejected]:   { label: 'Rejected',  variant: 'lost',      Icon: ShieldX },
}

const LIVE_TONES: Record<Exclude<LiveTone, 'live'>, { label: string; variant: BadgeVariant }> = {
  warning:  { label: 'Ending soon',  variant: 'ending-soon' },
  critical: { label: 'Final minute', variant: 'critical' },
}

interface SellerStatusBadgeProps {
  status:     SellerAuctionStatus
  /** Only read for live auctions. */
  tone?:      LiveTone
  className?: string
}

export function SellerStatusBadge({ status, tone = 'live', className }: SellerStatusBadgeProps) {
  const base = CONFIG[status]
  const override = status === SellerAuctionStatus.Active && tone !== 'live' ? LIVE_TONES[tone] : null
  const { Icon, pulse } = base
  return (
    <Badge variant={override?.variant ?? base.variant} className={cn('gap-1', className)}>
      {pulse && <span className="inline-block size-1.5 rounded-full bg-current animate-pulse" />}
      <Icon className="size-3" />
      {override?.label ?? base.label}
    </Badge>
  )
}
