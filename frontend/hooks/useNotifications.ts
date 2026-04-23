'use client'

import { useNotificationStore } from '@/store/notificationStore'

export function useNotifications() {
  return useNotificationStore()
}
