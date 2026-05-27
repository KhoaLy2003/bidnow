import * as React from 'react'
import {
  Radio, Timer, AlertTriangle, Lock, Trophy, XCircle, ArrowDown, CalendarClock,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { AuctionStatus } from '@/lib/design-tokens'
import { cn } from '@/lib/utils'
import type { VariantProps } from 'class-variance-authority'
import { badgeVariants } from '@/components/ui/badge'

interface StatusBadgeProps {
  status:     AuctionStatus
  className?: string
}

type BadgeVariant = VariantProps<typeof badgeVariants>['variant']

const CONFIG: Record<
  AuctionStatus,
  { label: string; Icon: React.ComponentType<{ className?: string }> }
> = {
  [AuctionStatus.Scheduled]:  { label: 'Upcoming',    Icon: CalendarClock },
  [AuctionStatus.Active]:     { label: 'Active',      Icon: Radio },
  [AuctionStatus.EndingSoon]: { label: 'Ending Soon', Icon: Timer },
  [AuctionStatus.Critical]:   { label: 'Ending Soon', Icon: AlertTriangle },
  [AuctionStatus.Closed]:     { label: 'Closed',      Icon: Lock },
  [AuctionStatus.Won]:        { label: 'Won',         Icon: Trophy },
  [AuctionStatus.Lost]:       { label: 'Lost',        Icon: XCircle },
  [AuctionStatus.Outbid]:     { label: 'Outbid',      Icon: ArrowDown },
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const { label, Icon } = CONFIG[status]

  return (
    <Badge variant={status as BadgeVariant} className={cn('gap-1', className)}>
      {status === AuctionStatus.Active && (
        <span className="inline-block size-1.5 rounded-full bg-current animate-pulse" />
      )}
      <Icon className="size-3" />
      {label}
    </Badge>
  )
}
