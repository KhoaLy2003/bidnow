# Design System Implementation Plan

## Status Overview

| Deliverable | Status |
|---|---|
| `docs/design-system.md` — full spec | ✅ Done |
| `docs/plans/design-system-implementation.md` | ✅ Done (this file) |
| Dependencies installed (lucide-react, zustand, socket.io-client, etc.) | ✅ Done |
| `npx shadcn@latest init` | ✅ Done |
| 18 shadcn UI primitives in `components/ui/` | ✅ Done |
| Stitch prototype (16 screens) | ✅ Done |
| **Phase 1** — `globals.css` full token rewrite | ✅ Done |
| **Phase 2** — Core utilities | ✅ Done |
| **Phase 3** — Customize shadcn primitives | ✅ Done |
| **Phase 4** — Auction components | ⬜ Next |
| **Phase 5** — Layout components | ⬜ Pending |
| **Phase 6** — Wallet & Notification components | ⬜ Pending |
| **Phase 7** — Route pages | ⬜ Pending |

---

## Phase 1 — `globals.css` Full Token Rewrite

**File:** `frontend/app/globals.css`

Replace the shadcn-generated stub with a full dual-layer token file.

### Structure order (must preserve)
1. `@import "tailwindcss"`
2. Raw palette values in `:root`
3. Semantic light-mode tokens in `:root` (shadcn HSL vars + BidNow extended tokens)
4. Dark overrides in `.dark {}`
5. `@theme inline {}` — forward all tokens as Tailwind utilities
6. `@keyframes` definitions
7. Global base styles (`*, body` resets)

### Layer 1 — shadcn HSL vars (map to brand indigo)
shadcn requires channel-only HSL values (no `hsl()` wrapper):
```
--primary:          244 72% 58%    /* #4F46E5 indigo */
--primary-foreground: 0 0% 100%
--secondary:        244 10% 95%
--secondary-foreground: 244 72% 40%
--accent:           244 20% 96%
--accent-foreground: 244 72% 30%
--destructive:      0 72% 51%
--muted:            240 5% 96%
--muted-foreground: 240 4% 46%
--border:           240 6% 90%
--ring:             244 72% 58%
--background:       0 0% 100%
--foreground:       240 10% 7%
--card:             0 0% 100%
--card-foreground:  240 10% 7%
--popover:          0 0% 100%
--popover-foreground: 240 10% 7%
--input:            240 6% 90%
--radius:           0.5rem
```

Dark overrides (in `.dark {}`):
```
--primary:          244 72% 68%    /* lightened for dark bg */
--background:       0 0% 6%
--foreground:       240 5% 94%
--card:             240 6% 12%
--border:           240 5% 17%
--muted:            240 4% 16%
--muted-foreground: 240 5% 64%
```

### Layer 2 — BidNow extended tokens (hex, full values)

**Brand:**
```css
--color-brand-400: #6366F1;
--color-brand-500: #4F46E5;   /* primary */
--color-brand-600: #3730A3;   /* pressed */
--color-brand-subtle: #EEF2FF;  dark: #1E1B4B
--color-brand-border: #A5B4FC;  dark: #4338CA
```

**Surface/Background:**
```css
--color-bg-base:        #FFFFFF;    dark: #0F0F0F
--color-bg-elevated:    #F9F9FB;    dark: #1A1A1F
--color-bg-overlay:     #F3F3F7;    dark: #242428
--color-bg-card:        #FFFFFF;    dark: #1E1E24
--color-bg-card-hover:  #F5F5FF;    dark: #25252E
--color-bg-modal:       rgba(255,255,255,0.97);  dark: rgba(18,18,22,0.97)
--color-bg-backdrop:    rgba(0,0,0,0.4);
--color-bg-sidebar:     #F4F4F8;    dark: #16161B
```

**Text:**
```css
--color-text-primary:    #111115;  dark: #F0F0F4
--color-text-secondary:  #52525C;  dark: #A0A0B0
--color-text-tertiary:   #8B8B9A;  dark: #6A6A7E
--color-text-disabled:   #C2C2D0;  dark: #3A3A4A
--color-text-brand:      #4F46E5;  dark: #8483F5
--color-text-link:       #4F46E5;  dark: #8483F5
```

**Semantic states:**
```css
--color-success-default:  #16A34A;  dark: #22C55E
--color-success-subtle:   #F0FDF4;  dark: #052E16
--color-warning-default:  #D97706;  dark: #F59E0B
--color-warning-subtle:   #FFFBEB;  dark: #1C1200
--color-danger-default:   #DC2626;  dark: #EF4444
--color-danger-subtle:    #FEF2F2;  dark: #1C0000
--color-info-default:     #0284C7;  dark: #38BDF8
--color-info-subtle:      #F0F9FF;  dark: #001B2E
```

**Auction state tokens** (example — full set in `design-system.md §2`):
```css
/* active */
--color-auction-active-bg:     #F0F9FF;  dark: #001B2E
--color-auction-active-text:   #0284C7;  dark: #38BDF8
--color-auction-active-border: #BAE6FD;  dark: #0369A1

/* ending-soon */
--color-auction-ending-bg:     #FFFBEB;  dark: #1C1200
--color-auction-ending-text:   #B45309;  dark: #FCD34D
--color-auction-ending-border: #FDE68A;  dark: #78350F

/* critical */
--color-auction-critical-bg:     #FEF2F2;  dark: #1C0000
--color-auction-critical-text:   #B91C1C;  dark: #F87171
--color-auction-critical-border: #FECACA;  dark: #7F1D1D

/* closed / won / lost / outbid — see design-system.md */
```

**Wallet:**
```css
--color-wallet-bg:       #F0F9FF;  dark: #001B2E
--color-wallet-text:     #0369A1;  dark: #38BDF8
--color-wallet-positive: #16A34A;  dark: #22C55E
--color-wallet-negative: #DC2626;  dark: #EF4444
```

**Shadows:**
```css
--shadow-brand:   0 0 0 3px rgba(99,102,241,0.20), 0 4px 14px 0 rgba(79,70,229,0.25);
--shadow-danger:  0 0 0 3px rgba(239,68,68,0.20), 0 4px 14px 0 rgba(220,38,38,0.25);
--shadow-success: 0 0 0 3px rgba(34,197,94,0.20), 0 4px 14px 0 rgba(22,163,74,0.25);
```

### @keyframes to define

| Name | Trigger | Behavior |
|---|---|---|
| `bid-pulse` | New bid arrives via WebSocket | scale 1→1.04→1, bg flash + `--shadow-brand` glow |
| `outbid-flash` | User is outbid | bg→danger-subtle, border→danger, horizontal shake ±4px, fade back |
| `countdown-tick` | Every second | scale 1→1.1→1; critical: scale 1.15, color→danger |
| `price-roll` | New bid amount | old slides up+out, new slides in from below, `overflow:hidden` container |
| `won-reveal` | Won state | scale 0.8→1.05→1, bg→won-bg, `--shadow-success` glow |
| `toast-slide-in` | Toast enter | translateX from right + opacity 0→1 |
| `toast-slide-out` | Toast exit | reverse |
| `pulse-ring` | Ending-soon continuous | expanding ring fades out, loops 1.5s |

### `@theme inline {}` forwarding
Every `--color-*`, `--shadow-*`, `--font-*`, `--radius-*` token must be forwarded here so Tailwind generates utilities:
```css
@theme inline {
  --color-brand-500: var(--color-brand-500);
  --color-auction-active-bg: var(--color-auction-active-bg);
  /* ... all tokens ... */
}
```

---

## Phase 2 — Core Utilities

### `lib/cn.ts` (or update `lib/utils.ts`)
Already generated by shadcn — `cn()` using clsx + tailwind-merge. No changes needed.

### `lib/design-tokens.ts`
```ts
export enum AuctionStatus {
  Active = 'active',
  EndingSoon = 'ending-soon',
  Critical = 'critical',
  Closed = 'closed',
  Won = 'won',
  Lost = 'lost',
  Outbid = 'outbid',
}

export function getStatusTokens(status: AuctionStatus) {
  // returns { bg, text, border } CSS var references per status
}

export const durations = {
  fast: 80,
  normal: 150,
  moderate: 250,
  slow: 350,
  bidPulse: 600,
  countdown: 1000,
} as const;

export const easings = {
  spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  snappy: 'cubic-bezier(0.2, 0, 0, 1)',
  bounceOut: 'cubic-bezier(0.68, -0.55, 0.27, 1.55)',
} as const;
```

### `lib/format.ts`
```ts
export function formatCurrency(cents: number, locale = 'en-US'): string
export function formatRelativeTime(date: Date): string
export function formatCountdown(seconds: number): string  // "4:59" or "0:23"
```

### `lib/auction-utils.ts`
```ts
export function getAuctionStatus(auction: Auction): AuctionStatus
export function deriveTimerState(endsAt: Date): 'normal' | 'warning' | 'critical'
// warning: < 5 min, critical: < 1 min
```

---

## Phase 3 — Customize shadcn Primitives

### `components/ui/button.tsx`
Add variant `"brand"` as the default primary — uses `--color-brand-500` bg with `--shadow-brand` on hover. Ensure `"destructive"` maps to `--color-danger-default`.

### `components/ui/badge.tsx`
Add 7 auction-state variants: `active`, `ending-soon`, `critical`, `closed`, `won`, `lost`, `outbid`. Each maps to the corresponding `--color-auction-*` tokens.

---

## Phase 4 — Auction Components

All files in `components/auction/`. All require `"use client"` unless noted.

| Component | Key notes |
|---|---|
| `StatusBadge.tsx` | Uses `badge.tsx` auction variants + icons from lucide-react |
| `CountdownTimer.tsx` | Accepts UTC `Date`, calls `useCountdown` hook, 3 visual states (normal/warning/critical), `countdown-tick` animation |
| `CurrentBidDisplay.tsx` | `position:relative; overflow:hidden`, two absolutely-positioned spans for `price-roll`, `bid-pulse` on update, "You're winning" / outbid states |
| `BidInput.tsx` | Extends shadcn `<Input>`, leading "$" adornment, 48px height, focus ring + error state + shake animation |
| `BidButton.tsx` | `<Button variant="brand">`, hover `--shadow-brand`, pressed scale 0.98 |
| `BidForm.tsx` | Composes `BidInput` + `BidButton` + auto-bid collapsible panel |
| `AuctionCard.tsx` | Wraps shadcn `<Card>`, image 4:3 aspect, `StatusBadge` overlay, `CountdownTimer`, mono price, hover translateY(-2px) + shadow |
| `BidHistory.tsx` | Scrollable list max-h 320px, avatar + name + time + amount (mono), current-user highlight, crown on winning bid |
| `AuctionGrid.tsx` | Responsive 1→2→3→4 col grid using container breakpoints, maps `AuctionCard[]` |

### `hooks/useCountdown.ts`
```ts
export function useCountdown(endsAt: Date): {
  secondsLeft: number;
  timerState: 'normal' | 'warning' | 'critical';
  isExpired: boolean;
}
```
Uses `setInterval` with 1s tick, cleans up on unmount.

### `hooks/useAuctionSocket.ts`
```ts
export function useAuctionSocket(auctionId: string): {
  currentBid: number;
  bidHistory: Bid[];
  status: AuctionStatus;
}
```
Connects via `socket.io-client`, subscribes to `auction:${auctionId}` room events: `bid:new`, `auction:status`, `auction:end`.

---

## Phase 5 — Layout Components

All files in `components/layout/`.

### `Header.tsx`
- Desktop: sticky 64px, `--z-index-sticky`; logo (SVG wordmark) | `<SearchBar />` 400px | `<WalletBadge />` + bell + avatar + "Sell" CTA
- Mobile: compressed logo + menu icon; delegates to `<BottomNav />`

### `BottomNav.tsx`
- Mobile only (hidden md+), 5 tabs: Home, Browse, Sell★ (brand accent), My Bids, Account
- Fixed bottom, `--z-index-sticky`, safe-area-inset padding

### `Footer.tsx`
- Simple link grid, copyright; Server Component

### `layout.tsx` updates needed
- Add `DM_Sans` from `next/font/google` with CSS var `--font-dm-sans`
- Wrap `{children}` with `<TooltipProvider>` (shadcn)
- Add `<Toaster />` (Sonner) after `{children}`

---

## Phase 6 — Wallet & Notification Components

### `components/wallet/`
| Component | Notes |
|---|---|
| `WalletBadge.tsx` | Pill in header: wallet icon + mono balance; low-balance warning tint + pulse; `price-roll` on deposit |
| `WalletPanel.tsx` | Sheet/drawer: balance, deposit CTA, `<TransactionRow />` list |
| `TransactionRow.tsx` | Icon + description + date + amount (mono, color-coded positive/negative) |

### `hooks/useWallet.ts`
Zustand store subscription, exposes `{ balance, isLow, deposit(), transactions }`.

### `components/notification/`
| Component | Notes |
|---|---|
| `NotificationBell.tsx` | Bell/BellRing icon + unread count badge in header |
| `NotificationPanel.tsx` | Popover/sheet with notification list |
| `NotificationToast.tsx` | Rendered via Sonner; left border 4px per variant; outbid=danger, won=success, ending=warning; auto-dismiss 8s/12s with progress bar |

### `hooks/useNotifications.ts`
Zustand store + socket subscription for real-time outbid/won/ending notifications.

---

## Phase 7 — Route Pages

### App Router structure
```
app/
├── (public)/
│   ├── page.tsx                    ← Homepage: hero + featured auctions grid
│   └── auctions/
│       ├── page.tsx                ← Browse: filters sidebar + AuctionGrid
│       └── [id]/
│           └── page.tsx            ← Detail: image gallery + bid panel (60/40 split)
├── (auth)/
│   ├── login/page.tsx
│   └── register/page.tsx
└── (dashboard)/
    ├── my-bids/page.tsx
    ├── wallet/page.tsx
    └── profile/page.tsx
```

### Auction Detail layout
- Tablet+: `display:grid; grid-template-columns: 60% 40%`; bid panel `position:sticky; top:80px`
- Mobile: single column; bid panel at bottom (not sticky)
- Components used: image carousel, `CurrentBidDisplay`, `CountdownTimer`, `BidForm`, `BidHistory`, `StatusBadge`

---

## TypeScript Types (`types/`)

```
types/
├── auction.ts   — Auction, Bid, AuctionStatus, BidHistoryItem
├── user.ts      — User, AuthSession
├── wallet.ts    — WalletBalance, Transaction, TransactionType
└── notification.ts — Notification, NotificationType
```

Rule: every API payload and component prop must have an explicit interface. No `any`.

---

## Verification Checklist

- [ ] `npm run dev` — no TypeScript errors, Tailwind generates all token utilities
- [ ] DevTools `:root` — all `--color-*` and `--shadow-*` vars visible
- [ ] Toggle `.dark` class on `<html>` — all tokens flip correctly
- [ ] Resize viewport — auction grid: 1 col (mobile) → 2 (640px) → 3 (1024px) → 4 (1280px)
- [ ] `StatusBadge` renders all 7 variants in light + dark mode
- [ ] `CountdownTimer` transitions normal→warning→critical with correct colors and animations
- [ ] `BidInput` focus ring, error shake, `BidButton` hover glow + press scale
- [ ] `price-roll` animation on `CurrentBidDisplay` when bid updates
- [ ] `NotificationToast` slides in/out, correct border color per variant
- [ ] `WalletBadge` balance updates with `price-roll`
