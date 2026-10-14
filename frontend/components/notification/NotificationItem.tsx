'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { formatTimeAgo } from '@/lib/formatTimeAgo'
import { NOTIFICATION_STYLE } from './notification-style'
import type { Notification } from '@/types/ui/notification.ui'

interface NotificationItemProps {
  notification: Notification
  onSelect:     (notification: Notification) => void
  /** e.g. a selection checkbox (full page) — rendered outside the clickable area */
  leading?:     ReactNode
  /** e.g. row actions (full page) — rendered outside the clickable area */
  trailing?:    ReactNode
  className?:   string
}

export function NotificationItem({ notification: n, onSelect, leading, trailing, className }: NotificationItemProps) {
  const { icon: Icon, accent } = NOTIFICATION_STYLE[n.type]
  return (
    <div className={cn('flex items-start gap-2 px-3 py-3', !n.isRead && 'bg-accent/50', className)}>
      {leading}
      <button
        type="button"
        onClick={() => onSelect(n)}
        className="flex min-w-0 flex-1 items-start gap-2.5 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span
          className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full"
          style={{ color: accent, background: `color-mix(in oklab, ${accent} 15%, transparent)` }}
          aria-hidden
        >
          <Icon className="size-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className={cn('block text-sm', !n.isRead && 'font-medium')}>{!n.isRead && <span className="sr-only">Unread: </span>}{n.title}</span>
          <span className="block text-xs text-muted-foreground line-clamp-2">{n.message}</span>
          <time className="mt-0.5 block text-[10px] text-muted-foreground" dateTime={n.createdAt.toISOString()}>
            {formatTimeAgo(n.createdAt)}
          </time>
        </span>
        <span
          className={cn('mt-1.5 size-2 shrink-0 rounded-full bg-primary', n.isRead && 'invisible')}
          aria-hidden
        />
      </button>
      {trailing}
    </div>
  )
}
