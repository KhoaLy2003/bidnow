'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { Loader2 } from 'lucide-react'

export function AdminGuard({ children }: { readonly children: React.ReactNode }) {
  const router = useRouter()
  const { isAuthenticated, user } = useAuthStore()

  const [isMounted, setIsMounted] = useState(false)

  useEffect(() => {
    setIsMounted(true)
  }, [])

  useEffect(() => {
    if (isMounted) {
      if (!isAuthenticated) {
        router.push('/login')
      } else if (user?.role !== 'ADMIN') {
        router.push('/')
      }
    }
  }, [isMounted, isAuthenticated, user, router])

  if (!isMounted || !isAuthenticated || user?.role !== 'ADMIN') {
    return (
      <div className="flex h-[80vh] w-full items-center justify-center">
        <Loader2 className="size-8 animate-spin text-[var(--color-text-brand)]" />
      </div>
    )
  }

  return <>{children}</>
}
