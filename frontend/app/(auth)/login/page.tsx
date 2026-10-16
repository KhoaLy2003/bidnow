'use client'

import { Suspense, useState, useEffect, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { AlertCircle, ArrowRight, Loader2, ShieldCheck, Timer, X, Zap } from 'lucide-react'
import { AuthShell } from '@/components/auth/AuthShell'
import { PasswordInput } from '@/components/auth/PasswordInput'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'
import { getErrorMessage } from '@/lib/utils'

const FACTS = [
  { Icon: Zap,         label: 'Auto-bid up to your max' },
  { Icon: Timer,       label: '+5:00 on late bids, so nobody wins by timing a click' },
  { Icon: ShieldCheck, label: 'Deposit refunded when you lose' },
] as const

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
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Invalid email or password'))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      headline={
        <>
          Welcome back.
          <br />
          The <span className="text-[var(--color-brand-300)]">clock</span> kept ticking.
        </>
      }
      description="Sign in to pick up your bids, watch your lists and see who is closing next."
      aside={
        <ul className="flex flex-col gap-3.5">
          {FACTS.map(({ Icon, label }) => (
            <li key={label} className="flex items-center gap-3 text-[0.9375rem] text-muted-foreground">
              <Icon className="size-[18px] shrink-0 text-[var(--color-warning-default)]" aria-hidden="true" />
              {label}
            </li>
          ))}
        </ul>
      }
    >
      <div className="flex flex-col gap-7">
        <div className="flex flex-col gap-2">
          <h2 className="font-display text-[length:var(--font-size-2xl)] font-medium leading-tight tracking-tight">
            Sign in
          </h2>
          <p className="text-muted-foreground">
            New to BidNow?{' '}
            <Link href="/register" className="font-medium text-[var(--color-text-link)] hover:underline">
              Create an account
            </Link>
          </p>
        </div>

        {showExpiredBanner && (
          <div
            role="status"
            className="flex items-center gap-2 rounded-md border border-[var(--color-info-border)] bg-[var(--color-info-subtle)] py-1 pl-3.5 pr-1 text-sm text-[var(--color-info-text)]"
          >
            <span className="flex-1">Your session has expired. Please sign in again.</span>
            <button
              type="button"
              onClick={() => setShowExpiredBanner(false)}
              aria-label="Dismiss"
              className="grid size-10 place-items-center rounded-md hover:bg-black/5"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>
        )}

        {error && (
          <div
            role="alert"
            className="flex gap-2.5 rounded-md border border-destructive/20 bg-destructive/10 px-3.5 py-3 text-sm text-destructive"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="flex flex-col gap-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              required
              className="h-12"
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link href="/forgot-password" className="text-sm text-[var(--color-text-link)] hover:underline">
                Forgot password?
              </Link>
            </div>
            <PasswordInput
              id="password"
              name="password"
              autoComplete="current-password"
              placeholder="Enter your password"
              required
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <Button type="submit" variant="brand" size="xl" className="mt-1 h-12 w-full text-base" disabled={isLoading}>
            {isLoading ? <Loader2 className="size-4 animate-spin" /> : null}
            Sign in
            {!isLoading && <ArrowRight className="size-4" />}
          </Button>
        </form>
      </div>
    </AuthShell>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  )
}
