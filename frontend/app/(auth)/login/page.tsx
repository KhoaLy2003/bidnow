'use client'

import { Suspense, useState, useEffect, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Loader2, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'

function LoginForm() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setAuth } = useAuthStore()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showExpiredBanner, setShowExpiredBanner] = useState(
    searchParams.get('reason') === 'session_expired'
  )

  useEffect(() => {
    if (searchParams.get('reason') === 'session_expired') {
      window.history.replaceState(null, '', '/login')
    }
    // Intentionally runs once on mount — we only need to clean the URL param once
    // after capturing it into state. Re-running on searchParams changes would cause
    // a loop if the router reflects the replaceState update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsLoading(true)
    setError(null)

    const formData = new FormData(e.currentTarget)
    const email = formData.get('email') as string
    const password = formData.get('password') as string

    try {
      const result = await authService.login(email, password)
      setAuth(result.data)

      if (result.data.role === 'ADMIN') {
        router.push('/admin/users')
      } else {
        router.push('/')
      }
    } catch (err: any) {
      setError(err.message || 'Invalid email or password')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 flex flex-col gap-6">
        <div className="text-center">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Welcome back</h1>
          <p className="mt-1 text-sm text-muted-foreground">Sign in to your account</p>
        </div>

        {showExpiredBanner && (
          <div className="flex items-start gap-3 rounded-lg border border-[var(--color-info-border)] bg-[var(--color-info-subtle)] px-3 py-2.5 text-sm text-[var(--color-info-text)]">
            <span className="flex-1">Your session has expired. Please sign in again.</span>
            <button onClick={() => setShowExpiredBanner(false)} aria-label="Dismiss">
              <X className="size-4 shrink-0" />
            </button>
          </div>
        )}

        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-lg border border-destructive/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              placeholder="you@example.com"
              required
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link href="/forgot-password" className="text-xs text-[var(--color-text-link)] hover:underline">
                Forgot password?
              </Link>
            </div>
            <Input
              id="password"
              name="password"
              type="password"
              placeholder="••••••••"
              required
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading}>
            {isLoading && <Loader2 className="size-4 animate-spin mr-2" />}
            Sign in
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{' '}
          <Link href="/register" className="text-[var(--color-text-link)] hover:underline font-medium">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  )
}
