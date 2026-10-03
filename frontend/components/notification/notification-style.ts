import {
  ArrowDown, Bell, CheckCircle2, CreditCard, Gavel, Megaphone, RotateCcw, Timer, Trophy, XCircle,
  AlertTriangle, type LucideIcon,
} from 'lucide-react'
import type { NotificationType } from '@/types/ui/notification.ui'

/** Icon + accent colour per UI category (design tokens from app/globals.css; no raw hex). */
export const NOTIFICATION_STYLE: Record<NotificationType, { icon: LucideIcon; accent: string }> = {
  outbid:         { icon: ArrowDown,     accent: 'var(--color-danger-default)' },
  won:            { icon: Trophy,        accent: 'var(--color-auction-won-accent)' },
  lost:           { icon: XCircle,       accent: 'var(--muted-foreground)' },
  ending_soon:    { icon: Timer,         accent: 'var(--color-auction-ending-accent)' },
  bid_placed:     { icon: Gavel,         accent: 'var(--color-brand-500)' },
  payment_due:    { icon: CreditCard,    accent: 'var(--color-warning-default)' },
  payment:        { icon: CheckCircle2,  accent: 'var(--color-success-default)' },
  payment_failed: { icon: AlertTriangle, accent: 'var(--color-danger-default)' },
  auction:        { icon: Megaphone,     accent: 'var(--color-info-default)' },
  refund:         { icon: RotateCcw,     accent: 'var(--color-success-default)' },
  system:         { icon: Bell,          accent: 'var(--muted-foreground)' },
}
