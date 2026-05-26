import { toast } from 'sonner'
import { Trophy, Timer, ArrowDown } from 'lucide-react'
import type { Notification } from '@/types/ui/notification.ui'

const DURATION: Record<Notification['type'], number | undefined> = {
  won:          8_000,
  outbid:       12_000,
  ending_soon:  8_000,
  lost:         8_000,
  bid_placed:   4_000,
  payment_due:  undefined,  // persistent
  system:       5_000,
}

export function showNotificationToast(notification: Notification) {
  const duration = DURATION[notification.type]
  const base = { duration, description: notification.message }

  switch (notification.type) {
    case 'won':
      return toast.success(notification.title, {
        ...base,
        icon: <Trophy className="size-4" />,
        classNames: { toast: 'border-l-4 border-l-[var(--color-auction-won-accent)]' },
      })

    case 'outbid':
      return toast.error(notification.title, {
        ...base,
        icon: <ArrowDown className="size-4" />,
        classNames: { toast: 'border-l-4 border-l-[var(--color-danger-default)]' },
      })

    case 'ending_soon':
      return toast.warning(notification.title, {
        ...base,
        icon: <Timer className="size-4" />,
        classNames: { toast: 'border-l-4 border-l-[var(--color-auction-ending-accent)]' },
      })

    default:
      return toast(notification.title, base)
  }
}
