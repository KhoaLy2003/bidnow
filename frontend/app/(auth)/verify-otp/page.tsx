'use client'

import { useState, useEffect, type FormEvent, Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Loader2, ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authService } from '@/services/auth.service'
import { toast } from 'sonner'

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
    } catch (err: any) {
      setError(err.message || 'Verification failed. Please check your code.')
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
    } catch (err: any) {
      setError(err.message || 'Failed to resend code.')
    } finally {
      setIsResending(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <Link 
            href="/register" 
            className="flex items-center text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="size-4 mr-1" />
            Back to register
          </Link>
          <div className="text-center">
            <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Verify email</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              We&apos;ve sent a code to <span className="font-medium text-foreground">{email}</span>
            </p>
          </div>
        </div>

        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-lg border border-destructive/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="otp">Verification Code</Label>
            <Input 
              id="otp" 
              type="text" 
              placeholder="000000" 
              required 
              maxLength={6}
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              className="text-center text-2xl tracking-[0.5em] font-mono h-14"
            />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading || otp.length !== 6}>
            {isLoading && <Loader2 className="size-4 animate-spin mr-2" />}
            Verify Account
          </Button>
        </form>

        <div className="text-center text-sm">
          <p className="text-muted-foreground">
            Didn&apos;t receive the code?{' '}
            <button 
              onClick={handleResend}
              disabled={isResending}
              className="text-[var(--color-text-link)] hover:underline font-medium disabled:opacity-50"
            >
              {isResending ? 'Sending...' : 'Resend code'}
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}

export default function VerifyOtpPage() {
  return (
    <Suspense fallback={<div className="flex items-center justify-center min-h-[400px]"><Loader2 className="animate-spin text-primary" /></div>}>
      <VerifyOtpContent />
    </Suspense>
  )
}
