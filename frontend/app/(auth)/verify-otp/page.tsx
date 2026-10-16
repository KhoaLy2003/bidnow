'use client'

import { useState, useEffect, type FormEvent, Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { AlertCircle, Loader2, ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { AuthShell } from '@/components/auth/AuthShell'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authService } from '@/services/auth.service'
import { toast } from 'sonner'
import { getErrorMessage } from '@/lib/utils'

function VerifyOtpContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const email = searchParams.get('email') || ''

  const [isLoading, setIsLoading] = useState(false)
  const [isResending, setIsResending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [otp, setOtp] = useState('')

  useEffect(() => {
    if (!email) {
      router.push('/register')
    }
  }, [email, router])

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (otp.length !== 6) {
      setError('Please enter a 6-digit code')
      return
    }

    setIsLoading(true)
    setError(null)

    try {
      await authService.verifyOtp(email, otp)
      toast.success('Account verified successfully! You can now sign in.')
      router.push('/login')
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Verification failed. Please check your code.'))
    } finally {
      setIsLoading(false)
    }
  }

  async function handleResend() {
    setIsResending(true)
    setError(null)
    try {
      await authService.resendOtp(email)
      toast.success('A new code has been sent to your email.')
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Failed to resend code.'))
    } finally {
      setIsResending(false)
    }
  }

  return (
    <AuthShell
      headline={
        <>
          Almost in.
          <br />
          Check your <span className="text-[var(--color-brand-300)]">inbox</span>.
        </>
      }
      description="Enter the one-time code we emailed you so we know the address is yours."
    >
      <div className="flex flex-col gap-7">
        <div className="flex flex-col gap-2">
          <Link
            href="/register"
            className="flex w-fit items-center text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="mr-1 size-4" />
            Back to register
          </Link>
          <p className="font-mono text-xs font-medium uppercase tracking-[0.1em] text-[var(--color-text-brand)]">
            Step 2 of 2
          </p>
          <h2 className="font-display text-[length:var(--font-size-2xl)] font-medium leading-tight tracking-tight">
            Verify your email
          </h2>
          <p className="text-muted-foreground">
            We&apos;ve sent a code to <span className="font-medium text-foreground">{email}</span>
          </p>
        </div>

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
            <Label htmlFor="otp">Verification code</Label>
            <Input
              id="otp"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="000000"
              required
              maxLength={6}
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              className="h-14 text-center font-mono text-2xl tracking-[0.5em]"
            />
          </div>
          <Button type="submit" variant="brand" size="xl" className="mt-1 h-12 w-full text-base" disabled={isLoading || otp.length !== 6}>
            {isLoading ? <Loader2 className="size-4 animate-spin" /> : null}
            Verify account
          </Button>
        </form>

        <p className="border-t pt-6 text-sm text-muted-foreground">
          Didn&apos;t receive the code?{' '}
          <button
            type="button"
            onClick={handleResend}
            disabled={isResending}
            className="font-medium text-[var(--color-text-link)] hover:underline disabled:opacity-50"
          >
            {isResending ? 'Sending...' : 'Resend code'}
          </button>
        </p>
      </div>
    </AuthShell>
  )
}

export default function VerifyOtpPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center"><Loader2 className="animate-spin text-primary" /></div>}>
      <VerifyOtpContent />
    </Suspense>
  )
}
