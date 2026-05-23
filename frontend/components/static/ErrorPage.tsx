import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { buttonVariants } from '@/components/ui/button'

interface ErrorPageProps {
  code: string
  accentColor: string
  statusLabel: string
  heading: string
  body: string
  primaryAction: { label: string; href: string }
  secondaryAction?: { label: string; href: string }
  requestId?: string
}

export function ErrorPage({
  code,
  accentColor,
  statusLabel,
  heading,
  body,
  primaryAction,
  secondaryAction,
  requestId,
}: ErrorPageProps) {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        <div
          className="mx-auto w-full max-w-[var(--container-sm)] px-6"
          style={{ paddingTop: 140, paddingBottom: 160 }}
        >
          <div className="flex flex-col gap-6 items-start">
            {/* Status label */}
            <div
              className="flex items-center gap-2 font-mono uppercase tracking-widest"
              style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}
            >
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: 2,
                  background: accentColor,
                  flexShrink: 0,
                  display: 'inline-block',
                }}
              />
              <span>{statusLabel}</span>
            </div>

            {/* Error code */}
            <div
              className="font-mono font-medium leading-none select-none"
              style={{
                fontSize: 'clamp(100px, 18vw, 180px)',
                letterSpacing: '-0.06em',
                color: 'var(--color-text-primary)',
                marginTop: -8,
              }}
            >
              {code}
            </div>

            {/* Heading + body */}
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
                {heading}
              </h1>
              <p
                style={{
                  fontSize: 16,
                  lineHeight: 1.6,
                  color: 'var(--color-text-secondary)',
                  margin: 0,
                }}
              >
                {body}
              </p>
            </div>

            {/* Actions */}
            <div className="flex flex-wrap gap-3 mt-2">
              <Link href={primaryAction.href} className={buttonVariants({ variant: 'brand', size: 'xl' })}>
                {primaryAction.label}
              </Link>
              {secondaryAction && (
                <Link href={secondaryAction.href} className={buttonVariants({ variant: 'outline', size: 'xl' })}>
                  {secondaryAction.label}
                </Link>
              )}
            </div>

            {/* Request ID */}
            {requestId && (
              <p
                className="font-mono"
                style={{
                  marginTop: 8,
                  fontSize: 11.5,
                  color: 'var(--color-text-tertiary)',
                }}
              >
                {requestId}
              </p>
            )}
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
