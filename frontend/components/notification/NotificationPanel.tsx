'use client'

import { Bell, CheckCheck } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { useNotifications } from '@/hooks/useNotifications'
import { formatRelativeTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Notification } from '@/types/notification'

const TYPE_COLOR: Record<Notification['type'], string> = {
  outbid:      'bg-[var(--color-danger-default)]',
  won:         'bg-[var(--color-auction-won-accent)]',
  lost:        'bg-[var(--color-danger-subtle)]',
  ending_soon: 'bg-[var(--color-auction-ending-accent)]',
  bid_placed:  'bg-[var(--color-brand-500)]',
  payment_due: 'bg-[var(--color-warning-default)]',
  system:      'bg-muted-foreground',
}

export function NotificationPanel() {
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications()

  return (
    <div className="flex flex-col gap-0 w-80">
      <div className="flex items-center justify-between px-3 py-2.5 border-b">
        <div className="flex items-center gap-2">
          <Bell className="size-4" />
          <span className="font-medium text-sm">Notifications</span>
          {unreadCount > 0 && (
            <span className="flex size-5 items-center justify-center rounded-full bg-primary text-[10px] font-medium text-primary-foreground">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <Button variant="ghost" size="xs" onClick={markAllRead} className="text-xs gap-1">
            <CheckCheck className="size-3" />
            Mark all read
          </Button>
        )}
      </div>

      {notifications.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          You&apos;re all caught up!
        </p>
      ) : (
        <ScrollArea className="max-h-96">
          {notifications.map((n, i) => (
            <div key={n.id}>
              {i > 0 && <Separator />}
              <button
                className={cn(
                  'w-full text-left px-3 py-3 hover:bg-accent transition-colors flex items-start gap-2.5',
                  !n.isRead && 'bg-accent/50',
                )}
                onClick={() => markRead(n.id)}
              >
                <span
                  className={cn(
                    'mt-1 size-2 shrink-0 rounded-full',
                    TYPE_COLOR[n.type],
                    n.isRead && 'opacity-0',
                  )}
                />
                <div className="flex-1 min-w-0">
                  <p className={cn('text-sm', !n.isRead && 'font-medium')}>
                    {n.title}
                  </p>
                  <p className="text-xs text-muted-foreground line-clamp-2">
                    {n.message}
                  </p>
                  <p className="mt-0.5 text-[10px] text-muted-foreground">
                    {formatRelativeTime(n.createdAt)}
                  </p>
                </div>
              </button>
            </div>
          ))}
        </ScrollArea>
      )}
    </div>
  )
}
