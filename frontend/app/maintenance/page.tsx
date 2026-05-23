import type { Metadata } from 'next'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'

export const metadata: Metadata = { title: 'Scheduled Maintenance · BidNow' }

export default function MaintenancePage() {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        <div
          className="mx-auto w-full max-w-[var(--container-sm)] px-6"
          style={{ paddingTop: 140, paddingBottom: 160 }}
        >
          <div className="flex flex-col gap-6 items-start">
            {/* Tag */}
            <span
              className="inline-flex items-center font-mono uppercase"
              style={{
                fontSize: 10.5,
                letterSpacing: '0.06em',
                fontWeight: 500,
                background: 'var(--color-auction-ending-bg)',
                color: 'var(--color-auction-ending-text)',
                border: '1px solid var(--color-auction-ending-border)',
                borderRadius: 4,
                padding: '3px 8px',
                gap: 5,
              }}
            >
              ⚙ Scheduled maintenance
            </span>

            <h1
              className="font-display font-medium"
              style={{
                margin: 0,
                fontSize: 44,
                lineHeight: 1.1,
                letterSpacing: '-0.02em',
                color: 'var(--color-text-primary)',
                maxWidth: 660,
              }}
            >
              BidNow is briefly offline for upgrades.
            </h1>

            <p
              style={{
                fontSize: 16,
                lineHeight: 1.6,
                color: 'var(--color-text-secondary)',
                maxWidth: 560,
                margin: 0,
              }}
            >
              No auctions are currently live. Bids placed before the window are safely stored and
              will resume the moment we&apos;re back.
            </p>

            <p
              className="font-mono"
              style={{ marginTop: 4, fontSize: 11.5, color: 'var(--color-text-tertiary)' }}
            >
              Follow updates at{' '}
              <a
                href="https://status.bidnow.vn"
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: 'var(--color-text-primary)', textDecoration: 'underline' }}
              >
                status.bidnow.vn
              </a>
            </p>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
