# Landing Page Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Stats, Category Browse, How It Works, Trust & Safety, guest-only CTA, and FAQ sections to the BidNow landing page; remove the redundant "All Active Auctions" section.

**Architecture:** All static marketing sections are inlined directly in `app/page.tsx` (server component). Two client components handle interactivity: `CTASection.tsx` hides itself for authenticated users via `useAuthStore`, and `FAQAccordion.tsx` uses shadcn Accordion for expand/collapse. No new API calls required.

**Tech Stack:** Next.js 16.2 App Router, Tailwind CSS v4, shadcn/ui Accordion, Lucide React, Zustand (`useAuthStore`)

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `frontend/components/home/FAQAccordion.tsx` | Create | Client component — static FAQ using shadcn Accordion |
| `frontend/components/home/CTASection.tsx` | Create | Client component — guest-only CTA banner, returns null when authenticated |
| `frontend/app/page.tsx` | Modify | Add all static sections inline; import + render both client components |
| `frontend/components/ui/accordion.tsx` | Create (via CLI) | shadcn Accordion primitive |

---

## Task 1: Install shadcn Accordion

**Files:**
- Create: `frontend/components/ui/accordion.tsx` (via CLI)

- [ ] **Step 1: Install the Accordion component**

From the `frontend/` directory:
```bash
npx shadcn@latest add accordion
```

- [ ] **Step 2: Verify the file was created**

```bash
ls frontend/components/ui/accordion.tsx
```
Expected: file exists.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/ui/accordion.tsx
git commit -m "chore(ui): add shadcn accordion component"
```

---

## Task 2: Create FAQAccordion component

**Files:**
- Create: `frontend/components/home/FAQAccordion.tsx`

- [ ] **Step 1: Create the component**

Create `frontend/components/home/FAQAccordion.tsx`:

```tsx
'use client'

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

const FAQ_ITEMS = [
  {
    question: 'How does bidding work?',
    answer:
      'Place a bid above the current price. If no one outbids you before the timer ends, you win.',
  },
  {
    question: 'Is my payment information secure?',
    answer:
      'Yes. All transactions are encrypted and processed through verified payment providers.',
  },
  {
    question: 'What happens when I win an auction?',
    answer:
      "You'll receive an email with payment instructions. Complete payment within 48 hours to claim your item.",
  },
  {
    question: 'How do I verify my account?',
    answer:
      'After registering, check your email for a one-time code (OTP). Enter it to activate your account.',
  },
  {
    question: 'Can I sell on BidNow?',
    answer:
      'Yes. Verified sellers can list items from the dashboard. Click "Start Selling" to get started.',
  },
] as const

export function FAQAccordion() {
  return (
    <Accordion type="single" collapsible className="w-full">
      {FAQ_ITEMS.map((item, i) => (
        <AccordionItem key={i} value={`faq-${i}`}>
          <AccordionTrigger className="text-left text-sm font-medium">
            {item.question}
          </AccordionTrigger>
          <AccordionContent className="text-sm text-muted-foreground">
            {item.answer}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/home/FAQAccordion.tsx
git commit -m "feat(home): add FAQAccordion client component"
```

---

## Task 3: Create CTASection component

**Files:**
- Create: `frontend/components/home/CTASection.tsx`

- [ ] **Step 1: Create the component**

Create `frontend/components/home/CTASection.tsx`:

```tsx
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
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/home/CTASection.tsx
git commit -m "feat(home): add CTASection client component, hidden for authenticated users"
```

---

## Task 4: Update page.tsx — add Stats, remove All Active Auctions

**Files:**
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Add Stats section and remove All Active Auctions**

Replace the entire content of `frontend/app/page.tsx` with:

```tsx
import type { Metadata } from 'next'
import Link from 'next/link'
import {
  Flame,
  Star,
  ArrowRight,
  Cpu,
  Gem,
  Shirt,
  Watch,
  Music,
  Palette,
  UserPlus,
  Gavel,
  CheckCircle,
  ShieldCheck,
  Lock,
  Zap,
  Headphones,
} from 'lucide-react'
import { Header }        from '@/components/layout/Header'
import { Footer }        from '@/components/layout/Footer'
import { BottomNav }     from '@/components/layout/BottomNav'
import { AuctionGrid }   from '@/components/auction/AuctionGrid'
import { Button }        from '@/components/ui/button'
import { CTASection }    from '@/components/home/CTASection'
import { FAQAccordion }  from '@/components/home/FAQAccordion'
import { AuctionStatus } from '@/lib/design-tokens'
import { auctionService } from '@/services/auction.service'

export const metadata: Metadata = {
  title: 'BidNow — Bid. Win. Repeat.',
  description: 'Live auctions for rare collectibles, luxury watches, vintage instruments, and more.',
}

const STATS = [
  { value: '50K+',  label: 'Active Users' },
  { value: '$2.5M+', label: 'Total Auction Value' },
  { value: '4.8★',  label: 'User Rating' },
] as const

const CATEGORIES = [
  { label: 'Electronics',  slug: 'electronics',  Icon: Cpu,     color: 'text-[var(--color-info-default)]',    bg: 'bg-[var(--color-info-subtle)]' },
  { label: 'Collectibles', slug: 'collectibles',  Icon: Gem,     color: 'text-[var(--color-success-default)]', bg: 'bg-[var(--color-success-subtle)]' },
  { label: 'Fashion',      slug: 'fashion',       Icon: Shirt,   color: 'text-[var(--color-warning-default)]', bg: 'bg-[var(--color-warning-subtle)]' },
  { label: 'Watches',      slug: 'watches',       Icon: Watch,   color: 'text-[var(--color-brand-600)]',       bg: 'bg-[var(--color-brand-50)]' },
  { label: 'Instruments',  slug: 'instruments',   Icon: Music,   color: 'text-orange-500',                     bg: 'bg-orange-50' },
  { label: 'Art',          slug: 'art',           Icon: Palette, color: 'text-rose-500',                       bg: 'bg-rose-50' },
] as const

const HOW_IT_WORKS = [
  { step: 1, Icon: UserPlus,     title: 'Register & Verify',  description: 'Create an account and confirm your identity with a one-time code.' },
  { step: 2, Icon: Gavel,        title: 'Browse & Bid',       description: 'Find items you love and place competitive real-time bids.' },
  { step: 3, Icon: CheckCircle,  title: 'Pay & Receive',      description: 'Win the auction, pay securely, and get your item delivered.' },
] as const

const TRUST_CARDS = [
  { Icon: ShieldCheck, title: 'Verified Sellers',   body: 'Every seller is reviewed and approved before listing.' },
  { Icon: Lock,        title: 'Secure Payments',    body: 'Payments are encrypted and held in escrow until delivery.' },
  { Icon: Zap,         title: 'Real-time Bidding',  body: 'Live price updates — no page refreshes needed.' },
  { Icon: Headphones,  title: '24/7 Support',        body: 'Our team is available around the clock to help.' },
] as const

export default async function HomePage() {
  const featuredPicks = await auctionService.getAuctions({ featured: true })
  const allAuctions   = await auctionService.getAuctions()
  const hotAuctions   = allAuctions.filter(
    (a) => a.status === AuctionStatus.Critical || a.status === AuctionStatus.EndingSoon,
  )

  return (
    <>
      <Header />
      <main className="flex-1 mx-auto w-full max-w-[var(--container-xl)] px-4 pb-14 md:pb-8">

        {/* Hero */}
        <section className="relative overflow-hidden rounded-2xl mt-8 mb-12 bg-gradient-to-br from-[var(--color-brand-600)] to-[var(--color-brand-900)] px-8 py-16 text-white sm:px-16">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--color-brand-400)_0%,_transparent_60%)] opacity-40" />
          <div className="relative flex flex-col gap-6 max-w-xl">
            <div className="inline-flex w-fit items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1 text-sm font-medium backdrop-blur-sm">
              <span className="size-2 rounded-full bg-[var(--color-auction-active-accent)] animate-pulse" />
              Live auctions happening now
            </div>
            <h1 className="font-display font-medium text-[length:var(--font-size-5xl)] leading-none">
              Bid. Win.<br />Repeat.
            </h1>
            <p className="text-base text-white/80 max-w-sm">
              Rare collectibles, luxury watches, vintage instruments — all in one place. Real-time bidding, zero compromise.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button variant="brand" className="h-11 bg-white text-[var(--color-brand-700)] hover:bg-white/90" render={<Link href="/auctions" />} nativeButton={false}>
                Browse Auctions
                <ArrowRight className="size-4" />
              </Button>
              <Button variant="outline" className="h-11 border-white/30 bg-transparent text-white hover:bg-white/10" render={<Link href="/auctions/new" />} nativeButton={false}>
                Start Selling
              </Button>
            </div>
          </div>
        </section>

        {/* Stats */}
        <section className="grid grid-cols-3 gap-4 mb-12">
          {STATS.map(({ value, label }) => (
            <div key={label} className="rounded-xl bg-muted px-4 py-6 text-center">
              <p className="font-mono font-bold text-[length:var(--font-size-2xl)] text-[var(--color-text-primary)]">
                {value}
              </p>
              <p className="text-xs text-muted-foreground mt-1">{label}</p>
            </div>
          ))}
        </section>

        {/* Ending Soon */}
        {hotAuctions.length > 0 && (
          <section className="flex flex-col gap-4 mb-12">
            <div className="flex items-center justify-between">
              <h2 className="font-display font-medium text-[length:var(--font-size-xl)] flex items-center gap-2">
                <Flame className="size-5 text-[var(--color-danger-default)]" />
                Ending Soon
              </h2>
              <Link href="/auctions?sort=ending" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
                View all <ArrowRight className="size-3.5" />
              </Link>
            </div>
            <AuctionGrid auctions={hotAuctions} />
          </section>
        )}

        {/* Featured Picks */}
        <section className="flex flex-col gap-4 mb-12">
          <div className="flex items-center justify-between">
            <h2 className="font-display font-medium text-[length:var(--font-size-xl)] flex items-center gap-2">
              <Star className="size-5 text-[var(--color-warning-text)]" />
              Featured Picks
            </h2>
            <Link href="/auctions?featured=true" className="text-sm text-[var(--color-text-link)] hover:underline flex items-center gap-1">
              View all <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <AuctionGrid auctions={featuredPicks} />
        </section>

        {/* Category Browse */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Browse by Category
          </h2>
          <div className="grid grid-cols-3 md:grid-cols-6 gap-3">
            {CATEGORIES.map(({ label, slug, Icon, color, bg }) => (
              <Link
                key={slug}
                href={`/auctions?category=${slug}`}
                className="group flex flex-col items-center gap-2 rounded-xl p-4 hover:bg-muted transition-colors duration-[var(--duration-tesla)]"
              >
                <div className={`rounded-full p-3 ${bg}`}>
                  <Icon className={`size-6 ${color}`} />
                </div>
                <span className="text-xs font-medium text-center group-hover:text-[var(--color-text-brand)] transition-colors">
                  {label}
                </span>
              </Link>
            ))}
          </div>
        </section>

        {/* How It Works */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-8">
            How It Works
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {HOW_IT_WORKS.map(({ step, Icon, title, description }) => (
              <div key={step} className="flex flex-col gap-4">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-brand-600)] text-white text-sm font-medium">
                    {step}
                  </span>
                  <Icon className="size-6 text-[var(--color-text-tertiary)]" />
                </div>
                <div>
                  <h3 className="font-medium text-sm mb-1">{title}</h3>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Trust & Safety */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Why Trust BidNow?
          </h2>
          <div className="grid grid-cols-2 gap-4">
            {TRUST_CARDS.map(({ Icon, title, body }) => (
              <div key={title} className="rounded-xl border border-[var(--color-border-default)] bg-card p-5 flex flex-col gap-3">
                <Icon className="size-5 text-[var(--color-brand-600)]" />
                <div>
                  <h3 className="font-medium text-sm mb-1">{title}</h3>
                  <p className="text-xs text-muted-foreground">{body}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* CTA — guest only, hides itself client-side */}
        <CTASection />

        {/* FAQ */}
        <section className="mb-12">
          <h2 className="font-display font-medium text-[length:var(--font-size-xl)] mb-6">
            Frequently Asked Questions
          </h2>
          <FAQAccordion />
        </section>

      </main>
      <Footer />
      <BottomNav />
    </>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3: Start the dev server and verify visually**

```bash
cd frontend && npm run dev
```

Open `http://localhost:3000` and confirm:
- Stats row appears below hero (3 metric cards)
- "Ending Soon" and "Featured Picks" sections present
- Category Browse grid (3×2 mobile / 6 cols desktop)
- "How It Works" 3-step section
- "Trust & Safety" 2×2 card grid
- CTA section visible when logged out, hidden when logged in
- FAQ accordion expands/collapses each item
- "All Active Auctions" section is gone

- [ ] **Step 4: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat(home): add stats, categories, how-it-works, trust, cta, faq sections"
```

---

## Self-Review

**Spec coverage:**
- ✅ Stats — Task 4 (`STATS` array, grid section)
- ✅ Category Browse — Task 4 (`CATEGORIES` array, grid section)
- ✅ How It Works — Task 4 (`HOW_IT_WORKS` array, 3-step section)
- ✅ Trust & Safety — Task 4 (`TRUST_CARDS` array, 2×2 grid)
- ✅ CTA (guest-only) — Task 3 (`CTASection.tsx`), Task 4 (imported in page.tsx)
- ✅ FAQ — Task 2 (`FAQAccordion.tsx`), Task 4 (imported in page.tsx)
- ✅ Remove All Active Auctions — Task 4 (not present in new page.tsx)
- ✅ Page order matches spec (Hero → Stats → Ending Soon → Featured → Categories → How It Works → Trust → CTA → FAQ)

**Placeholder scan:** No TBD/TODO. All code blocks are complete. No vague steps.

**Type consistency:** `FAQAccordion` and `CTASection` are both named exports matching their imports in page.tsx. `useAuthStore` selector `(s) => s.isAuthenticated` matches the store's `isAuthenticated: boolean` field. Icon types are all `LucideIcon`-compatible from direct Lucide imports. `as const` on data arrays preserves literal types throughout.

**Note on Instruments/Art colors:** `text-orange-500`/`bg-orange-50` and `text-rose-500`/`bg-rose-50` use Tailwind v4 built-in palette utilities since no custom CSS token exists for these colors. This is acceptable — the rule against raw hex values in AGENTS.md targets inline style attributes, not utility classes.
