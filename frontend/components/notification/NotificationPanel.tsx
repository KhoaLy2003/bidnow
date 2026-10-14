'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Bell, CheckCheck } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { PopoverTitle } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { useNotificationStore } from '@/store/notificationStore'
import { NotificationItem } from './NotificationItem'
import type { Notification } from '@/types/ui/notification.ui'

interface NotificationPanelProps {
  /** Called when the user follows a link from the panel (closes the popover). */
  onNavigate: () => void
}

export function NotificationPanel({ onNavigate }: NotificationPanelProps) {
  const router = useRouter()
  const notifications = useNotificationStore((s) => s.notifications)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const loading = useNotificationStore((s) => s.loading)
  const markRead = useNotificationStore((s) => s.markRead)
  const markAllRead = useNotificationStore((s) => s.markAllRead)

  const select = (n: Notification) => {
    if (!n.isRead) void markRead(n.id)
    onNavigate()
    router.push(n.linkUrl ?? '/notifications')
  }

  const readAll = async () => {
    if (!(await markAllRead())) toast.error('Could not mark notifications as read. Please try again.')
  }

  return (
    <div className="flex w-[min(22rem,calc(100vw-2rem))] flex-col">
      <div className="flex items-center justify-between border-b px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Bell className="size-4" aria-hidden />
          <PopoverTitle className="text-sm font-medium">Notifications</PopoverTitle>
          {unreadCount > 0 && (
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-medium text-primary-foreground">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <Button variant="ghost" size="xs" onClick={readAll} className="gap-1 text-xs">
            <CheckCheck className="size-3" aria-hidden />
            Mark all as read
          </Button>
        )}
      </div>

      {loading && notifications.length === 0 ? (
        <div className="flex flex-col gap-3 p-3" aria-busy>
          {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}
        </div>
      ) : notifications.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">You&apos;re all caught up!</p>
      ) : (
        <ScrollArea className="max-h-96">
          <ul>
            {notifications.map((n, i) => (
              <li key={n.id}>
                {i > 0 && <Separator />}
                <NotificationItem notification={n} onSelect={select} className="hover:bg-accent transition-[background-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]" />
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}

      <div className="border-t p-1.5">
        <Button variant="ghost" size="sm" className="w-full" render={<Link href="/notifications" onClick={onNavigate} />} nativeButton={false}>
          View all notifications
        </Button>
      </div>
    </div>
  )
}
