'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { PopoverTitle } from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { useNotificationStore } from '@/store/notificationStore'
import { cn } from '@/lib/utils'
import { NotificationItem } from './NotificationItem'
import type { Notification } from '@/types/ui/notification.ui'

interface NotificationPanelProps {
  /** Called when the user follows a link from the panel (closes the popover). */
  onNavigate: () => void
}

type Tab = 'all' | 'unread'

export function NotificationPanel({ onNavigate }: NotificationPanelProps) {
  const router = useRouter()
  const [tab, setTab] = useState<Tab>('all')
  const notifications = useNotificationStore((s) => s.notifications)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const loading = useNotificationStore((s) => s.loading)
  const hasMore = useNotificationStore((s) => s.hasMore)
  const loadingMore = useNotificationStore((s) => s.loadingMore)
  const loadMore = useNotificationStore((s) => s.loadMore)
  const markRead = useNotificationStore((s) => s.markRead)
  const markAllRead = useNotificationStore((s) => s.markAllRead)

  const visible = tab === 'unread' ? notifications.filter((n) => !n.isRead) : notifications

  const more = async () => {
    if (!(await loadMore())) toast.error('Could not load more notifications. Please try again.')
  }

  const select = (n: Notification) => {
    if (!n.isRead) void markRead(n.id)
    onNavigate()
    router.push(n.linkUrl ?? '/notifications')
  }

  const readAll = async () => {
    if (!(await markAllRead())) toast.error('Could not mark notifications as read. Please try again.')
  }

  return (
    <div className="flex w-[min(392px,calc(100vw-2rem))] flex-col">
      <div className="flex items-center justify-between px-4 pt-3.5 pb-2.5">
        <PopoverTitle className="font-display text-base font-semibold">Notifications</PopoverTitle>
        {unreadCount > 0 && (
          <button
            type="button"
            onClick={readAll}
            className="p-1 text-[13px] font-medium text-[var(--color-text-brand)] hover:underline"
          >
            Mark all read
          </button>
        )}
      </div>

      <div className="flex gap-1 px-3 pb-2" role="tablist" aria-label="Filter notifications">
        {(['all', 'unread'] as const).map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn(
              'h-[30px] rounded-full px-3 text-[13px] font-medium transition-colors',
              tab === t ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {t === 'all' ? 'All' : 'Unread'}
            {t === 'unread' && unreadCount > 0 && <span className="ml-1 font-mono text-xs">{unreadCount}</span>}
          </button>
        ))}
      </div>

      <div className="border-t">
        {loading && notifications.length === 0 ? (
          <div className="flex flex-col gap-3 p-3" aria-busy>
            {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : visible.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">You&apos;re all caught up!</p>
        ) : (
          // The viewport is `size-full`, so a max-height on the root alone never bounds it — cap the viewport itself.
          <ScrollArea className="max-h-96 [&_[data-slot=scroll-area-viewport]]:max-h-96">
            <ul>
              {visible.map((n) => (
                <li key={n.id}>
                  <NotificationItem
                    notification={n}
                    onSelect={select}
                    className="transition-[background-color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:bg-muted"
                  />
                </li>
              ))}
            </ul>
            {hasMore && (
              <div className="border-t p-1.5">
                <Button variant="ghost" size="sm" className="w-full gap-1.5" onClick={more} disabled={loadingMore}>
                  {loadingMore && <Loader2 className="size-3.5 animate-spin" aria-hidden />}
                  {loadingMore ? 'Loading…' : 'Load more'}
                </Button>
              </div>
            )}
          </ScrollArea>
        )}
      </div>

      <Link
        href="/notifications"
        onClick={onNavigate}
        className="block border-t bg-muted p-3 text-center text-[13px] font-medium text-[var(--color-text-brand)] hover:underline"
      >
        View all notifications
      </Link>
    </div>
  )
}
