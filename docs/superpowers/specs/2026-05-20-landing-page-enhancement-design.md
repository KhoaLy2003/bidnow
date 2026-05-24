# Landing Page Enhancement Design

**Date:** 2026-05-20
**Branch:** feature/audit-logs (or new branch)
**Scope:** Add missing marketing sections to the BidNow homepage

---

## Overview

The current landing page (`app/page.tsx`) has a Hero, "Ending Soon" hot auctions, "Featured Picks", and "All Active Auctions" sections. This enhancement fills the gaps identified in `docs/epics/enhancement/landing-page-issue.md`: Stats, Category Browse, How It Works, Trust & Safety, a guest-only CTA, and a static FAQ.

---

## Architecture

**Approach:** Inline static sections directly in `page.tsx`. Only the FAQ accordion is extracted into `components/home/FAQAccordion.tsx` because it needs client-side state (shadcn `Accordion`).

No new server fetching is required — all new sections are static. Auth check for the CTA section uses the existing server-side session pattern.

---

## Page Order (top to bottom)

| # | Section | Status |
|---|---------|--------|
| 1 | Hero | Existing — no changes |
| 2 | Stats | New |
| 3 | Ending Soon (hot auctions) | Existing — no changes |
| 4 | Featured Picks | Existing — no changes |
| 5 | Category Browse | New |
| 6 | How It Works | New |
| 7 | Trust & Safety | New |
| 8 | CTA (guest-only) | New |
| 9 | FAQ | New |

**Removed:** "All Active Auctions" section — redundant with the "View all" links and `/auctions` page. Removes scroll fatigue before users reach marketing sections.

---

## Section Designs

### Stats
- Layout: `grid grid-cols-3` full-width
- 3 metric cards, each: large mono number + label
- Background: `--color-background-secondary`
- Numbers: `--font-size-lg`, `font-mono font-bold`
- Content:
  - "50K+" / "Active Users"
  - "$2.5M+" / "Total Auction Value"
  - "4.8★" / "User Rating"

### Category Browse
- Heading: "Browse by Category"
- Layout: `grid grid-cols-3 md:grid-cols-6`
- 6 static categories with Lucide icons, each links to `/auctions?category=<slug>`:

| Category | Icon | Color token |
|----------|------|-------------|
| Electronics | `Cpu` | `--color-info-*` (blue) |
| Collectibles | `Gem` | `--color-success-*` (green) |
| Fashion | `Shirt` | `--color-warning-*` (amber) |
| Watches | `Watch` | `--color-brand-*` (indigo) |
| Instruments | `Music` | orange (custom) |
| Art | `Palette` | rose (custom) |

- Each cell: icon (24px) + label below, hover state with brand underline

### How It Works
- Heading: "How It Works"
- Layout: `grid grid-cols-1 md:grid-cols-3 gap-8`
- 3 steps, each: numbered badge + Lucide icon + title + 1-sentence description

| Step | Icon | Title | Description |
|------|------|-------|-------------|
| 1 | `UserPlus` | Register & Verify | Create an account and confirm your identity with a one-time code. |
| 2 | `Gavel` | Browse & Bid | Find items you love and place competitive real-time bids. |
| 3 | `CheckCircle` | Pay & Receive | Win the auction, pay securely, and get your item delivered. |

### Trust & Safety
- Heading: "Why Trust BidNow?"
- Layout: `grid grid-cols-2 gap-4`
- 4 cards, each: icon + title + 1 sentence

| Icon | Title | Body |
|------|-------|------|
| `ShieldCheck` | Verified Sellers | Every seller is reviewed and approved before listing. |
| `Lock` | Secure Payments | Payments are encrypted and held in escrow until delivery. |
| `Zap` | Real-time Bidding | Live price updates — no page refreshes needed. |
| `Headphones` | 24/7 Support | Our team is available around the clock to help. |

### CTA Section
- Condition: **hidden for authenticated users** (server-side check)
- Heading: "Ready to find great deals?"
- Subtext: "Join thousands of buyers and sellers on BidNow."
- Primary button: `variant="brand"` → "Sign Up Free" → `/register`
- Secondary button: `variant="outline"` → "Browse Auctions" → `/auctions`
- Background: gradient matching brand colors (lighter variant of Hero)

### FAQ (`components/home/FAQAccordion.tsx`)
- `"use client"` directive — uses shadcn `Accordion` (already installed)
- Heading: "Frequently Asked Questions"
- 5 static Q&A items:

| Question | Answer |
|----------|--------|
| How does bidding work? | Place a bid above the current price. If no one outbids you before the timer ends, you win. |
| Is my payment information secure? | Yes. All transactions are encrypted and processed through verified payment providers. |
| What happens when I win an auction? | You'll receive an email with payment instructions. Complete payment within 48 hours to claim your item. |
| How do I verify my account? | After registering, check your email for a one-time code (OTP). Enter it to activate your account. |
| Can I sell on BidNow? | Yes. Verified sellers can list items from the dashboard. Click "Start Selling" to get started. |

---

## Auth Check for CTA

Auth is client-side only via `useAuthStore` (Zustand). The CTA section must be a `"use client"` component — `components/home/CTASection.tsx` — that reads `isAuthenticated` from `useAuthStore` and returns `null` when the user is logged in. This avoids any flash of content: the section simply doesn't render after hydration.

---

## Styling Constraints

- All colors via CSS variables — no raw hex
- Prices and numbers: `font-mono`
- Icons: Lucide React only
- No new dependencies required — shadcn `Accordion` is already available

---

## Files Changed

| File | Change |
|------|--------|
| `app/page.tsx` | Add Stats, Category Browse, How It Works, Trust & Safety, CTA sections; remove All Active Auctions |
| `components/home/FAQAccordion.tsx` | New client component for FAQ accordion |
| `components/home/CTASection.tsx` | New client component; hides CTA for authenticated users via `useAuthStore` |
