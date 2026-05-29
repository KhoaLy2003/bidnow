import type { Metadata } from 'next'
import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export const metadata: Metadata = {
  title: 'About BidNow',
  description:
    "Vietnam's transparent, accessible, and fair auction platform — built around competitive bidding.",
}

const STATS = [
  { value: '12,400+', label: 'Auctions completed' },
  { value: '₫4.2B', label: 'GMV last quarter' },
  { value: '98.6%', label: 'Settlement on time' },
  { value: '2026', label: 'Founded · HCMC' },
] as const

const VALUES = [
  {
    icon: '◐',
    title: 'Transparency',
    body: 'Every bid is visible. Every result is final. Prices set by the market, not by us.',
  },
  {
    icon: '✓',
    title: 'Trust',
    body: 'Verified identities, escrowed payments, and Buyer Protection on every qualifying transaction.',
  },
  {
    icon: '○',
    title: 'Accessibility',
    body: 'Designed to work on any screen, any network condition, for users of every technical experience.',
  },
  {
    icon: '★',
    title: 'Accountability',
    body: "When things go wrong — we respond quickly, investigate fairly, and make it right.",
  },
] as const

const TIMELINE = [
  { yr: '2025', q: 'Q3', title: 'Platform founded', body: 'Engineering and product team forms in Ho Chi Minh City.' },
  { yr: '2025', q: 'Q4', title: 'Closed beta', body: 'First 200 users invited; 38 auctions completed.' },
  { yr: '2026', q: 'Q1', title: 'Public beta launch', body: 'Core auction, bidding, and payment features go live.' },
  { yr: '2026', q: 'Q2', title: 'VNPAY integration', body: 'Localized payment processing for Vietnamese banks and cards.' },
  { yr: '2026', q: 'Q2', title: 'Buyer Protection', body: 'Escrow-backed guarantee on every verified-seller transaction.' },
] as const

export default function AboutPage() {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        {/* Hero */}
        <div className="mx-auto w-full px-6" style={{ maxWidth: 1200, paddingTop: 80, paddingBottom: 48 }}>
          <div className="flex flex-col gap-5">
            <span
              className="font-mono font-medium uppercase"
              style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
            >
              About BidNow
            </span>
            <h1
              className="font-display font-medium"
              style={{
                margin: 0,
                fontSize: 'clamp(40px, 6vw, 72px)',
                lineHeight: 1.0,
                letterSpacing: '-0.025em',
                color: 'var(--color-text-primary)',
                maxWidth: 1020,
              }}
            >
              Auctions that are{' '}
              <em style={{ color: 'var(--color-brand-600)', fontStyle: 'normal' }}>transparent</em>,
              <br />
              accessible, and fair.
            </h1>
            <p
              style={{
                fontSize: 19,
                lineHeight: 1.5,
                color: 'var(--color-text-secondary)',
                maxWidth: 720,
                marginTop: 8,
                marginBottom: 0,
              }}
            >
              BidNow is Vietnam&apos;s modern online auction platform. We exist because competitive bidding
              is the most honest way to determine market value — and because the country deserved a
              marketplace built to prove it.
            </p>
          </div>
        </div>

        {/* Stats strip */}
        <div className="mx-auto w-full px-6 pb-20" style={{ maxWidth: 1200 }}>
          <div
            className="grid grid-cols-2 md:grid-cols-4 gap-8 py-8"
            style={{
              borderTop: '1px solid var(--color-border-default)',
              borderBottom: '1px solid var(--color-border-default)',
            }}
          >
            {STATS.map(({ value, label }) => (
              <div key={label} className="flex flex-col gap-2">
                <span
                  className="font-mono font-medium"
                  style={{ fontSize: 64, lineHeight: 1, letterSpacing: '-0.025em', color: 'var(--color-text-primary)' }}
                >
                  {value}
                </span>
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.06em' }}
                >
                  {label}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Mission */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1100 }}>
          <div className="flex flex-col gap-5" style={{ maxWidth: 820 }}>
            <span
              className="font-mono font-medium uppercase"
              style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
            >
              Our mission
            </span>
            <h2
              className="font-display font-medium"
              style={{
                margin: 0,
                fontSize: 44,
                lineHeight: 1.15,
                letterSpacing: '-0.018em',
                color: 'var(--color-text-primary)',
              }}
            >
              Make auction-based commerce transparent, accessible, and fair for everyone in Vietnam.
            </h2>
            <p style={{ fontSize: 17, lineHeight: 1.65, color: 'var(--color-text-secondary)', maxWidth: 720, margin: 0 }}>
              Traditional auction houses were inaccessible to most people. Informal auctions on social
              platforms were rife with fraud, fake listings, and zero buyer protection. We built BidNow
              as a purpose-built alternative — one that combines the excitement of competitive bidding
              with the safeguards users deserve.
            </p>
            <div className="flex flex-wrap gap-2 mt-1">
              {['VNPAY-integrated', 'Anti-sniping', 'Escrow-backed', 'Identity-verified'].map((t) => (
                <span
                  key={t}
                  className="font-mono uppercase"
                  style={{
                    fontSize: 10.5,
                    letterSpacing: '0.06em',
                    background: 'var(--color-brand-50)',
                    color: 'var(--color-brand-700)',
                    border: '1px solid hsl(239,87%,83%)',
                    borderRadius: 4,
                    padding: '3px 8px',
                  }}
                >
                  {t}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Values */}
        <div
          className="py-20"
          style={{ background: 'var(--color-bg-elevated)' }}
        >
          <div className="mx-auto w-full px-6" style={{ maxWidth: 1200 }}>
            <div className="flex flex-col gap-8">
              <div className="flex flex-wrap items-baseline justify-between gap-4">
                <div className="flex flex-col gap-2">
                  <span
                    className="font-mono font-medium uppercase"
                    style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
                  >
                    What we believe
                  </span>
                  <h2
                    className="font-display font-medium"
                    style={{ margin: 0, fontSize: 40, letterSpacing: '-0.018em', color: 'var(--color-text-primary)' }}
                  >
                    Four values, in order of importance.
                  </h2>
                </div>
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.06em' }}
                >
                  FROM OUR FOUNDING DOC · MAY 2026
                </span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                {VALUES.map((v, i) => (
                  <div
                    key={v.title}
                    className="flex flex-col gap-4 rounded-xl p-6"
                    style={{
                      border: '1px solid var(--color-border-default)',
                      background: 'var(--color-bg-base)',
                    }}
                  >
                    <div className="flex items-center justify-between">
                      <div
                        className="flex items-center justify-center rounded-lg text-xl"
                        style={{
                          width: 44,
                          height: 44,
                          background: 'var(--color-brand-50)',
                          color: 'var(--color-brand-700)',
                        }}
                      >
                        {v.icon}
                      </div>
                      <span
                        className="font-mono"
                        style={{ fontSize: 11, color: 'var(--color-text-tertiary)' }}
                      >
                        0{i + 1}
                      </span>
                    </div>
                    <div className="flex flex-col gap-2">
                      <span
                        className="font-display font-medium"
                        style={{ fontSize: 20, color: 'var(--color-text-primary)' }}
                      >
                        {v.title}
                      </span>
                      <span className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.55 }}>
                        {v.body}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Timeline */}
        <div className="mx-auto w-full px-6 py-24" style={{ maxWidth: 1200 }}>
          <div className="flex flex-col gap-8">
            <div className="flex flex-col gap-2">
              <span
                className="font-mono font-medium uppercase"
                style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
              >
                Milestones
              </span>
              <h2
                className="font-display font-medium"
                style={{ margin: 0, fontSize: 40, letterSpacing: '-0.018em', color: 'var(--color-text-primary)' }}
              >
                A short, recent history.
              </h2>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-8 relative pt-9">
              {/* Rail */}
              <div
                className="absolute left-0 right-0 top-3 hidden lg:block"
                style={{ height: 1, background: 'var(--color-border-default)' }}
              />
              {TIMELINE.map((m, i) => (
                <div key={m.title} className="flex flex-col gap-3 relative">
                  <div
                    className="absolute hidden lg:block"
                    style={{
                      top: -28,
                      left: 0,
                      width: 12,
                      height: 12,
                      borderRadius: '50%',
                      background: i === TIMELINE.length - 1 ? 'var(--color-brand-600)' : 'var(--color-bg-base)',
                      border: '2px solid var(--color-brand-600)',
                    }}
                  />
                  <span
                    className="font-mono uppercase"
                    style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.06em' }}
                  >
                    {m.yr} · {m.q}
                  </span>
                  <span
                    className="font-display font-medium"
                    style={{ fontSize: 18, color: 'var(--color-text-primary)' }}
                  >
                    {m.title}
                  </span>
                  <span className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.55 }}>
                    {m.body}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Start bidding CTA */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1200 }}>
          <div
            className="rounded-xl p-10 flex flex-wrap items-center justify-between gap-6"
            style={{ background: 'var(--color-brand-800)', border: '1px solid var(--color-brand-800)' }}
          >
            <div className="flex flex-col gap-3" style={{ maxWidth: 520 }}>
              <span
                className="font-mono font-medium uppercase"
                style={{ fontSize: 11, color: 'rgba(255,255,255,0.6)', letterSpacing: '0.1em' }}
              >
                Ready to bid?
              </span>
              <h3
                className="font-display font-medium"
                style={{ margin: 0, fontSize: 32, color: '#FFF', letterSpacing: '-0.015em' }}
              >
                Your first auction is one minute away.
              </h3>
              <p className="text-sm" style={{ color: 'rgba(255,255,255,0.7)', lineHeight: 1.6, margin: 0 }}>
                Create a free account, verify your identity, and start bidding on live auctions today.
                No fees until you win.
              </p>
            </div>
            <div className="flex gap-3">
              <Link
                href="/register"
                className={cn(buttonVariants({ size: 'xl' }), 'bg-white text-[var(--color-brand-900)] hover:bg-white/90 border-white')}
              >
                Create free account
              </Link>
              <Link
                href="/auctions"
                className={cn(buttonVariants({ variant: 'ghost', size: 'xl' }), 'text-white border border-white/30 hover:bg-white/10 hover:text-white')}
              >
                Browse auctions
              </Link>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
