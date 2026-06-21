import {
  Radio, Timer, Lock, XCircle, Ban, FileText, ShieldX,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import { cn } from '@/lib/utils'

type BadgeVariant = 'default' | 'secondary' | 'scheduled' | 'active' | 'closed' | 'won' | 'lost' | 'outbid'

const CONFIG: Record<SellerAuctionStatus, {
  label:   string
  variant: BadgeVariant
  Icon:    React.ComponentType<{ className?: string }>
  pulse?:  boolean
}> = {
  [SellerAuctionStatus.Draft]:      { label: 'Draft',       variant: 'secondary',   Icon: FileText },
  [SellerAuctionStatus.Scheduled]:  { label: 'Scheduled',   variant: 'scheduled',   Icon: Timer },
  [SellerAuctionStatus.Active]:     { label: 'Active',      variant: 'active',      Icon: Radio,          pulse: true },
  [SellerAuctionStatus.Completed]:  { label: 'Completed',   variant: 'closed',      Icon: Lock },
  [SellerAuctionStatus.Failed]:     { label: 'Failed',      variant: 'closed',      Icon: XCircle },
  [SellerAuctionStatus.Cancelled]:  { label: 'Cancelled',   variant: 'closed',      Icon: Ban },
  [SellerAuctionStatus.Rejected]:   { label: 'Rejected',     variant: 'lost',        Icon: ShieldX },
}

interface SellerStatusBadgeProps {
  status:     SellerAuctionStatus
  className?: string
}

export function SellerStatusBadge({ status, className }: SellerStatusBadgeProps) {
  const { label, variant, Icon, pulse } = CONFIG[status]
  return (
    <Badge variant={variant} className={cn('gap-1', className)}>
      {pulse && <span className="inline-block size-1.5 rounded-full bg-current animate-pulse" />}
      <Icon className="size-3" />
      {label}
    </Badge>
  )
}
