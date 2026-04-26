'use client'

import type { Metadata } from 'next'
import { useState, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { Input }  from '@/components/ui/input'
import { Label }  from '@/components/ui/label'
import { Button } from '@/components/ui/button'

export default function LoginPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsLoading(true)
    await new Promise<void>((r) => setTimeout(r, 800))
    setIsLoading(false)
    router.push('/')
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 shadow-sm flex flex-col gap-6">
        <div className="text-center">
          <h1 className="font-display font-bold text-[length:var(--font-size-xl)]">Welcome back</h1>
          <p className="mt-1 text-sm text-muted-foreground">Sign in to your account</p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" placeholder="you@example.com" required />
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link href="/forgot-password" className="text-xs text-[var(--color-text-link)] hover:underline">
                Forgot password?
              </Link>
            </div>
            <Input id="password" type="password" placeholder="••••••••" required />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading}>
            {isLoading && <Loader2 className="size-4 animate-spin" />}
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
