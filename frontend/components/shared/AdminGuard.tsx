'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'
import { Loader2 } from 'lucide-react'

export function AdminGuard({ children }: { readonly children: React.ReactNode }) {
  const router = useRouter()
  const { isAuthenticated, user, accessTokenExpiresAt, refreshToken, setTokens, logout } = useAuthStore()
  const [isMounted, setIsMounted] = useState(false)
  const [isValidating, setIsValidating] = useState(false)

  useEffect(() => {
    setIsMounted(true)
  }, [])

  useEffect(() => {
    if (!isMounted) return

    if (!isAuthenticated) {
      router.push('/login')
      return
    }

    if (user?.role !== 'ADMIN') {
      router.push('/')
      return
    }

    const nearExpiry =
      accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - 60_000

    if (nearExpiry) {
      if (!refreshToken) {
        logout()
        router.push('/login?reason=session_expired')
        return
      }
      setIsValidating(true)
      authService
        .refresh(refreshToken)
        .then(({ data }) => setTokens(data.accessToken, data.refreshToken, data.expiresIn))
        .catch(() => {
          logout()
          router.push('/login?reason=session_expired')
        })
        .finally(() => setIsValidating(false))
    }
  }, [isMounted, isAuthenticated, user, accessTokenExpiresAt, refreshToken, router, setTokens, logout])

  if (!isMounted || !isAuthenticated || user?.role !== 'ADMIN' || isValidating) {
    return (
      <div className="flex h-[80vh] w-full items-center justify-center">
        <Loader2 className="size-8 animate-spin text-[var(--color-text-brand)]" />
      </div>
    )
  }

  return <>{children}</>
}
