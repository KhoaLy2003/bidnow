'use client'

import Link from 'next/link'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'

export function CTASection() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  if (isAuthenticated) return null

  return (
    <section className="rounded-2xl bg-gradient-to-br from-[var(--color-brand-50)] to-[var(--color-brand-100)] border border-[var(--color-brand-200)] px-8 py-16 text-center mb-12">
      <h2 className="font-display font-medium text-[length:var(--font-size-2xl)] text-[var(--color-brand-900)] mb-3">
        Ready to find great deals?
      </h2>
      <p className="text-sm text-[var(--color-text-secondary)] mb-8 max-w-sm mx-auto">
        Join thousands of buyers and sellers on BidNow.
      </p>
      <div className="flex flex-wrap justify-center gap-3">
        <Button
          variant="brand"
          className="h-11"
          render={<Link href="/register" />}
          nativeButton={false}
        >
          Sign Up Free
        </Button>
        <Button
          variant="outline"
          className="h-11"
          render={<Link href="/auctions" />}
          nativeButton={false}
        >
          Browse Auctions
        </Button>
      </div>
    </section>
  )
}
