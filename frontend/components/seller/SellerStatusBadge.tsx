import {
  Radio, Timer, AlertTriangle, Lock, Trophy, XCircle, Ban, FileText,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { SellerAuctionStatus } from '@/types/seller'
import { cn } from '@/lib/utils'

type BadgeVariant = 'default' | 'secondary' | 'active' | 'ending-soon' | 'critical' | 'closed' | 'won' | 'lost'

const CONFIG: Record<SellerAuctionStatus, {
  label:   string
  variant: BadgeVariant
  Icon:    React.ComponentType<{ className?: string }>
  pulse?:  boolean
}> = {
  [SellerAuctionStatus.Draft]:      { label: 'Draft',       variant: 'secondary',   Icon: FileText },
  [SellerAuctionStatus.Active]:     { label: 'Active',      variant: 'active',      Icon: Radio,          pulse: true },
  [SellerAuctionStatus.EndingSoon]: { label: 'Ending Soon', variant: 'ending-soon', Icon: Timer },
  [SellerAuctionStatus.Critical]:   { label: 'Critical',    variant: 'critical',    Icon: AlertTriangle },
  [SellerAuctionStatus.Closed]:     { label: 'Closed',      variant: 'closed',      Icon: Lock },
  [SellerAuctionStatus.Won]:        { label: 'Sold',        variant: 'won',         Icon: Trophy },
  [SellerAuctionStatus.Failed]:     { label: 'No Sale',     variant: 'closed',      Icon: XCircle },
  [SellerAuctionStatus.Cancelled]:  { label: 'Cancelled',   variant: 'closed',      Icon: Ban },
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
