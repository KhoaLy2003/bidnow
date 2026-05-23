import type { Metadata } from 'next'
import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'

export const metadata: Metadata = {
  title: 'Contact Us',
  description: 'Get in touch with the BidNow support team.',
}

const CATEGORIES = ['Bidding', 'Account', 'Payments', 'Disputes', 'Other'] as const

export default function ContactPage() {
  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        {/* Hero */}
        <div
          className="mx-auto w-full px-6 text-center flex flex-col items-center gap-4"
          style={{ maxWidth: 720, paddingTop: 80, paddingBottom: 48 }}
        >
          <span
            className="font-mono font-medium uppercase"
            style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
          >
            Talk to us
          </span>
          <h1
            className="font-display font-medium"
            style={{
              margin: 0,
              fontSize: 56,
              lineHeight: 1.05,
              letterSpacing: '-0.02em',
              color: 'var(--color-text-primary)',
            }}
          >
            We&apos;re here. Real humans.
          </h1>
          <p style={{ fontSize: 17, lineHeight: 1.55, color: 'var(--color-text-secondary)', maxWidth: 560, margin: 0 }}>
            Found a bug, lost access to your account, or just want to share feedback? A real person
            on our team will reply.
          </p>
          <span
            className="inline-flex items-center font-mono uppercase"
            style={{
              fontSize: 10.5,
              letterSpacing: '0.06em',
              background: 'var(--color-bg-elevated)',
              color: 'var(--color-text-secondary)',
              border: '1px solid var(--color-border-default)',
              borderRadius: 4,
              padding: '3px 8px',
            }}
          >
            Typical reply · 2 business hours
          </span>
        </div>

        {/* Form card */}
        <div className="mx-auto w-full px-6 pb-16" style={{ maxWidth: 720 }}>
          <div
            className="rounded-xl overflow-hidden"
            style={{ border: '1px solid var(--color-border-default)', background: 'var(--color-bg-base)' }}
          >
            <form className="flex flex-col p-9" action="#" method="POST">
              <span
                className="font-mono font-medium uppercase"
                style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
              >
                Send a message
              </span>

              <div className="grid grid-cols-2 gap-3.5 mt-4.5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                    Full name
                  </label>
                  <input
                    type="text"
                    name="name"
                    className="h-11 px-3.5 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-background text-sm focus:outline-none focus:border-[var(--color-border-focus)] transition-colors"
                    placeholder="Your full name"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                    Email
                  </label>
                  <input
                    type="email"
                    name="email"
                    className="h-11 px-3.5 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-background text-sm focus:outline-none focus:border-[var(--color-border-focus)] transition-colors"
                    placeholder="you@example.com"
                  />
                </div>
              </div>

              {/* Category */}
              <div className="flex flex-col gap-1.5 mt-3.5">
                <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                  Category
                </label>
                <div className="flex flex-wrap gap-2">
                  {CATEGORIES.map((c, i) => (
                    <label
                      key={c}
                      className="inline-flex items-center px-3 h-9 rounded-[var(--radius-md)] cursor-pointer text-sm transition-colors"
                      style={{
                        border: `1px solid ${i === 0 ? 'var(--color-brand-600)' : 'var(--color-border-default)'}`,
                        color: i === 0 ? 'var(--color-brand-700)' : 'var(--color-text-secondary)',
                        background: i === 0 ? 'var(--color-brand-50)' : 'transparent',
                        fontWeight: i === 0 ? 500 : 400,
                      }}
                    >
                      <input type="radio" name="category" value={c} className="sr-only" defaultChecked={i === 0} />
                      {c}
                    </label>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3.5 mt-3.5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                    Subject
                  </label>
                  <input
                    type="text"
                    name="subject"
                    className="h-11 px-3.5 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-background text-sm focus:outline-none focus:border-[var(--color-border-focus)] transition-colors"
                    placeholder="Brief summary"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                    Related ID{' '}
                    <span className="font-normal" style={{ color: 'var(--color-text-tertiary)' }}>
                      (optional)
                    </span>
                  </label>
                  <input
                    type="text"
                    name="related_id"
                    className="h-11 px-3.5 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-background font-mono text-sm focus:outline-none focus:border-[var(--color-border-focus)] transition-colors"
                    placeholder="A-XXXX"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-1.5 mt-3.5">
                <label className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
                  Message
                </label>
                <textarea
                  name="message"
                  rows={6}
                  className="px-3.5 py-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-background text-sm resize-none focus:outline-none focus:border-[var(--color-border-focus)] transition-colors"
                  style={{ lineHeight: 1.55 }}
                  placeholder="Describe your issue in detail…"
                />
                <p className="text-xs" style={{ color: 'var(--color-text-tertiary)' }}>
                  Maximum 5,000 characters · attach files via email if needed
                </p>
              </div>

              <div className="flex items-center gap-2 mt-3.5">
                <input
                  type="checkbox"
                  id="privacy-consent"
                  name="privacy_consent"
                  className="rounded border-[var(--color-border-strong)] accent-[var(--color-brand-600)]"
                />
                <label htmlFor="privacy-consent" className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
                  I&apos;ve read the{' '}
                  <Link href="/privacy" className="text-[var(--color-text-primary)] underline">
                    Privacy Policy
                  </Link>
                  .
                </label>
              </div>

              <button
                type="submit"
                className="mt-4.5 w-full h-12 rounded-[var(--radius-md)] bg-[var(--color-brand-600)] text-white font-medium text-sm hover:bg-[var(--color-brand-700)] transition-colors"
              >
                Send message →
              </button>

              <p className="text-xs text-center mt-3" style={{ color: 'var(--color-text-tertiary)' }}>
                We&apos;ll reply to your email · usually within 2 business hours.
              </p>
            </form>
          </div>
        </div>

        {/* Office + hours */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1100, paddingTop: 24 }}>
          <div
            className="rounded-xl overflow-hidden"
            style={{ border: '1px solid var(--color-border-default)', background: 'var(--color-bg-base)' }}
          >
            <div className="grid grid-cols-1 md:grid-cols-2">
              <div
                className="flex flex-col gap-2 p-7"
                style={{ borderRight: '1px solid var(--color-border-default)' }}
              >
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', letterSpacing: '0.1em' }}
                >
                  Headquarters
                </span>
                <div className="text-sm" style={{ color: 'var(--color-text-primary)', lineHeight: 1.65 }}>
                  BidNow Co., Ltd.
                  <br />
                  District 1, Ho Chi Minh City
                  <br />
                  Vietnam
                </div>
              </div>
              <div className="flex flex-col gap-2 p-7">
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', letterSpacing: '0.1em' }}
                >
                  Support hours
                </span>
                <div className="text-sm" style={{ color: 'var(--color-text-primary)', lineHeight: 1.65 }}>
                  Mon – Fri ·{' '}
                  <span className="font-mono">9:00 – 18:00 ICT</span>
                  <br />
                  Sat ·{' '}
                  <span className="font-mono">10:00 – 14:00 ICT</span>
                  <br />
                  Sun ·{' '}
                  <span style={{ color: 'var(--color-text-tertiary)' }}>Closed (email only)</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
