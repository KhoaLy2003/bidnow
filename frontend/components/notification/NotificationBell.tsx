'use client'

import { useState } from 'react'
import { Bell } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { useNotificationStore } from '@/store/notificationStore'
import { NotificationPanel } from './NotificationPanel'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const hasUnread = unreadCount > 0
  const label = hasUnread ? `Notifications, ${unreadCount} unread` : 'Notifications'

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={<Button variant="ghost" className="relative size-11 rounded-[6px] aria-expanded:bg-muted" aria-label={label} />}
      >
        <Bell className="size-5" aria-hidden />
        {hasUnread && (
          <span
            className="absolute top-[5px] right-1 flex size-auto h-[17px] min-w-[17px] items-center justify-center rounded-full border-2 border-background bg-[var(--color-danger-default)] px-1 font-mono text-[10px] font-semibold text-white"
            aria-hidden
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent
        side="bottom"
        align="end"
        sideOffset={8}
        className="w-auto gap-0 overflow-hidden rounded-[10px] p-0 shadow-[0_18px_48px_rgb(20_23_31/0.16)]"
      >
        <NotificationPanel onNavigate={() => setOpen(false)} />
      </PopoverContent>
    </Popover>
  )
}
