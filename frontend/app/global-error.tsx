'use client'

import Link from 'next/link'
import { useEffect } from 'react'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { Button, buttonVariants } from '@/components/ui/button'

export default function GlobalError({
  error,
  reset,
}: {
  readonly error: Error & { digest?: string }
  readonly reset: () => void
}) {
  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <>
      <Header />
      <main className="flex-1">
        <div
          className="mx-auto w-full max-w-[var(--container-sm)] px-6"
          style={{ paddingTop: 140, paddingBottom: 160 }}
        >
          <div className="flex flex-col gap-6 items-start">
            <div
              className="flex items-center gap-2 font-mono uppercase tracking-widest"
              style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}
            >
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: 2,
                  background: 'var(--color-danger-default)',
                  flexShrink: 0,
                  display: 'inline-block',
                }}
              />
              <span>HTTP · 500 Internal Server Error</span>
            </div>

            <div
              className="font-mono font-medium leading-none select-none"
              style={{
                fontSize: 'clamp(100px, 18vw, 180px)',
                letterSpacing: '-0.06em',
                color: 'var(--color-text-primary)',
                marginTop: -8,
              }}
            >
              500
            </div>

            <div className="flex flex-col gap-3 mt-2" style={{ maxWidth: 560 }}>
              <h1
                className="font-display font-medium"
                style={{
                  margin: 0,
                  fontSize: 38,
                  lineHeight: 1.1,
                  letterSpacing: '-0.018em',
                  color: 'var(--color-text-primary)',
                }}
              >
                Something went wrong on our end.
              </h1>
              <p style={{ fontSize: 16, lineHeight: 1.6, color: 'var(--color-text-secondary)', margin: 0 }}>
                We&apos;ve logged this and our team is investigating. Your bids and wallet balance are safe.
              </p>
            </div>

            <div className="flex flex-wrap gap-3 mt-2">
              <Button variant="brand" size="xl" onClick={reset}>↻ Try again</Button>
              <Link href="/contact" className={buttonVariants({ variant: 'outline', size: 'xl' })}>
                Contact support
              </Link>
            </div>

            {error.digest && (
              <p className="font-mono mt-2" style={{ fontSize: 11.5, color: 'var(--color-text-tertiary)' }}>
                REQUEST · {error.digest}
              </p>
            )}
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
