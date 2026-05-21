# BidNow Design System

## 1. Brand Identity

### Personality
Competitive, trustworthy, fast-paced, premium.

### Wordmark
- "Bid" in medium weight (500) + "Now" in medium weight (500), brand color — brand distinction via color only, not weight
- Light mode: "Bid" → `#111115`, "Now" → `#4F46E5`
- Dark mode: "Bid" → `#F0F0F4`, "Now" → `#8483F5`
- Optional glyph: stylized gavel SVG (32×32 viewBox) left of wordmark
- Minimum display height: 24px

---

## 2. Tooling & Setup

### Stack
| Tool | Role |
|---|---|
| Next.js 15 (App Router) | Framework |
| Tailwind CSS v4 | Utility-first styling |
| shadcn/ui | Accessible component primitives |
| Lucide React | Icon library |
| clsx + tailwind-merge | Class composition (via shadcn's `lib/utils.ts`) |

### shadcn/ui Installation

shadcn/ui is **not a dependency** — it's a CLI that copies component source into your project. Components live in `components/ui/` and are fully owned/editable.

```bash
# Initialize shadcn with Tailwind v4
npx shadcn@latest init

# Install required components (run once, add more as needed)
npx shadcn@latest add button input badge avatar card dialog
npx shadcn@latest add toast sonner tooltip skeleton switch
npx shadcn@latest add select dropdown-menu navigation-menu
npx shadcn@latest add sheet progress separator scroll-area
npx shadcn@latest add form label popover
```

### components.json (shadcn config)

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": true,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "app/globals.css",
    "baseColor": "slate",
    "cssVariables": true
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  }
}
```

### lib/utils.ts (shadcn-generated, do not recreate manually)

```ts
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

---

## 3. Color System

### Strategy: Dual-Layer Tokens

shadcn/ui requires its own CSS variable names (HSL format, no `hsl()` wrapper) to power its components. BidNow extends these with additional auction-domain tokens. Both layers live in `globals.css`.

**Layer 1 — shadcn semantic variables** (required, powers all shadcn components)
**Layer 2 — BidNow extended tokens** (auction states, wallet, brand palette steps)

### Layer 1: shadcn/ui Variables

Map BidNow's indigo brand onto shadcn's naming convention. Values are HSL channels only (no `hsl()` wrapper) — shadcn applies them as `hsl(var(--primary))`.

```css
/* globals.css — light mode */
:root {
  --background:           0 0% 100%;           /* #FFFFFF */
  --foreground:           240 6% 7%;            /* #111115 */

  --card:                 0 0% 100%;            /* #FFFFFF */
  --card-foreground:      240 6% 7%;            /* #111115 */

  --popover:              0 0% 100%;
  --popover-foreground:   240 6% 7%;

  --primary:              244 72% 58%;          /* #4F46E5 — brand indigo */
  --primary-foreground:   0 0% 100%;            /* white text on brand */

  --secondary:            240 5% 96%;           /* #F4F4F8 — subtle bg */
  --secondary-foreground: 240 6% 7%;

  --muted:                240 5% 96%;           /* #F0F0F5 */
  --muted-foreground:     240 4% 46%;           /* #6B6B82 */

  --accent:               239 84% 97%;          /* #EEF2FF — brand-subtle */
  --accent-foreground:    244 72% 58%;          /* brand indigo */

  --destructive:          0 72% 51%;            /* #DC2626 */
  --destructive-foreground: 0 0% 100%;

  --border:               240 6% 90%;           /* #E2E2EC */
  --input:                240 6% 90%;
  --ring:                 244 72% 58%;          /* brand indigo — focus ring */

  --radius:               0.5rem;               /* 8px — base radius for shadcn components */
}

/* Dark mode overrides */
.dark {
  --background:           240 7% 6%;            /* #0F0F0F */
  --foreground:           240 9% 95%;           /* #F0F0F4 */

  --card:                 240 6% 12%;           /* #1E1E24 */
  --card-foreground:      240 9% 95%;

  --popover:              240 6% 12%;
  --popover-foreground:   240 9% 95%;

  --primary:              239 84% 67%;          /* #6366F1 — slightly lighter for dark */
  --primary-foreground:   0 0% 100%;

  --secondary:            240 5% 15%;           /* #242428 */
  --secondary-foreground: 240 9% 95%;

  --muted:                240 5% 15%;
  --muted-foreground:     240 4% 56%;           /* #A0A0B0 */

  --accent:               244 59% 20%;          /* #1E1B4B — dark brand-subtle */
  --accent-foreground:    239 84% 67%;

  --destructive:          0 84% 60%;            /* #EF4444 */
  --destructive-foreground: 0 0% 100%;

  --border:               240 6% 20%;           /* #2A2A35 */
  --input:                240 6% 20%;
  --ring:                 239 84% 67%;
}
```

> The `.dark` class approach is used instead of `@media (prefers-color-scheme: dark)` so a manual theme toggle can be supported alongside OS preference. Apply the class to `<html>` in `layout.tsx`.

### Layer 2: BidNow Extended Tokens

These complement shadcn's variables. They use a `hsl()` wrapper for compatibility with Tailwind v4's `@theme inline`.

#### Brand Palette (raw HSL steps)
| Token | HSL | Hex equiv | Usage |
|---|---|---|---|
| `--brand-50` | `239 100% 97%` | `#EEEFFE` | Tint backgrounds |
| `--brand-100` | `239 93% 92%` | `#D5D6FC` | Hover fills |
| `--brand-200` | `239 87% 83%` | `#ADADF9` | Borders on brand |
| `--brand-300` | `239 86% 73%` | `#8483F5` | Disabled primary |
| `--brand-400` | `239 84% 67%` | `#6366F1` | Dark mode interactive |
| `--brand-500` | `244 72% 58%` | `#4F46E5` | **PRIMARY (light mode)** |
| `--brand-600` | `244 48% 41%` | `#3730A3` | Pressed/active |
| `--brand-700` | `244 46% 33%` | `#2E2A80` | Heavy emphasis |
| `--brand-800` | `244 45% 20%` | `#1E1B4B` | Dark backgrounds |
| `--brand-900` | `244 45% 15%` | `#13113B` | Near-black brand |

#### Surface / Background
| Token | Light | Dark |
|---|---|---|
| `--color-bg-base` | `#FFFFFF` | `#0F0F0F` |
| `--color-bg-elevated` | `#F9F9FB` | `#1A1A1F` |
| `--color-bg-overlay` | `#F3F3F7` | `#242428` |
| `--color-bg-card` | `#FFFFFF` | `#1E1E24` |
| `--color-bg-card-hover` | `#F5F5FF` | `#25252E` |
| `--color-bg-modal` | `rgba(255,255,255,0.97)` | `rgba(18,18,22,0.97)` |
| `--color-bg-backdrop` | `rgba(0,0,0,0.4)` | `rgba(0,0,0,0.6)` |
| `--color-bg-sidebar` | `#F4F4F8` | `#16161B` |

#### Text
| Token | Light | Dark |
|---|---|---|
| `--color-text-primary` | `#111115` | `#F0F0F4` |
| `--color-text-secondary` | `#52525C` | `#A0A0B0` |
| `--color-text-tertiary` | `#8B8B9A` | `#6A6A7E` |
| `--color-text-disabled` | `#C2C2D0` | `#3A3A4A` |
| `--color-text-inverse` | `#FFFFFF` | `#0F0F0F` |
| `--color-text-brand` | `#4F46E5` | `#8483F5` |
| `--color-text-link` | `#4F46E5` | `#8483F5` |
| `--color-text-link-hover` | `#3730A3` | `#A5A4F8` |

#### Semantic States
| Token | Light | Dark |
|---|---|---|
| `--color-success-default` | `#16A34A` | `#22C55E` |
| `--color-success-subtle` | `#F0FDF4` | `#052E16` |
| `--color-success-border` | `#BBF7D0` | `#166534` |
| `--color-success-text` | `#14532D` | `#86EFAC` |
| `--color-warning-default` | `#D97706` | `#F59E0B` |
| `--color-warning-subtle` | `#FFFBEB` | `#1C1200` |
| `--color-warning-border` | `#FDE68A` | `#78350F` |
| `--color-warning-text` | `#92400E` | `#FCD34D` |
| `--color-danger-default` | `#DC2626` | `#EF4444` |
| `--color-danger-subtle` | `#FEF2F2` | `#1C0000` |
| `--color-danger-border` | `#FECACA` | `#7F1D1D` |
| `--color-danger-text` | `#7F1D1D` | `#FCA5A5` |
| `--color-info-default` | `#0284C7` | `#38BDF8` |
| `--color-info-subtle` | `#F0F9FF` | `#001B2E` |
| `--color-info-border` | `#BAE6FD` | `#0369A1` |
| `--color-info-text` | `#075985` | `#7DD3FC` |

#### Neutral Scale
| Token | Value |
|---|---|
| `--color-neutral-0` | `#FFFFFF` |
| `--color-neutral-50` | `#F8F8FC` |
| `--color-neutral-100` | `#F0F0F5` |
| `--color-neutral-200` | `#E2E2EC` |
| `--color-neutral-300` | `#C8C8D8` |
| `--color-neutral-400` | `#9898AE` |
| `--color-neutral-500` | `#6B6B82` |
| `--color-neutral-600` | `#52525C` |
| `--color-neutral-700` | `#3A3A4A` |
| `--color-neutral-800` | `#27272F` |
| `--color-neutral-900` | `#111115` |
| `--color-neutral-950` | `#08080A` |

#### Border
| Token | Light | Dark |
|---|---|---|
| `--color-border-default` | `#E2E2EC` | `#2A2A35` |
| `--color-border-strong` | `#C8C8D8` | `#3A3A4A` |
| `--color-border-focus` | `#6366F1` | `#6366F1` |
| `--color-border-error` | `#DC2626` | `#EF4444` |

#### Auction-Specific State Tokens
| Status | Token suffix | bg / text / border (light) | bg / text / border (dark) |
|---|---|---|---|
| Active | `--color-auction-active-*` | `#F0F9FF` / `#0284C7` / `#BAE6FD` | `#001B2E` / `#38BDF8` / `#0369A1` |
| Ending Soon | `--color-auction-ending-*` | `#FFFBEB` / `#B45309` / `#FDE68A` | `#1C1200` / `#FCD34D` / `#78350F` |
| Critical | `--color-auction-critical-*` | `#FEF2F2` / `#B91C1C` / `#FECACA` | `#1C0000` / `#F87171` / `#7F1D1D` |
| Closed | `--color-auction-closed-*` | `#F0F0F5` / `#6B6B82` / `#E2E2EC` | `#1A1A1F` / `#6A6A7E` / `#2A2A35` |
| Won | `--color-auction-won-*` | `#F0FDF4` / `#14532D` / `#BBF7D0` | `#052E16` / `#86EFAC` / `#166534` |
| Lost | `--color-auction-lost-*` | `#FEF2F2` / `#7F1D1D` / `#FECACA` | `#1C0000` / `#FCA5A5` / `#7F1D1D` |
| Outbid | `--color-auction-outbid-*` | `#FFF7ED` / `#9A3412` / `#FDBA74` | `#180900` / `#FB923C` / `#7C2D12` |

Each status also has `--color-auction-{status}-accent` for glow (e.g. `won-accent: #16A34A`, `ending-pulse: #F59E0B`).

#### Wallet Tokens
| Token | Light | Dark |
|---|---|---|
| `--color-wallet-bg` | `#F0F9FF` | `#001B2E` |
| `--color-wallet-text` | `#0369A1` | `#38BDF8` |
| `--color-wallet-icon` | `#0284C7` | `#7DD3FC` |
| `--color-wallet-positive` | `#16A34A` | `#22C55E` |
| `--color-wallet-negative` | `#DC2626` | `#EF4444` |

#### Shadow Tokens

> **Tesla migration:** All box-shadows are `none`. Depth is communicated through z-index layering and opacity-based transparency only — never box-shadow. Do not use `shadow-*` Tailwind utilities in components.

```css
/* All box-shadows intentionally zeroed — Tesla flat aesthetic */
--shadow-none:    none;
--shadow-xs:      none;
--shadow-sm:      none;
--shadow-md:      none;
--shadow-lg:      none;
--shadow-xl:      none;
--shadow-2xl:     none;
--shadow-inner:   none;
--shadow-brand:   none;
--shadow-danger:  none;
--shadow-success: none;
/* Opacity-based depth tokens (used as background-color values, not box-shadow) */
--shadow-frosted:  rgba(255,255,255,0.75);   /* frosted glass nav backdrop */
--shadow-overlay:  rgba(128,128,128,0.65);   /* modal/dialog overlay */
```

#### Backdrop Blur
```css
--blur-sm: blur(4px);
--blur-md: blur(8px);
--blur-lg: blur(16px);
--blur-xl: blur(24px);
```

---

## 4. Typography

### Font Stack
| Token | Value |
|---|---|
| `--font-sans` | `var(--font-geist-sans), ui-sans-serif, system-ui, sans-serif` |
| `--font-mono` | `var(--font-geist-mono), ui-monospace, 'Cascadia Code', monospace` |
| `--font-display` | `var(--font-dm-sans), var(--font-geist-sans), sans-serif` |

- **Geist Sans** — UI labels, body, descriptions (already loaded)
- **Geist Mono** — all prices, bid amounts, wallet balances, countdowns (prevents width-jump on digit change)
- **DM Sans** — display headings, auction titles, hero text (add via `next/font/google`)

> Rule: every price, bid amount, wallet balance, and countdown number uses `font-mono`.

### Type Scale
| Token | rem | px | Usage |
|---|---|---|---|
| `--font-size-2xs` | `0.625rem` | 10px | Micro badges, fine print |
| `--font-size-xs` | `0.75rem` | 12px | Captions, timestamps |
| `--font-size-sm` | `0.875rem` | 14px | Secondary body, labels |
| `--font-size-base` | `1rem` | 16px | Primary body |
| `--font-size-md` | `1.125rem` | 18px | Large body, card descriptions |
| `--font-size-lg` | `1.25rem` | 20px | Subheadings, section titles |
| `--font-size-xl` | `1.5rem` | 24px | Heading 4, card titles |
| `--font-size-2xl` | `1.875rem` | 30px | Heading 3 |
| `--font-size-3xl` | `2.25rem` | 36px | Heading 2 |
| `--font-size-4xl` | `3rem` | 48px | Heading 1 |
| `--font-size-5xl` | `3.75rem` | 60px | Display large |
| `--font-size-6xl` | `4.5rem` | 72px | Display XL |
| `--font-size-price-sm` | `1.5rem` | 24px | Card grid price |
| `--font-size-price-md` | `2.25rem` | 36px | Detail page current bid |
| `--font-size-price-lg` | `3rem` | 48px | Featured auction hero price |
| `--font-size-timer-sm` | `1.25rem` | 20px | Card countdown |
| `--font-size-timer-md` | `2rem` | 32px | Detail page countdown |
| `--font-size-timer-lg` | `3rem` | 48px | Ending-soon banner |

### Font Weight

> **Tesla migration:** Maximum weight is **500 (medium)**. Never use `font-semibold` (600), `font-bold` (700), or `font-extrabold` (800) in components. These utilities are banned.

| Token | Value |
|---|---|
| `--font-weight-regular` | `400` |
| `--font-weight-medium` | `500` |

### Line Height
| Token | Value | Usage |
|---|---|---|
| `--line-height-none` | `1` | Mono numbers, precise spacing |
| `--line-height-tight` | `1.2` | Display text, prices, timers |
| `--line-height-snug` | `1.35` | Headings |
| `--line-height-normal` | `1.5` | Body text |
| `--line-height-relaxed` | `1.625` | Long-form descriptions |

### Letter Spacing

> **Tesla migration:** `letter-spacing: 0` everywhere. Never use `tracking-wide`, `tracking-wider`, `tracking-tight`, `tracking-tighter`, or `tracking-widest` in components. Tailwind's `tracking-normal` (0) is the only permitted value. The tokens below are defined in CSS but unused in components.

| Token | Value |
|---|---|
| `--letter-spacing-normal` | `0em` | Default — the only value used |

---

## 5. Spacing & Layout

**Base unit: 4px.** All tokens are multiples of 4px.

### Spacing Scale
```
0, px(1px), 0.5(2px), 1(4px), 1.5(6px), 2(8px), 2.5(10px), 3(12px), 3.5(14px),
4(16px), 5(20px), 6(24px), 7(28px), 8(32px), 9(36px), 10(40px), 11(44px), 12(48px),
14(56px), 16(64px), 20(80px), 24(96px), 28(112px), 32(128px), 40(160px), 48(192px), 64(256px)
```

### Container Widths
| Token | Value | Usage |
|---|---|---|
| `--container-xs` | `480px` | Mobile max readable width |
| `--container-sm` | `640px` | Small tablet |
| `--container-md` | `768px` | Tablet |
| `--container-lg` | `1024px` | Small desktop |
| `--container-xl` | `1280px` | Standard desktop |
| `--container-2xl` | `1440px` | Wide desktop |
| `--container-auction-grid` | `1200px` | Auction listing grid max-width |
| `--container-auction-detail` | `960px` | Single auction detail page |
| `--container-sidebar` | `320px` | Bid history / filter sidebar |

### Auction Grid Breakpoints
| Viewport | Columns | Gap |
|---|---|---|
| < 640px | 1 | — |
| 640px – 1023px | 2 | 16px |
| 1024px – 1279px | 3 | 20px |
| 1280px+ | 4 | 24px |

### Auction Detail Page Layout
- Mobile: single column stack
- Tablet+: images + details 60% / bid panel 40%
- Desktop: images + details 65% / bid panel 35% (sticky)

### Z-Index Scale
| Token | Value | Usage |
|---|---|---|
| `--z-index-base` | `0` | Default flow |
| `--z-index-raised` | `10` | Elevated cards |
| `--z-index-dropdown` | `100` | Dropdown menus |
| `--z-index-sticky` | `200` | Sticky header |
| `--z-index-overlay` | `300` | Page overlays |
| `--z-index-modal` | `400` | Modals |
| `--z-index-toast` | `500` | Toast notifications |
| `--z-index-tooltip` | `600` | Tooltips |

---

## 6. Border & Radius

### Border Radius

> **Tesla migration:** `--radius` is set to `4px` (Tesla's button radius). `rounded-md` (4px) is the standard for buttons and inputs. `rounded-xl` (12px) for larger containers. No large-radius pill buttons.

shadcn's `--radius` drives its components. BidNow extends with a full scale:

| Token | Value | shadcn mapping | Usage |
|---|---|---|---|
| `--radius-none` | `0px` | — | Square elements |
| `--radius-xs` | `2px` | — | Micro badges |
| `--radius-sm` | `2px` | `calc(var(--radius) - 2px)` | — |
| `--radius-md` | `4px` | `var(--radius)` | **shadcn base** — buttons, inputs |
| `--radius-lg` | `8px` | `calc(var(--radius) + 4px)` | Cards |
| `--radius-xl` | `12px` | `calc(var(--radius) + 8px)` | Large cards, panels, category cards |
| `--radius-2xl` | `16px` | — | Modals |
| `--radius-3xl` | `24px` | — | Hero cards |
| `--radius-full` | `9999px` | — | Pills, avatars, tags |

### Border Width
| Token | Value | Usage |
|---|---|---|
| `--border-0` | `0px` | No border |
| `--border-1` | `1px` | Default borders |
| `--border-2` | `2px` | Focus rings, active states |
| `--border-4` | `4px` | Error borders, toast accents |

---

## 7. Motion & Animation

### Duration Tokens
| Token | Value | Usage |
|---|---|---|
| `--duration-instant` | `0ms` | No animation |
| `--duration-fast` | `80ms` | Micro-interactions |
| `--duration-normal` | `150ms` | Default hover/focus |
| `--duration-moderate` | `250ms` | Dropdown, tooltip |
| `--duration-slow` | `350ms` | Modal open, page transitions |
| `--duration-extra-slow` | `500ms` | Won/lost reveal |
| `--duration-bid-pulse` | `600ms` | Bid landed pulse |
| `--duration-countdown` | `1000ms` | Countdown tick |
| **`--duration-tesla`** | **`333ms`** | **Standard for all interactive transitions** |

### Easing Tokens
| Token | Value | Usage |
|---|---|---|
| `--ease-linear` | `linear` | Progress bars |
| `--ease-in` | `cubic-bezier(0.4, 0, 1, 1)` | Exit animations |
| `--ease-out` | `cubic-bezier(0, 0, 0.2, 1)` | Enter animations |
| `--ease-in-out` | `cubic-bezier(0.4, 0, 0.2, 1)` | Default transitions |
| `--ease-spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Price number roll-up |
| `--ease-bounce-out` | `cubic-bezier(0.68, -0.55, 0.27, 1.55)` | Bounce effects |
| `--ease-snappy` | `cubic-bezier(0.2, 0, 0, 1)` | Elevation changes |
| **`--ease-tesla`** | **`cubic-bezier(0.5, 0, 0, 0.75)`** | **Standard for all interactive transitions** |

> **Transition standard:** All interactive state transitions use `transition-[specific-properties] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]`. Never use `transition-all` or hardcoded `duration-150`/`duration-300`. Specify only the properties that actually change (e.g., `background-color,border-color,color`).

### Keyframe Animations
| Name | Trigger | Behavior |
|---|---|---|
| `bid-pulse` | New bid via WebSocket on `CurrentBidDisplay` | scale 1→1.04→1, brief brand-subtle bg flash + `--shadow-brand` glow |
| `outbid-flash` | User outbid | bg→danger-subtle, border→danger, shake ±4px, fade back |
| `countdown-tick` | Each second on `CountdownTimer` | scale 1→1.1→1; crossfade warning/critical colors |
| `price-roll` | New bid amount | old slides up+out, new slides in from below, `overflow:hidden` container |
| `won-reveal` | Won state applied | scale 0.8→1.05→1, bg→won-bg, `--shadow-success` glow |
| `toast-slide-in/out` | Sonner toast enter/exit | translateX from right edge, opacity 0→1 (or let Sonner handle it) |
| `pulse-ring` | Continuous, ending-soon auctions | Expanding ring pseudo-element fades out, loops 1.5s |

---

## 8. Component Patterns

### Component Ownership

| Component | Source | Notes |
|---|---|---|
| Button | **shadcn/ui** | Customize `default` variant to use brand `--primary` |
| Input | **shadcn/ui** | Extend with currency adornment wrapper |
| Badge | **shadcn/ui** | Add auction-specific variants (`active`, `ending-soon`, etc.) |
| Avatar | **shadcn/ui** | Extend with online indicator and hash-based fallback color |
| Card | **shadcn/ui** | Use as base for `AuctionCard`; add hover animations |
| Dialog | **shadcn/ui** | Used for bid confirm modal, wallet panel |
| Sheet | **shadcn/ui** | Mobile bid panel, mobile wallet panel |
| Toast / Sonner | **shadcn/ui** | Use Sonner variant; customize per notification type |
| Tooltip | **shadcn/ui** | Bid increment hints, price explanation |
| Skeleton | **shadcn/ui** | Loading states for AuctionCard, BidHistory |
| Switch | **shadcn/ui** | Auto-bid toggle |
| Select | **shadcn/ui** | Category filter, sort order |
| DropdownMenu | **shadcn/ui** | User avatar menu, auction options |
| Progress | **shadcn/ui** | Toast auto-dismiss bar |
| ScrollArea | **shadcn/ui** | BidHistory scroll container |
| Separator | **shadcn/ui** | Section dividers |
| Form + Label | **shadcn/ui** | BidInput form, auth forms |
| NavigationMenu | **shadcn/ui** | Desktop header navigation |
| Popover | **shadcn/ui** | Filter panels, date pickers |
| CountdownTimer | **Custom** | No shadcn equivalent |
| CurrentBidDisplay | **Custom** | Real-time price with animation |
| BidHistory | **Custom** | Composes ScrollArea + Avatar |
| StatusBadge | **Custom** | Extends shadcn Badge with auction variants |
| AuctionCard | **Custom** | Composes shadcn Card + custom elements |
| WalletBadge | **Custom** | Header wallet display |
| NotificationBell | **Custom** | Composes shadcn Badge for unread count |
| BidForm | **Custom** | Composes shadcn Input + Button + Switch |
| AuctionGrid | **Custom** | Responsive grid layout |
| Header / BottomNav | **Custom** | App shell, composes shadcn NavigationMenu |

### AuctionCard

Built on top of shadcn `<Card>`:
- Card transition: `transition-[border-color,opacity] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]` — no hover lift, no shadow
- Image: aspect-ratio 4/3, `rounded-t-xl object-cover`; `grayscale-[60%]` when closed; `transition-transform duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] group-hover:scale-[1.02]`
- Status badge: absolute top-left, `<StatusBadge>` component
- "Ending soon" banner: full-width bottom of image, `bg-[--color-auction-ending-bg]/90 backdrop-blur-sm`
- Price: `font-mono text-[--font-size-price-sm] font-medium`
- CTA: shadcn `<Button>` — `variant="brand"` ("Place Bid") or `variant="outline"` ("View Item")
- Min card width: 240px / Max: 360px

### BidInput + BidButton

**BidInput** — extends shadcn `<Input>`:
- Wrap in a relative container with a leading "$" span (`text-muted-foreground`)
- Add `pl-7` to the input for adornment offset
- Override font to `font-mono font-medium`
- Error state: `aria-invalid` + shadcn `<FormMessage>` for helper text
- Optional ±steppers: shadcn `<Button variant="ghost" size="icon">` at right

**BidButton** — shadcn `<Button variant="brand">`:
- `className="h-12 w-full font-medium"` (48px height)
- No hover shadow (all shadows are `none`)
- Active: `active:scale-[0.98]`
- Loading: shadcn spinner pattern — `disabled` + `<Loader2 className="animate-spin" />`
- Dynamic label: "Place Bid" → "Confirm $X.XX" on input change

**Auto-Bid Panel**:
- shadcn `<Switch>` for toggle
- Collapsible using CSS `max-height` transition or shadcn `<Collapsible>`
- Max-bid input: same BidInput styling

### CountdownTimer

Three states — all custom (no shadcn equivalent):

| State | Trigger | Tailwind classes |
|---|---|---|
| Normal | > 5 min | `font-mono font-medium text-muted-foreground` |
| Warning | < 5 min, > 1 min | `font-mono font-medium text-[--color-warning-text] bg-[--color-auction-ending-bg] rounded-full px-2 py-0.5` + `pulse-ring` |
| Critical | < 1 min | `font-mono font-medium text-[--color-danger-text] bg-[--color-auction-critical-bg] border-2 border-[--color-auction-critical-border] rounded-full px-2 py-0.5` |

Format: `HH:MM:SS` (< 1 day), `Xd Xh` (longer). Anti-snipe label at critical: `"⚡ Time extends if you bid"` in `text-2xs`.

### CurrentBidDisplay

- Label: `"CURRENT BID"` — `text-xs text-muted-foreground uppercase` (no tracking)
- Price: `font-mono font-medium text-[--font-size-price-md] text-foreground`
- On new bid: `bid-pulse` + `price-roll` animations
- "You're winning" pill: shadcn `<Badge>` with custom `won` variant
- Outbid state: `outbid-flash` animation
- DOM: `relative overflow-hidden` wrapper with two `absolute` spans for `price-roll`

### BidHistory List

- Container: shadcn `<ScrollArea className="max-h-80">`
- Rows: flex — shadcn `<Avatar>` (sm) | name + relative time | amount
- Amount: `font-mono font-medium`
- Current user rows: `bg-accent`
- Winning bid: `text-[--color-success-text]` + `<Trophy className="h-3 w-3" />`
- Auto-bid rows: `<Bot className="h-3 w-3 text-muted-foreground" />`
- Dividers: shadcn `<Separator>`
- "Load more": shadcn `<Button variant="link" size="sm">`

### WalletBadge (Header)

- Pill: `<Button variant="ghost">` styled as pill with wallet icon + mono balance
- Low balance: swap to `text-[--color-warning-text] bg-[--color-warning-subtle]` + pulse
- Deposit animation: `price-roll` on balance number
- Click: opens shadcn `<Sheet side="right">` (WalletPanel)

### StatusBadge

Extends shadcn `<Badge>` with `variant` prop mapped to auction states.

Add these variants in `components/ui/badge.tsx` using `cva`:

```
active      → bg-[--color-auction-active-bg] text-[--color-auction-active-text] border-[--color-auction-active-border]
ending-soon → bg-[--color-auction-ending-bg] text-[--color-auction-ending-text] ...
critical    → bg-[--color-auction-critical-bg] text-[--color-auction-critical-text] ...
closed      → bg-[--color-auction-closed-bg] text-[--color-auction-closed-text] ...
won         → bg-[--color-auction-won-bg] text-[--color-auction-won-text] ...
lost        → bg-[--color-auction-lost-bg] text-[--color-auction-lost-text] ...
outbid      → bg-[--color-auction-outbid-bg] text-[--color-auction-outbid-text] ...
```

All variants: `text-xs font-medium uppercase rounded-full px-2 py-0.5 border` (no tracking).
`active` badge includes an animated pulsing dot via CSS `animate-pulse` on a small `<span>`.

### NotificationToast

Use **Sonner** (shadcn's recommended toast library via `npx shadcn@latest add sonner`):

- `<Toaster position="bottom-right" />` in root layout
- Custom `toast()` call per variant:
  - Outbid: `toast.error()` with custom JSX, left accent `border-l-4 border-destructive`
  - Won: `toast.success()` with Trophy icon, `border-l-4 border-[--color-auction-won-accent]`
  - Ending soon: `toast.warning()` with Timer icon
- shadcn `<Progress>` component for auto-dismiss countdown bar
- Auto-dismiss: 8s (won), 12s (outbid)

### UserAvatar

Built on shadcn `<Avatar>`:
- `<AvatarImage>` for photo
- `<AvatarFallback>` for 2-letter initials with hash-based background color
- Online indicator: absolute 8px `<span>` bottom-right, `bg-green-500 border-2 border-background rounded-full`
- Sizes via `className`: `h-5 w-5` (xs) through `h-16 w-16` (xl)

### Header / Navigation

**Desktop (> 1024px):**
- Sticky `<header>` — `h-16 bg-background border-b border-border sticky top-0 z-[--z-index-sticky]`
- Scrolled: `backdrop-blur-sm` (add via scroll listener)
- shadcn `<NavigationMenu>` for nav links
- Center: shadcn `<Input>` with `<Search>` icon adornment, `max-w-[400px]`
- Right: `<WalletBadge>` + `<NotificationBell>` + shadcn `<DropdownMenu>` (avatar) + `<Button>` ("Sell")

**Mobile (< 1024px):**
- Compressed top bar; icon-only right side
- Bottom nav: fixed `h-14 bg-background border-t border-border` with 5 tabs
- Center "Sell" tab: `<Button variant="brand" size="icon" className="rounded-full -mt-4">` (no shadow)
- Safe area: `pb-[env(safe-area-inset-bottom)]`

---

## 9. Icons

**Library: Lucide React** (`npm install lucide-react`)

Rationale: tree-shakeable, SVG strokes at 1.5px, 1000+ icons, React-first, TypeScript included. Already integrated by shadcn/ui (shadcn uses Lucide internally for its own icons like `ChevronDown`, `X`, `Check`).

### Required Icons for BidNow
| Icon | Component | Usage |
|---|---|---|
| Gavel | `Gavel` | Primary auction icon, logo glyph |
| Trophy | `Trophy` | Won state, bid history leader |
| Timer | `Timer` | Countdown, ending soon |
| Clock | `Clock` | Countdown normal state |
| AlertTriangle | `AlertTriangle` | Critical time, warnings |
| Wallet | `Wallet` | Wallet badge |
| ArrowUp | `ArrowUp` | Bid placed, price increase |
| ArrowDown | `ArrowDown` | Outbid state |
| ChevronUp | `ChevronUp` | Increment bid |
| ChevronDown | `ChevronDown` | Decrement bid |
| Bell | `Bell` | Notifications |
| BellRing | `BellRing` | Active notification |
| Zap | `Zap` | Auto-bid feature |
| Bot | `Bot` | Auto-bid indicator in history |
| CircleDollarSign | `CircleDollarSign` | Prices, wallet deposits |
| Tag | `Tag` | Category, Buy It Now |
| Search | `Search` | Search bar |
| Filter | `Filter` | Auction filters |
| Grid2X2 | `Grid2X2` | Grid view toggle |
| List | `List` | List view toggle |
| Eye | `Eye` | Watching an auction |
| Heart | `Heart` | Saved / watchlist |
| Share2 | `Share2` | Share auction |
| Lock | `Lock` | Closed auction |
| CheckCircle | `CheckCircle` | Successful bid / payment |
| XCircle | `XCircle` | Failed / lost state |
| Loader2 | `Loader2` | Loading spinner (`animate-spin`) |
| User | `User` | User profile |
| LogOut | `LogOut` | Sign out |
| Plus | `Plus` | Create auction |
| Image | `Image` | Image upload |
| Package | `Package` | Item / product |
| Star | `Star` | Featured auction |
| Flame | `Flame` | Hot / trending |
| TrendingUp | `TrendingUp` | Bid history chart |
| History | `History` | Transaction history |
| RefreshCw | `RefreshCw` | Refresh live data |
| Radio | `Radio` | Live / active dot |
| Shield | `Shield` | Security, verified seller |
| Info | `Info` | Tooltips, help |
| ChevronRight | `ChevronRight` | Navigation arrows |
| Menu | `Menu` | Mobile hamburger |
| X | `X` | Close, dismiss |

---

## 10. File & Folder Structure

```
frontend/
├── app/
│   ├── globals.css                    ← shadcn vars + BidNow extended tokens + @keyframes
│   ├── layout.tsx                     ← Add DM_Sans, ThemeProvider, <Toaster />
│   ├── (public)/
│   │   ├── layout.tsx
│   │   ├── auctions/
│   │   │   ├── page.tsx               ← Browse / listing
│   │   │   └── [id]/page.tsx          ← Auction detail
│   │   └── search/page.tsx
│   ├── (auth)/
│   │   ├── layout.tsx
│   │   ├── login/page.tsx
│   │   ├── register/page.tsx
│   │   └── forgot-password/page.tsx
│   └── (dashboard)/
│       ├── layout.tsx
│       ├── my-bids/page.tsx
│       ├── my-auctions/page.tsx
│       ├── wallet/page.tsx
│       ├── notifications/page.tsx
│       └── profile/page.tsx
│
├── components/
│   ├── ui/                            ← shadcn-generated (DO NOT hand-write; use CLI)
│   │   ├── button.tsx                 ← Customize default variant → brand primary
│   │   ├── input.tsx
│   │   ├── badge.tsx                  ← Add auction-state variants here
│   │   ├── avatar.tsx
│   │   ├── card.tsx
│   │   ├── dialog.tsx
│   │   ├── sheet.tsx
│   │   ├── sonner.tsx                 ← Toast via Sonner
│   │   ├── tooltip.tsx
│   │   ├── skeleton.tsx
│   │   ├── switch.tsx
│   │   ├── select.tsx
│   │   ├── dropdown-menu.tsx
│   │   ├── navigation-menu.tsx
│   │   ├── progress.tsx
│   │   ├── scroll-area.tsx
│   │   ├── separator.tsx
│   │   ├── form.tsx
│   │   ├── label.tsx
│   │   ├── popover.tsx
│   │   └── ...
│   │
│   ├── auction/                       ← Custom auction-domain components
│   │   ├── AuctionCard/
│   │   ├── BidForm/                   ← BidInput + BidButton + AutoBidPanel
│   │   ├── CountdownTimer/
│   │   ├── CurrentBidDisplay/
│   │   ├── BidHistory/
│   │   ├── StatusBadge/               ← Extends shadcn Badge
│   │   ├── AuctionGrid/
│   │   ├── AuctionDetail/
│   │   └── index.ts
│   │
│   ├── wallet/
│   │   ├── WalletBadge/
│   │   ├── WalletPanel/               ← Uses shadcn Sheet
│   │   ├── TransactionRow/
│   │   └── index.ts
│   │
│   ├── notification/
│   │   ├── NotificationBell/          ← Uses shadcn Badge for unread count
│   │   ├── NotificationPanel/         ← Uses shadcn Sheet
│   │   └── index.ts
│   │
│   ├── layout/
│   │   ├── Header/                    ← Uses shadcn NavigationMenu, DropdownMenu
│   │   ├── BottomNav/
│   │   ├── Sidebar/
│   │   ├── Footer/
│   │   └── index.ts
│   │
│   └── shared/
│       ├── UserAvatar/                ← Extends shadcn Avatar
│       ├── SearchBar/                 ← Extends shadcn Input
│       ├── EmptyState/
│       ├── ErrorBoundary/
│       └── index.ts
│
├── lib/
│   ├── utils.ts                       ← shadcn-generated (cn function)
│   ├── design-tokens.ts               ← Typed CSS var refs + AuctionStatus helpers
│   ├── auction-utils.ts               ← getAuctionStatus(), deriveTimerState()
│   └── format.ts                      ← formatCurrency(), formatRelativeTime()
│
├── hooks/
│   ├── useCountdown.ts                ← Accepts UTC Date from server
│   ├── useAuctionSocket.ts
│   ├── useWallet.ts
│   └── useNotifications.ts
│
├── types/
│   ├── auction.ts
│   ├── user.ts
│   ├── wallet.ts
│   └── notification.ts
│
├── store/
│   ├── auctionStore.ts
│   ├── walletStore.ts
│   └── notificationStore.ts
│
└── components.json                    ← shadcn configuration
```

---

## 11. globals.css Structure

```
1.  @import "tailwindcss"

2.  :root { /* shadcn Layer 1 — HSL channels, light mode */
      --background, --foreground, --card, --card-foreground,
      --popover, --popover-foreground, --primary, --primary-foreground,
      --secondary, --secondary-foreground, --muted, --muted-foreground,
      --accent, --accent-foreground, --destructive, --destructive-foreground,
      --border, --input, --ring, --radius
    }

3.  .dark { /* shadcn dark overrides */ }

4.  :root { /* BidNow Layer 2 — extended tokens (light) */
      --brand-50 ... --brand-900,
      --color-bg-*, --color-text-*, --color-border-*,
      --color-success-*, --color-warning-*, --color-danger-*, --color-info-*,
      --color-neutral-*, --color-auction-*, --color-wallet-*,
      --shadow-*, --blur-*
    }

5.  .dark { /* BidNow dark overrides for Layer 2 */ }

6.  @theme inline {
      /* Forward all BidNow tokens as Tailwind utilities */
      /* shadcn vars are auto-used via hsl(var(--primary)) etc. */
    }

7.  @keyframes {
      bid-pulse, outbid-flash, countdown-tick,
      price-roll-out, price-roll-in, won-reveal,
      pulse-ring
    }

8.  /* Global base styles */
    body { font-family: var(--font-sans); background: hsl(var(--background)); color: hsl(var(--foreground)); }
    * { box-sizing: border-box; }
```

---

## 12. lib/design-tokens.ts Typed Interface

Exports:
- `colors` — object mapping semantic names to CSS var references
- `durations` — numeric ms values for `setTimeout` / motion libraries
- `easings` — cubic-bezier strings for Framer Motion or Web Animations API
- `AuctionStatus` enum: `Active | EndingSoon | Critical | Closed | Won | Lost | Outbid`
- `getStatusTokens(status: AuctionStatus)` → `{ bg, text, border }` triple — used by `StatusBadge` and `AuctionCard`
- `formatCurrency(value: number, locale?: string)` — respects app currency setting

---

## 13. Implementation Sequence

| Phase | Deliverable | Notes |
|---|---|---|
| 1 | shadcn init + install components | Run CLI commands from §2; generates `components/ui/`, `lib/utils.ts`, `components.json` |
| 2 | Token foundation | Write `globals.css` with both shadcn vars and BidNow extended tokens |
| 3 | Customize shadcn primitives | Update `button.tsx` (brand primary), `badge.tsx` (add auction variants) |
| 4 | Core utilities | `lib/design-tokens.ts`, `lib/format.ts`, `lib/auction-utils.ts` |
| 5 | Auction components | `CountdownTimer` → `StatusBadge` → `CurrentBidDisplay` → `BidForm` → `AuctionCard` → `BidHistory` → `AuctionGrid` |
| 6 | Layout components | `Header` → `BottomNav` → `Sidebar` → `Footer` |
| 7 | Domain-specific | `WalletBadge` → `WalletPanel` → `NotificationBell` → `NotificationPanel` |
| 8 | Route pages | Wire `app/(public|auth|dashboard)/*` using composed components |

---

## 14. Key Implementation Constraints

- **`components/ui/` is shadcn territory** — always add components via `npx shadcn@latest add <name>`, never hand-write files there. Extend or compose in `components/auction/`, `components/wallet/`, etc.
- **shadcn color vars use HSL channels** — apply as `hsl(var(--primary))`, not bare `var(--primary)`. BidNow extended tokens use full `#hex` values and are applied as `var(--color-auction-active-bg)`.
- **Theme toggle** — apply `.dark` class to `<html>` tag. Use a `ThemeProvider` wrapper in `layout.tsx` to manage this and respect `prefers-color-scheme` as the default.
- **`useCountdown`** must accept a UTC `Date` from the server — never derive from local time.
- **`CurrentBidDisplay`** requires `position:relative; overflow:hidden` wrapper with two absolutely-positioned spans for `price-roll` animation.
- **Server vs Client**: `BidForm`, `CountdownTimer`, Sonner `<Toaster>`, `WalletBadge`, any WebSocket consumer → `"use client"`. shadcn `Card`, `Badge`, `Avatar`, `Skeleton`, `StatusBadge` in read-only context → Server Components.
- **`layout.tsx`**: Add `DM_Sans` via `next/font/google`, `<Toaster />` from Sonner, and `ThemeProvider`.
- **`--radius`** is set to `4px` (Tesla's button radius). `rounded-md` resolves to `var(--radius)` = 4px (buttons, inputs). `rounded-xl` = 12px for larger containers. Never use `rounded-full` on buttons.

### Tesla Design Constraints (enforced across all components)

| Rule | What to use | What to avoid |
|------|-------------|---------------|
| Font weight | `font-medium` (500) max | `font-semibold`, `font-bold`, `font-extrabold` |
| Letter spacing | `tracking-normal` (0) only | `tracking-wide`, `tracking-wider`, `tracking-tight`, `tracking-widest` |
| Shadows | None — do not use `shadow-*` | Any `shadow-sm/md/lg/xl/brand/danger` |
| Hover lift | Forbidden | `hover:-translate-y-*` |
| Hover ring | Forbidden for card interaction | `hover:ring-*` |
| Transitions | `transition-[properties] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]` | `transition-all`, hardcoded `duration-150`/`duration-300` |
