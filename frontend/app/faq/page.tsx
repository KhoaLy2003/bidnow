import type { Metadata } from 'next'
import Link from 'next/link'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { buttonVariants } from '@/components/ui/button'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

export const metadata: Metadata = {
  title: 'FAQ',
  description: 'Answers to common questions about bidding, accounts, payments, disputes, and more.',
}

const CATEGORIES = [
  { id: 'bidding',   label: 'Bidding & Auctions',     count: 6, icon: '$' },
  { id: 'account',   label: 'Account & Verification',  count: 5, icon: '@' },
  { id: 'wallet',    label: 'Wallet & Payments',        count: 5, icon: '₫' },
  { id: 'shipping',  label: 'Shipping & Delivery',      count: 4, icon: '→' },
  { id: 'disputes',  label: 'Disputes & Returns',       count: 4, icon: '⚖' },
  { id: 'security',  label: 'Security & Privacy',       count: 4, icon: '⚿' },
  { id: 'technical', label: 'Technical Issues',         count: 4, icon: '⚙' },
] as const

const FAQ_SECTIONS: Record<string, { q: string; a: string }[]> = {
  bidding: [
    {
      q: 'How do I place a bid?',
      a: 'Open any active auction listing and enter your bid amount in the Bid Panel on the right. Your bid must exceed the current highest bid by at least the minimum increment shown. Click Place Bid and confirm. You\'ll receive immediate on-screen confirmation and will be notified by email and in-app if you\'re outbid.',
    },
    {
      q: 'Can I retract a bid?',
      a: 'Bids are legally binding commitments. Retraction is only considered in cases of a clear typographical error — for example, bidding 10,000,000 VND when you intended 1,000,000 VND. Contact support immediately via the Help Center. Requests are evaluated within 24 hours and are not guaranteed.',
    },
    {
      q: 'What is anti-sniping and how does it work?',
      a: 'If any bid is placed within the final 3 minutes of an auction, the clock resets to 3 minutes. The auction closes only when 3 full minutes pass with no new bid. This gives all bidders a fair chance to respond.',
    },
    {
      q: "Why didn't I win even though I placed the highest bid?",
      a: "The seller may have set a reserve price that was not met. If the highest bid falls below the reserve, the auction ends without a sale obligation — though the seller may choose to accept the offer.",
    },
    {
      q: 'What happens if two bidders submit the same amount at the same time?',
      a: 'The earlier bid wins. BidNow records bid timestamps to the millisecond on the server side. Display ties are broken by server-side timestamp.',
    },
    {
      q: 'Is there a maximum bid feature?',
      a: "Not currently. Bids must be entered manually. Set up outbid notifications to stay informed when your bid is surpassed.",
    },
  ],
  account: [
    {
      q: 'How do I complete identity verification?',
      a: 'Go to Profile → Identity Verification. Upload a clear, well-lit photo of your government-issued ID (CCCD, CMND, or passport) showing all four corners. Follow the on-screen prompts to complete the live selfie step. Verification typically takes 1–2 business days. You\'ll receive an email when your account is verified.',
    },
    {
      q: 'Why do I need to verify my identity?',
      a: 'Vietnamese e-commerce law requires identity verification for high-value transactions. Verification also protects all platform users from fraud, ensures accountability, and qualifies you for buyer protection on transactions.',
    },
    {
      q: 'Can I have more than one BidNow account?',
      a: 'No. Each person may hold only one BidNow account. Duplicate accounts are a violation of our Terms of Service and will be closed. If you have lost access to your account, contact support to recover it.',
    },
    {
      q: 'How do I update my email address or phone number?',
      a: 'Go to Profile → Account Settings and update your contact details. A verification code will be sent to the new email address or phone number to confirm the change before it takes effect.',
    },
    {
      q: "I didn't receive my verification email. What should I do?",
      a: 'Check your spam or junk mail folder first. If it\'s not there, return to the verification screen and request a new OTP. If the problem persists, contact support at support@bidnow.com with your registered email address.',
    },
  ],
  wallet: [
    {
      q: 'What is the BidNow Wallet?',
      a: 'The BidNow Wallet is your in-platform balance. It is used for paying deposits, completing auction purchases, and receiving refunds. You can top up your Wallet via VNPAY or bank transfer and withdraw funds to your registered bank account at any time.',
    },
    {
      q: 'How do I top up my Wallet?',
      a: 'Go to Wallet → Top Up. Select your preferred method (VNPAY QR code, bank card, or bank transfer) and enter the amount. Top-ups via VNPAY are credited instantly. Bank transfers may take 1 business day.',
    },
    {
      q: 'How long does a deposit refund take?',
      a: 'Deposit refunds for non-winning bids are processed automatically within 24 hours of auction close and credited to your BidNow Wallet. From there, you can use the balance immediately or initiate a withdrawal to your bank account (1–3 business days).',
    },
    {
      q: 'Are there fees for using the Wallet?',
      a: "BidNow does not charge fees for Wallet deposits or withdrawals. Standard transaction fees may be applied by VNPAY for top-ups, as specified in VNPAY's fee schedule.",
    },
    {
      q: 'What payment methods are accepted?',
      a: 'BidNow accepts payment via VNPAY (supporting major Vietnamese bank cards, international credit cards, and QR code payments) and direct bank transfer for Wallet top-ups.',
    },
  ],
  shipping: [
    {
      q: 'How is shipping arranged?',
      a: 'Shipping is arranged directly between buyer and seller. Each listing specifies available shipping methods, estimated costs, and the seller\'s handling time. Review these details carefully before bidding.',
    },
    {
      q: 'What if my item arrives damaged?',
      a: 'File a dispute within 7 days of receiving your item through My Bids → Transaction History → File Dispute. Provide photos of the damage and the original packaging. Our mediation team will review the evidence within 3–5 business days.',
    },
    {
      q: 'Can I arrange local pickup instead of shipping?',
      a: 'Local pickup is available for listings where the seller has enabled it. Check the shipping section of each listing. BidNow recommends conducting in-person exchanges in safe, public locations.',
    },
    {
      q: 'How do I track my shipment?',
      a: 'Once the seller ships your item, they are required to enter the tracking number and carrier into the transaction record. You\'ll receive a notification with tracking details. Find tracking information under My Bids → Won Auctions.',
    },
  ],
  disputes: [
    {
      q: 'How do I file a dispute?',
      a: 'Go to My Bids → Transaction History, locate the relevant transaction, and click File Dispute. Describe the issue in detail and attach any supporting evidence. Disputes must be filed within 7 calendar days of auction close.',
    },
    {
      q: 'What are valid reasons to file a dispute?',
      a: "Valid dispute grounds include: item not received, item significantly not as described, item arrived damaged, or suspected fraud. Buyer's remorse is not a valid dispute reason. All bids are binding.",
    },
    {
      q: 'How long does dispute resolution take?',
      a: 'Our mediation team aims to reach a decision within 3–5 business days of the dispute being opened. Complex cases may require up to 10 business days. You will receive status updates throughout the process.',
    },
    {
      q: 'What is BidNow Buyer Protection?',
      a: "If a verified seller fails to deliver your item and our investigation confirms non-delivery or material misrepresentation, BidNow will refund your purchase price to your BidNow Wallet within 7 business days.",
    },
  ],
  security: [
    {
      q: 'Is my payment information secure?',
      a: 'Yes. BidNow does not store your full card number or CVV. All payment processing is handled by VNPAY, which is PCI-DSS Level 1 certified. All connections to BidNow are encrypted using TLS 1.2 or higher.',
    },
    {
      q: 'How do I report suspicious activity or fraud?',
      a: 'Use the Report button on any listing, bid, or user profile, or email support@bidnow.com. Include the relevant listing or transaction ID and a description of the suspicious activity. All reports are reviewed within 3 business days.',
    },
    {
      q: 'Can I delete my account and all my data?',
      a: 'Yes. Contact support@bidnow.com to request account deletion. We will process your request within 30 days. Certain data (transaction records, identity verification) must be retained for legal compliance periods.',
    },
    {
      q: 'Does BidNow share my personal data with third parties?',
      a: "We share data only as necessary — with VNPAY for payment processing and identity verification providers. We do not sell your data to any third party for marketing purposes. See our Privacy Policy for full details.",
    },
  ],
  technical: [
    {
      q: "The bid panel isn't loading. What should I do?",
      a: 'Refresh the page. If the issue persists, clear your browser cache and cookies or try a different browser. Real-time auction features require JavaScript to be enabled. If the problem continues, contact support with your browser name/version and the URL.',
    },
    {
      q: "I placed a bid but it's not showing. Did it go through?",
      a: "Check My Bids → Bid History for a record of your bid. If it does not appear there and you are not listed as the current highest bidder, the bid was not processed — likely due to a dropped connection. You may safely place the bid again.",
    },
    {
      q: 'The countdown timer seems off. Is this a bug?',
      a: 'The timer synchronizes with the server at regular intervals. Minor display differences of 1–3 seconds are normal and are caused by network latency. The authoritative close time is always determined server-side.',
    },
    {
      q: 'Which browsers does BidNow support?',
      a: 'BidNow supports the latest two major versions of Google Chrome, Mozilla Firefox, Apple Safari, and Microsoft Edge. We recommend keeping your browser up to date. Internet Explorer is not supported.',
    },
  ],
}

export default async function FAQPage({ searchParams }: { searchParams: Promise<{ cat?: string }> }) {
  const { cat } = await searchParams
  const activeCat = CATEGORIES.find(c => c.id === cat) ?? CATEGORIES[0]
  const activeItems = FAQ_SECTIONS[activeCat.id] ?? []

  return (
    <>
      <Header />
      <main id="main-content" className="flex-1">
        {/* Hero */}
        <div className="mx-auto w-full px-6" style={{ maxWidth: 1200, paddingTop: 64, paddingBottom: 48 }}>
          <div className="flex flex-col gap-5">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span>Help Center</span>
              <span style={{ color: 'var(--color-text-disabled)' }}>›</span>
              <span style={{ color: 'var(--color-text-secondary)' }}>FAQ</span>
            </div>
            <span
              className="font-mono font-medium uppercase"
              style={{ fontSize: 11, color: 'var(--color-brand-600)', letterSpacing: '0.1em' }}
            >
              Frequently asked questions
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
              What can we help you with?
            </h1>
            <p style={{ fontSize: 17, lineHeight: 1.55, color: 'var(--color-text-secondary)', maxWidth: 640, margin: 0 }}>
              Search 32 articles across seven topics, or browse by category. Can&apos;t find what you&apos;re after?{' '}
              <Link href="/contact" style={{ color: 'var(--color-text-primary)', textDecoration: 'underline' }}>
                Contact support →
              </Link>
            </p>

            {/* Search */}
            <div
              className="flex items-center mt-3"
              style={{
                maxWidth: 680,
                height: 56,
                border: '1px solid var(--color-border-strong)',
                borderRadius: 8,
                background: 'var(--color-bg-base)',
                padding: '0 18px',
              }}
            >
              <span style={{ fontSize: 18, color: 'var(--color-text-tertiary)', marginRight: 12 }}>⌕</span>
              <span style={{ flex: 1, fontSize: 16, color: 'var(--color-text-tertiary)' }}>
                Try &quot;deposit refund&quot; or &quot;anti-sniping&quot;
              </span>
              <span
                className="font-mono"
                style={{
                  background: 'var(--color-bg-elevated)',
                  padding: '3px 7px',
                  borderRadius: 4,
                  color: 'var(--color-text-tertiary)',
                  fontSize: 11,
                }}
              >
                ⌘K
              </span>
            </div>

            <div className="flex flex-wrap gap-2">
              <span className="text-xs text-muted-foreground self-center">Popular:</span>
              {['Deposit refund timing', 'Verify my ID', 'How VNPAY works', 'Anti-sniping'].map((t) => (
                <span
                  key={t}
                  className="text-sm px-2.5 py-1 rounded-full"
                  style={{
                    border: '1px solid var(--color-border-default)',
                    color: 'var(--color-text-secondary)',
                  }}
                >
                  {t}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Category grid */}
        <div className="mx-auto w-full px-6 pb-12" style={{ maxWidth: 1200 }}>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
            {CATEGORIES.slice(0, 4).map((c) => {
              const isActive = c.id === activeCat.id
              return (
              <Link
                key={c.id}
                href={`/faq?cat=${c.id}`}
                className="flex flex-col gap-3 rounded-xl no-underline"
                style={{
                  border: `1px solid ${isActive ? 'var(--color-brand-600)' : 'var(--color-border-default)'}`,
                  background: 'var(--color-bg-base)',
                  padding: 18,
                }}
              >
                <div
                  className="flex items-center justify-center rounded-lg text-xl"
                  style={{
                    width: 36,
                    height: 36,
                    background: isActive ? 'var(--color-brand-50)' : 'var(--color-bg-elevated)',
                    color: isActive ? 'var(--color-brand-700)' : 'var(--color-text-primary)',
                  }}
                >
                  {c.icon}
                </div>
                <span className="text-sm font-medium" style={{ color: 'var(--color-text-primary)' }}>
                  {c.label}
                </span>
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.08em' }}
                >
                  {c.count} articles
                </span>
              </Link>
              )
            })}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5 mt-3.5">
            {CATEGORIES.slice(4).map((c) => {
              const isActive = c.id === activeCat.id
              return (
              <Link
                key={c.id}
                href={`/faq?cat=${c.id}`}
                className="flex flex-col gap-3 rounded-xl no-underline"
                style={{
                  border: `1px solid ${isActive ? 'var(--color-brand-600)' : 'var(--color-border-default)'}`,
                  background: 'var(--color-bg-base)',
                  padding: 18,
                }}
              >
                <div
                  className="flex items-center justify-center rounded-lg text-xl"
                  style={{
                    width: 36,
                    height: 36,
                    background: isActive ? 'var(--color-brand-50)' : 'var(--color-bg-elevated)',
                    color: isActive ? 'var(--color-brand-700)' : 'var(--color-text-primary)',
                  }}
                >
                  {c.icon}
                </div>
                <span className="text-sm font-medium" style={{ color: 'var(--color-text-primary)' }}>
                  {c.label}
                </span>
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.08em' }}
                >
                  {c.count} articles
                </span>
              </Link>
              )
            })}
          </div>
        </div>

        {/* Active category */}
        <div className="mx-auto w-full px-6 pb-24" style={{ maxWidth: 1200 }}>
          <div
            style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: 48, alignItems: 'start' }}
          >
            {/* Sidebar */}
            <nav className="flex flex-col gap-1">
              <span
                className="font-mono uppercase"
                style={{ fontSize: 10.5, color: 'var(--color-text-tertiary)', letterSpacing: '0.1em' }}
              >
                All categories
              </span>
              <div className="flex flex-col mt-2">
                {CATEGORIES.map((c) => {
                  const isActive = c.id === activeCat.id
                  return (
                  <Link
                    key={c.id}
                    href={`/faq?cat=${c.id}`}
                    className="flex items-center justify-between rounded-md no-underline"
                    style={{
                      padding: '10px 12px',
                      background: isActive ? 'var(--color-brand-50)' : 'transparent',
                    }}
                  >
                    <span
                      className="text-sm"
                      style={{
                        color: isActive ? 'var(--color-brand-700)' : 'var(--color-text-secondary)',
                        fontWeight: isActive ? 500 : 400,
                      }}
                    >
                      {c.label}
                    </span>
                    <span
                      className="font-mono"
                      style={{
                        fontSize: 11,
                        color: isActive ? 'var(--color-brand-700)' : 'var(--color-text-tertiary)',
                      }}
                    >
                      {c.count}
                    </span>
                  </Link>
                  )
                })}
              </div>
            </nav>

            {/* Questions */}
            <div className="flex flex-col gap-4">
              <div className="flex items-baseline justify-between">
                <h2
                  className="font-display font-medium"
                  style={{ margin: 0, fontSize: 26, letterSpacing: '-0.01em', color: 'var(--color-text-primary)' }}
                >
                  {activeCat.label}
                </h2>
                <span
                  className="font-mono uppercase"
                  style={{ fontSize: 11, color: 'var(--color-text-tertiary)', letterSpacing: '0.06em' }}
                >
                  {activeCat.count} articles · UPDATED MAY 2026
                </span>
              </div>

              <Accordion className="w-full">
                {activeItems.map((item, i) => (
                  <AccordionItem key={i} value={`item-${i}`}>
                    <AccordionTrigger
                      className="font-display font-medium text-left"
                      style={{ fontSize: 18, letterSpacing: '-0.005em' }}
                    >
                      {item.q}
                    </AccordionTrigger>
                    <AccordionContent>
                      <p
                        style={{
                          fontSize: 14.5,
                          lineHeight: 1.65,
                          color: 'var(--color-text-secondary)',
                          maxWidth: 720,
                          margin: 0,
                        }}
                      >
                        {item.a}
                      </p>
                    </AccordionContent>
                  </AccordionItem>
                ))}
              </Accordion>

              {/* Still stuck */}
              <div
                className="flex flex-wrap items-center justify-between gap-6 rounded-xl p-7 mt-3"
                style={{ background: 'var(--color-bg-elevated)', border: '1px solid var(--color-border-default)' }}
              >
                <div className="flex flex-col gap-1">
                  <span
                    className="font-display font-medium"
                    style={{ fontSize: 18, color: 'var(--color-text-primary)' }}
                  >
                    Still stuck?
                  </span>
                  <span className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
                    Our team usually replies within 2 business hours during weekdays.
                  </span>
                </div>
                <div className="flex gap-2">
                  <Link href="/contact" className={buttonVariants({ variant: 'brand', size: 'lg' })}>
                    Contact support
                  </Link>
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
