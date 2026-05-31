import type { Metadata } from 'next'
import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { buttonVariants } from '@/components/ui/button'

export const metadata: Metadata = {
  title: 'How It Works',
  description:
    'Six steps from sign-up to your first won auction. Anti-sniping, verified identities, escrow-backed payments.',
}

const STEPS = [
  {
    n: '01',
    title: 'Create your account',
    body: 'Sign up with your full name, email, and a strong password. Confirm your email with the one-time code we send you. Done — no payment info required to browse.',
    bullets: ['Email + OTP verification', 'Active immediately', 'Browse without committing'],
    badge: '5 min',
  },
  {
    n: '02',
    title: 'Verify your identity',
    body: 'Bidding requires identity verification. Upload your CCCD, CMND, or passport and complete a live selfie. Reviewed within 1–2 business days — required by Vietnamese e-commerce law.',
    bullets: ['Government photo ID', 'Live selfie check', '1–2 business days'],
    badge: 'Required to bid',
  },
  {
    n: '03',
    title: 'Browse & watch',
    body: 'Use category filters, price range, and Ending Soon. Add items to your Watchlist to track them without bidding. Every listing shows the current bid, bid count, time left, and seller rating.',
    bullets: ['Filters & saved searches', 'Watchlist', 'Live ending-soon view'],
    badge: 'No commitment',
  },
  {
    n: '04',
    title: 'Place a bid',
    body: 'Register a deposit on qualifying auctions — automatically refunded if you don\'t win. Enter your amount and confirm. Bids are binding the moment they\'re placed.',
    bullets: ['Refundable deposit', 'Server-side timestamps', 'Anti-sniping protection'],
    badge: 'Binding offer',
  },
  {
    n: '05',
    title: 'Win & pay',
    body: 'If you hold the high bid at close, you have 48 hours to complete payment via Wallet or VNPAY. The seller confirms and ships within their stated handling time.',
    bullets: ['48 hours to pay', 'Wallet or VNPAY', 'Notifications + tracking'],
    badge: '48 h window',
  },
  {
    n: '06',
    title: 'Or sell your own',
    body: 'Complete seller verification, list your item with at least 3 original photos, set a starting price, and submit for review. Listings go live within 4 business hours.',
    bullets: ['Seller verification', '3+ original photos', 'Live within 4 hours'],
    badge: 'Earn',
  },
]

export default function HowItWorksPage() {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        {/* Hero */}
        <div className="mx-auto w-full px-6" style={{ maxWidth: 1100, paddingTop: 72, paddingBottom: 56 }}>
          <div className="flex flex-col gap-4">
            <span
              className="font-mono font-medium uppercase"
              style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
            >
              GETTING STARTED
            </span>
            <h1
              className="font-display font-medium"
              style={{
                margin: 0,
                fontSize: 56,
                lineHeight: 1.05,
                letterSpacing: '-0.02em',
                color: 'var(--color-text-primary)',
                maxWidth: 880,
              }}
            >
              How BidNow works.
            </h1>
            <p style={{ fontSize: 18, lineHeight: 1.55, color: 'var(--color-text-secondary)', maxWidth: 720, margin: 0 }}>
              Six steps from sign-up to your first won auction. Built around competitive bidding,
              verified identities, and escrow-backed payments — no surprises.
            </p>
            <div className="flex flex-wrap gap-3 mt-2">
              <Link
                href="/register"
                className={buttonVariants({ variant: 'brand', size: 'xl' })}
              >
                Create an account
              </Link>
              <Link
                href="/auctions"
                className={buttonVariants({ variant: 'outline', size: 'xl' })}
              >
                Browse auctions
              </Link>
              <span className="flex items-center gap-2 text-xs text-muted-foreground self-center ml-2">
                <span>⏱</span>
                <span>About 8 minutes to read · 5 minutes to sign up</span>
              </span>
            </div>
          </div>
        </div>

        {/* Auctions explainer */}
        <div className="mx-auto w-full px-6 pb-14" style={{ maxWidth: 1100 }}>
          <div
            className="rounded-xl p-8"
            style={{
              border: '1px solid var(--color-border-default)',
              background: 'var(--color-bg-elevated)',
            }}
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
              <div className="flex flex-col gap-3">
                <span
                  className="font-mono font-medium uppercase"
                  style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
                >
                  How auctions work
                </span>
                <h2
                  className="font-display font-medium"
                  style={{ margin: 0, fontSize: 28, letterSpacing: '-0.01em', color: 'var(--color-text-primary)' }}
                >
                  Timed competitive bidding — with two safeguards.
                </h2>
                <p className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.65 }}>
                  Each listing has a start, an end, and a starting bid. The highest bid when the timer
                  reaches zero wins. Two rules protect every participant from the games auctions usually attract.
                </p>
              </div>
              <div className="flex flex-col gap-4">
                {[
                  {
                    icon: '⏱',
                    tag: 'Anti-sniping',
                    title: 'Bids in the final 3 min reset the clock.',
                    body: 'If anyone bids in the last 3 minutes, the timer resets to 3:00. The auction only closes when a full 3 minutes pass with no new bid. No last-second steals.',
                  },
                  {
                    icon: '✓',
                    tag: 'Reserve',
                    title: 'Reserves are disclosed up front.',
                    body: 'Sellers may set a minimum acceptable price. If bidding doesn\'t reach it, the sale isn\'t binding. Whether a reserve exists is always shown on the listing.',
                  },
                ].map((item) => (
                  <div key={item.tag} className="flex items-start gap-3">
                    <div
                      className="flex items-center justify-center shrink-0 rounded-lg text-xl"
                      style={{
                        width: 44,
                        height: 44,
                        background: 'var(--color-brand-50)',
                        color: 'var(--color-brand-700)',
                      }}
                    >
                      {item.icon}
                    </div>
                    <div className="flex flex-col gap-1 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium" style={{ color: 'var(--color-text-primary)' }}>
                          {item.title}
                        </span>
                        <span
                          className="font-mono uppercase"
                          style={{
                            fontSize: 10.5,
                            letterSpacing: '0.06em',
                            background: 'var(--color-brand-50)',
                            color: 'var(--color-brand-700)',
                            border: '1px solid hsl(239,87%,83%)',
                            borderRadius: 4,
                            padding: '2px 7px',
                          }}
                        >
                          {item.tag}
                        </span>
                      </div>
                      <span className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.55 }}>
                        {item.body}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Six steps */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1100, paddingTop: 24 }}>
          <div className="flex flex-col gap-6">
            <div className="flex items-baseline justify-between">
              <div className="flex flex-col gap-2">
                <span
                  className="font-mono font-medium uppercase"
                  style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
                >
                  The 6-step journey
                </span>
                <h2
                  className="font-display font-medium"
                  style={{ margin: 0, fontSize: 32, letterSpacing: '-0.015em', color: 'var(--color-text-primary)' }}
                >
                  From sign-up to shipped.
                </h2>
              </div>
              <span
                className="font-mono uppercase hidden md:block"
                style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.06em' }}
              >
                READ ~ 8 MIN · UPDATED MAY 2026
              </span>
            </div>

            <div className="flex flex-col mt-3">
              {STEPS.map((step, i) => (
                <div key={step.n} className="flex" style={{ gap: 0, alignItems: 'stretch' }}>
                  {/* Rail */}
                  <div className="flex flex-col items-center shrink-0" style={{ width: 100, paddingTop: 4 }}>
                    <span
                      className="font-mono font-medium"
                      style={{ fontSize: 28, color: 'var(--color-brand-600)', letterSpacing: '-0.02em', lineHeight: 1 }}
                    >
                      {step.n}
                    </span>
                    {i < STEPS.length - 1 && (
                      <div
                        style={{
                          flex: 1,
                          width: 1,
                          marginTop: 14,
                          marginBottom: 14,
                          borderLeft: '1px dashed var(--color-border-strong)',
                        }}
                      />
                    )}
                  </div>

                  {/* Content */}
                  <div
                    className="flex flex-col gap-4 flex-1"
                    style={{ paddingBottom: i < STEPS.length - 1 ? 48 : 0 }}
                  >
                    <div className="flex items-center gap-3 justify-between">
                      <h3
                        className="font-display font-medium"
                        style={{
                          margin: 0,
                          fontSize: 28,
                          letterSpacing: '-0.01em',
                          color: 'var(--color-text-primary)',
                        }}
                      >
                        {step.title}
                      </h3>
                      <span
                        className="font-mono uppercase shrink-0"
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
                        {step.badge}
                      </span>
                    </div>
                    <p
                      style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--color-text-secondary)', maxWidth: 720, margin: 0 }}
                    >
                      {step.body}
                    </p>
                    <div
                      className="rounded-xl p-4.5"
                      style={{
                        border: '1px solid var(--color-border-default)',
                        background: 'var(--color-bg-base)',
                        padding: '18px',
                      }}
                    >
                      <div className="flex flex-col gap-2">
                        {step.bullets.map((b) => (
                          <div key={b} className="flex items-start gap-2 text-sm" style={{ color: 'var(--color-text-primary)' }}>
                            <span
                              className="flex items-center justify-center shrink-0 rounded-full text-xs"
                              style={{
                                width: 18,
                                height: 18,
                                background: 'var(--color-auction-won-bg)',
                                color: 'var(--color-auction-won-text)',
                                marginTop: 1,
                                fontSize: 11,
                              }}
                            >
                              ✓
                            </span>
                            <span>{b}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* CTA */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1100 }}>
          <div
            className="rounded-xl p-9"
            style={{ border: '1px solid var(--color-border-default)', background: 'var(--color-bg-base)' }}
          >
            <div className="flex flex-wrap items-center justify-between gap-6">
              <div className="flex flex-col gap-2" style={{ maxWidth: 540 }}>
                <h3
                  className="font-display font-medium"
                  style={{ margin: 0, fontSize: 28, letterSpacing: '-0.015em', color: 'var(--color-text-primary)' }}
                >
                  Ready to try your first bid?
                </h3>
                <p className="text-sm" style={{ color: 'var(--color-text-secondary)', lineHeight: 1.6, margin: 0 }}>
                  You can sign up in five minutes, watch auctions risk-free, and only verify when you
                  decide to bid. No payment info required.
                </p>
              </div>
              <div className="flex gap-3">
                <Link
                  href="/register"
                  className={buttonVariants({ variant: 'brand', size: 'xl' })}
                >
                  Create your account →
                </Link>
                <Link
                  href="/faq"
                  className={buttonVariants({ variant: 'outline', size: 'xl' })}
                >
                  Read the FAQ
                </Link>
              </div>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
