'use client'

import { useState, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'

import { authService } from '@/services/auth.service'
import { toast } from 'sonner'

export default function RegisterPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
    } catch (err: any) {
      setError(err.message || 'Registration failed. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 flex flex-col gap-6">
        <div className="text-center">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Create account</h1>
          <p className="mt-1 text-sm text-muted-foreground">Join BidNow and start bidding</p>
        </div>

        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-lg border border-destructive/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Display name</Label>
            <Input id="name" name="name" type="text" placeholder="Your name" required maxLength={100} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" name="email" type="email" placeholder="you@example.com" required />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input id="password" name="password" type="password" placeholder="8+ characters" required minLength={8} />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading}>
            {isLoading && <Loader2 className="size-4 animate-spin mr-2" />}
            Create account
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link href="/login" className="text-[var(--color-text-link)] hover:underline font-medium">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
