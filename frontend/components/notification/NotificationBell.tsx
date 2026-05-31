'use client'

import { Bell, BellRing } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { NotificationPanel } from './NotificationPanel'
import { useNotifications } from '@/hooks/useNotifications'

export function NotificationBell() {
  const { unreadCount } = useNotifications()
  const hasUnread = unreadCount > 0

  return (
    <Popover>
      <PopoverTrigger render={<Button variant="ghost" size="icon" className="relative" aria-label="Notifications" />}>
        {hasUnread ? (
          <BellRing className="size-5" />
        ) : (
          <Bell className="size-5" />
        )}
        {hasUnread && (
          <span className="absolute -top-0.5 -right-0.5 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-medium text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent
        side="bottom"
        align="end"
        sideOffset={8}
        className="w-auto p-0"
      >
        <NotificationPanel />
      </PopoverContent>
    </Popover>
  )
}
