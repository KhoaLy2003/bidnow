'use client'

import { useState } from 'react'
import { Bell, BellRing } from 'lucide-react'
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
      <PopoverTrigger render={<Button variant="ghost" size="icon" className="relative" aria-label={label} />}>
        {hasUnread ? <BellRing className="size-5" aria-hidden /> : <Bell className="size-5" aria-hidden />}
        {hasUnread && (
          <span
            className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-0.5 text-[10px] font-medium text-white"
            aria-hidden
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent side="bottom" align="end" sideOffset={8} className="w-auto p-0 shadow-none">
        <NotificationPanel onNavigate={() => setOpen(false)} />
      </PopoverContent>
    </Popover>
  )
}
