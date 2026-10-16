'use client'

import { useState, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { AlertCircle, ArrowRight, Loader2, Mail } from 'lucide-react'
import { AuthShell } from '@/components/auth/AuthShell'
import { PasswordInput } from '@/components/auth/PasswordInput'
import { PasswordStrengthMeter } from '@/components/auth/PasswordStrengthMeter'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'

import { authService } from '@/services/auth.service'
import { toast } from 'sonner'
import { getErrorMessage } from '@/lib/utils'
import { MIN_PASSWORD_LENGTH } from '@/lib/password-strength'

const STEPS = [
  { title: 'Create your account', body: 'Name, email and a password. Under a minute.' },
  { title: 'Confirm with a code', body: 'We email you a one-time code to verify it is you.' },
  { title: 'Place your first bid', body: 'Auto-bid up to your max, deposit refunded if you lose.' },
] as const

export default function RegisterPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [password, setPassword] = useState('')

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsLoading(true)
    setError(null)

    const formData = new FormData(e.currentTarget)
    const name = formData.get('name') as string
    const email = formData.get('email') as string
    const password = formData.get('password') as string

    try {
      await authService.register(name, email, password)
      toast.success('Registration successful! Please check your email for OTP.')
      router.push(`/verify-otp?email=${encodeURIComponent(email)}`)
    } catch (err: unknown) {
      setError(getErrorMessage(err, 'Registration failed. Please try again.'))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      headline={
        <>
          Take a seat
          <br />
          before the <span className="text-[var(--color-brand-300)]">clock</span>
          <br />
          runs out.
        </>
      }
      description="Free to join. Bid on watches, instruments and collectibles, or list your own lots."
      aside={
        <ol className="flex flex-col gap-5">
          {STEPS.map(({ title, body }, i) => (
            <li key={title} className="flex items-start gap-4">
              <span
                className={
                  i === 0
                    ? 'grid size-7 shrink-0 place-items-center rounded-full bg-white font-mono text-sm font-medium text-[var(--color-brand-700)]'
                    : 'grid size-7 shrink-0 place-items-center rounded-full border border-white/40 font-mono text-sm font-medium text-[var(--color-neutral-300)]'
                }
              >
                {i + 1}
              </span>
              <span className="flex flex-col gap-0.5">
                <span className="font-medium text-white">{title}</span>
                <span className="text-sm text-muted-foreground">{body}</span>
              </span>
            </li>
          ))}
        </ol>
      }
    >
      <div className="flex flex-col gap-7">
        <div className="flex flex-col gap-2">
          <p className="font-mono text-xs font-medium uppercase tracking-[0.1em] text-[var(--color-text-brand)]">
            Step 1 of 2
          </p>
          <h2 className="font-display text-[length:var(--font-size-2xl)] font-medium leading-tight tracking-tight">
            Create your account
          </h2>
          <p className="text-muted-foreground">
            Already have one?{' '}
            <Link href="/login" className="font-medium text-[var(--color-text-link)] hover:underline">
              Sign in
            </Link>
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
            <Label htmlFor="name">Display name</Label>
            <Input
              id="name"
              name="name"
              type="text"
              autoComplete="nickname"
              placeholder="How bidders will see you"
              required
              maxLength={100}
              className="h-12"
            />
          </div>
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
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="password">Password</Label>
            <PasswordInput
              id="password"
              name="password"
              autoComplete="new-password"
              placeholder={`${MIN_PASSWORD_LENGTH}+ characters`}
              required
              minLength={MIN_PASSWORD_LENGTH}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-describedby="password-hint"
            />
            <PasswordStrengthMeter password={password} id="password-hint" />
          </div>
          <Button type="submit" variant="brand" size="xl" className="mt-1 h-12 w-full text-base" disabled={isLoading}>
            {isLoading ? <Loader2 className="size-4 animate-spin" /> : null}
            Create account
            {!isLoading && <ArrowRight className="size-4" />}
          </Button>
        </form>

        <p className="flex gap-2.5 border-t pt-6 text-sm leading-normal text-muted-foreground">
          <Mail className="mt-0.5 size-[18px] shrink-0" aria-hidden="true" />
          Next, we email you a one-time code to confirm your address.
        </p>
      </div>
    </AuthShell>
  )
}
