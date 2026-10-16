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
    <div className={cn('flex items-start gap-2 px-4 py-3', className)}>
      {leading}
      <button
        type="button"
        onClick={() => onSelect(n)}
        className="flex min-w-0 flex-1 items-start gap-3 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span
          className="flex size-[34px] shrink-0 items-center justify-center rounded-full"
          style={{ color: accent, background: `color-mix(in oklab, ${accent} 15%, transparent)` }}
          aria-hidden
        >
          <Icon className="size-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[13px] font-semibold">{!n.isRead && <span className="sr-only">Unread: </span>}{n.title}</span>
          <span className="mt-0.5 block text-xs text-muted-foreground line-clamp-2">{n.message}</span>
          <time className="mt-1 block text-[11px] text-muted-foreground" dateTime={n.createdAt.toISOString()}>
            {formatTimeAgo(n.createdAt)}
          </time>
        </span>
        <span
          className={cn('mt-1.5 size-2 shrink-0 rounded-full bg-[var(--color-text-brand)]', n.isRead && 'invisible')}
          aria-hidden
        />
      </button>
      {trailing}
    </div>
  )
}
