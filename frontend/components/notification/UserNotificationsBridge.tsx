'use client'

import { useUserNotifications } from '@/hooks/useUserNotifications'

/** Mounts the personal-notification connection once for the whole app (root layout). Renders nothing. */
export function UserNotificationsBridge() {
  useUserNotifications()
  return null
}
