# Design Spec: Design Token Refactor — Tesla-Inspired System
**Date:** 2026-05-20
**Scope:** `frontend/app/globals.css` — token layer only
**Reference:** `DESIGN.md` (Tesla design system)
**Status:** Approved for implementation

---

## Overview

Migrate BidNow's CSS design tokens to align with the Tesla-inspired design philosophy defined in `DESIGN.md`. This is the foundational layer — no component or page code changes. All subsequent UI refactor phases (Header, homepage, auction cards) will build on this clean token baseline.

### Core principles applied
- White canvas + Light Ash only for surfaces (no tinted grays)
- Carbon Dark → Graphite → Pewter → Silver Fog text hierarchy
- Zero shadows — depth through opacity and z-index only
- 4px border-radius for interactive elements, 12px for image surfaces
- 0.33s `cubic-bezier(0.5, 0, 0, 0.75)` as the standard interaction timing
- Weight 400/500 only — token-level enforcement
- Indigo (`#4F46E5`) kept as brand color, applied CTA-only per Tesla's single-accent discipline
- All 7 auction state color sets retained, text/accent muted ~30%
- Dark mode preserved and updated to Carbon Dark–family palette

---

## What Changes

### 1. shadcn Semantic Vars (Layer 1)

**Light mode (`:root`):**
```css
--background:           hsl(0, 0%, 100%);          /* unchanged */
--foreground:           hsl(220, 13%, 9%);          /* #171A20 Carbon Dark */
--card:                 hsl(0, 0%, 100%);           /* unchanged */
--card-foreground:      hsl(220, 13%, 9%);
--popover:              hsl(0, 0%, 100%);
--popover-foreground:   hsl(220, 13%, 9%);
--primary:              hsl(244, 72%, 58%);         /* unchanged — BidNow indigo */
--primary-foreground:   hsl(0, 0%, 100%);
--secondary:            hsl(0, 0%, 96%);            /* #F4F4F4 Light Ash */
--secondary-foreground: hsl(220, 13%, 9%);
--muted:                hsl(0, 0%, 96%);            /* #F4F4F4 Light Ash */
--muted-foreground:     hsl(214, 4%, 37%);          /* #5C5E62 Pewter */
--accent:               hsl(0, 0%, 96%);            /* #F4F4F4 Light Ash */
--accent-foreground:    hsl(220, 13%, 9%);
--destructive:          hsl(0, 72%, 51%);           /* unchanged */
--border:               hsl(0, 0%, 93%);            /* #EEEEEE Cloud Gray */
--input:                hsl(0, 0%, 93%);
--ring:                 hsl(244, 72%, 58%);         /* unchanged */
--radius:               4px;                        /* was 0.5rem */
```

**Dark mode (`.dark`):**
```css
--background:           hsl(220, 14%, 10%);         /* #171A20 Carbon Dark */
--foreground:           hsl(240, 5%, 91%);          /* #E8E8EC */
--card:                 hsl(220, 13%, 13%);         /* #1E2128 */
--card-foreground:      hsl(240, 5%, 91%);
--popover:              hsl(220, 13%, 13%);
--popover-foreground:   hsl(240, 5%, 91%);
--primary:              hsl(239, 84%, 67%);         /* unchanged */
--primary-foreground:   hsl(0, 0%, 100%);
--secondary:            hsl(220, 12%, 15%);         /* #1E2128 */
--secondary-foreground: hsl(240, 5%, 91%);
--muted:                hsl(220, 12%, 15%);
--muted-foreground:     hsl(220, 3%, 39%);          /* #62626A */
--accent:               hsl(220, 13%, 16%);         /* #252830 */
--accent-foreground:    hsl(240, 5%, 91%);
--destructive:          hsl(0, 84%, 60%);           /* unchanged */
--border:               hsl(220, 10%, 19%);         /* #2A2D34 */
--input:                hsl(220, 10%, 19%);
--ring:                 hsl(239, 84%, 67%);
```

---

### 2. Surface & Background Tokens

**Light mode:**
```css
--color-bg-base:        #FFFFFF;
--color-bg-elevated:    #F4F4F4;   /* was #F9F9FB */
--color-bg-overlay:     #F4F4F4;   /* was #F3F3F7 */
--color-bg-card:        #FFFFFF;
--color-bg-card-hover:  #F4F4F4;   /* was #F5F5FF */
--color-bg-modal:       rgba(255, 255, 255, 0.97);  /* unchanged */
--color-bg-backdrop:    rgba(0, 0, 0, 0.4);         /* unchanged */
--color-bg-sidebar:     #F4F4F4;   /* was #F4F4F8 */
```

**Dark mode:**
```css
--color-bg-base:        #171A20;   /* Carbon Dark */
--color-bg-elevated:    #1E2128;   /* was #1A1A1F */
--color-bg-overlay:     #252830;   /* was #242428 */
--color-bg-card:        #1E2128;   /* was #1E1E24 */
--color-bg-card-hover:  #252830;   /* was #25252E */
--color-bg-modal:       rgba(23, 26, 32, 0.97);
--color-bg-backdrop:    rgba(0, 0, 0, 0.6);         /* unchanged */
--color-bg-sidebar:     #171A20;   /* was #16161B */
```

---

### 3. Text Hierarchy Tokens

**Light mode:**
```css
--color-text-primary:    #171A20;  /* Carbon Dark — was #111115 */
--color-text-secondary:  #393C41;  /* Graphite — was #52525C */
--color-text-tertiary:   #5C5E62;  /* Pewter — was #8B8B9A */
--color-text-disabled:   #8E8E8E;  /* Silver Fog — was #C2C2D0 */
--color-text-inverse:    #FFFFFF;  /* unchanged */
--color-text-brand:      #4F46E5;  /* unchanged */
--color-text-link:       #4F46E5;  /* unchanged */
--color-text-link-hover: #3730A3;  /* unchanged */
```

**Dark mode:**
```css
--color-text-primary:    #E8E8EC;  /* was #F0F0F4 */
--color-text-secondary:  #9A9A9E;  /* was #A0A0B0 */
--color-text-tertiary:   #62626A;  /* was #6A6A7E */
--color-text-disabled:   #3A3A40;  /* was #3A3A4A */
--color-text-inverse:    #171A20;  /* was #0F0F0F */
--color-text-brand:      #8483F5;  /* unchanged */
--color-text-link:       #8483F5;  /* unchanged */
--color-text-link-hover: #A5A4F8;  /* unchanged */
```

---

### 4. Border Tokens

**Light mode:**
```css
--color-border-default: #EEEEEE;   /* Cloud Gray — was #E2E2EC */
--color-border-strong:  #D0D1D2;   /* Pale Silver — was #C8C8D8 */
--color-border-focus:   #4F46E5;   /* unchanged */
--color-border-error:   #DC2626;   /* unchanged */
```

**Dark mode:**
```css
--color-border-default: #2A2D34;   /* was #2A2A35 */
--color-border-strong:  #3A3D46;   /* was #3A3A4A */
--color-border-focus:   #6366F1;   /* unchanged */
--color-border-error:   #EF4444;   /* unchanged */
```

---

### 5. Shadow Tokens

All shadows zeroed. Depth is achieved through opacity-based transparency and z-index layering only.

```css
--shadow-none:    none;
--shadow-xs:      none;   /* was 0 1px 2px ... */
--shadow-sm:      none;
--shadow-md:      none;
--shadow-lg:      none;
--shadow-xl:      none;
--shadow-2xl:     none;
--shadow-inner:   none;
--shadow-brand:   none;
--shadow-danger:  none;
--shadow-success: none;
```

Two new opacity-based tokens added (not box-shadows — used as CSS values in backdrop/background):
```css
--shadow-frosted:  rgba(255, 255, 255, 0.75);  /* nav backdrop on scroll */
--shadow-overlay:  rgba(128, 128, 128, 0.65);  /* modal scrim */
```

> **Note on keyframes:** `bid-pulse` and `won-reveal` animations reference `var(--shadow-brand)` and `var(--shadow-success)`. These will resolve to `none`, removing the glow effect. The scale/background-color transitions in those animations are unaffected and remain functional.

---

### 6. Border Radius Scale

```css
--radius:       4px;    /* shadcn base — was 0.5rem */
--radius-none:  0px;
--radius-xs:    2px;
--radius-sm:    2px;    /* was calc(var(--radius) - 4px) = 4px */
--radius-md:    4px;    /* was calc(var(--radius) - 2px) = 6px */
--radius-lg:    4px;    /* was var(--radius) = 8px */
--radius-xl:    12px;   /* category/image cards — was 12px (unchanged) */
--radius-2xl:   12px;   /* was 16px */
--radius-3xl:   12px;   /* was 24px */
--radius-full:  9999px; /* carousel dots — unchanged */
```

---

### 7. Transition Tokens (additions)

Two named values added to the existing scale for Tesla-standard interactions:

```css
--ease-tesla:     cubic-bezier(0.5, 0, 0, 0.75);
--duration-tesla: 333ms;
```

All existing duration/easing tokens are preserved. `--ease-tesla` + `--duration-tesla` become the standard for button, link, and nav hover/focus/active transitions. Auction-specific animations (`bid-pulse`, `countdown-tick`, `price-roll`) continue using their existing timing.

---

### 8. Typography Tokens

**One new token:**
```css
--font-size-hero: 2.5rem;   /* 40px — Tesla hero title size */
```

**Three tokens removed:**
```css
/* REMOVED — not available in Tesla system */
--font-weight-semibold:  600;
--font-weight-bold:      700;
--font-weight-extrabold: 800;
```

All other font-size, line-height, and letter-spacing tokens are unchanged. Usage rule: only `--letter-spacing-normal` (0em) is permitted in the Tesla-inspired UI. No tracking manipulation on headings or labels.

---

### 9. Auction State Color Muting

Backgrounds (`-bg`) and borders (`-border`) are **unchanged**. Only `text` and `accent` tokens are muted (~30% saturation reduction).

**Light mode:**
```css
/* Active */
--color-auction-active-text:   #3A7FBE;   /* was #0284C7 */
--color-auction-active-accent: #4A8DB8;   /* was #0284C7 */

/* Ending Soon */
--color-auction-ending-text:   #9A6B3A;   /* was #B45309 */
--color-auction-ending-accent: #C8933A;   /* was #F59E0B */

/* Critical */
--color-auction-critical-text:   #9B4848; /* was #B91C1C */
--color-auction-critical-accent: #B55555; /* was #DC2626 */

/* Won */
--color-auction-won-text:   #2A5E3A;      /* was #14532D */
--color-auction-won-accent: #3A8F5A;      /* was #16A34A */

/* Lost */
--color-auction-lost-text:   #7A3A3A;     /* was #7F1D1D */
--color-auction-lost-accent: #B55555;     /* was #DC2626 */

/* Outbid */
--color-auction-outbid-text:   #844530;   /* was #9A3412 */
--color-auction-outbid-accent: #C06835;   /* was #EA580C */

/* Closed */
--color-auction-closed-text:   #5C5E62;   /* Pewter — was #6B6B82 */
--color-auction-closed-accent: #8E8E8E;   /* Silver Fog — was #9898AE */
```

**Dark mode:** reduce saturation ~30% on the `text` and `accent` values in each `.dark` auction state block. The existing dark bg/border tokens are unchanged. Implementation guide: in HSL, reduce the `S` component by ~30 percentage points while keeping `H` and `L` the same.

---

## What Does NOT Change

- Brand palette (`--brand-50` through `--brand-900`) — unchanged
- Semantic success/warning/danger/info tokens — unchanged
- Neutral scale (`--color-neutral-0` through `--color-neutral-950`) — unchanged
- Wallet tokens — unchanged
- Blur tokens — unchanged
- Z-index scale — unchanged
- Font stacks (`--font-sans`, `--font-mono`, `--font-display`) — unchanged
- Font sizes (except `--font-size-hero` added) — unchanged
- Line height tokens — unchanged
- Letter spacing tokens — unchanged
- Border width tokens — unchanged
- Duration tokens (except `--duration-tesla` added) — unchanged
- Easing tokens (except `--ease-tesla` added) — unchanged
- Container width tokens — unchanged
- All `@keyframes` (glow effects will silently disappear as shadow tokens resolve to `none`)
- `@theme inline` forwarding block — add new tokens, remove removed tokens
- Global base styles — unchanged

---

## Affected Files

| File | Change |
|------|--------|
| `frontend/app/globals.css` | Token values updated throughout `:root` and `.dark` |

No component files change in this phase. Visual impact will be visible wherever components consume the updated tokens — cards will lose shadows, borders will warm from purple-gray to Cloud Gray, text will shift warmer.

---

## Definition of Done

- [ ] `:root` shadcn vars updated (background, foreground, secondary, muted, accent, border, radius)
- [ ] `:root` surface tokens updated to white/Light Ash palette
- [ ] `:root` text tokens updated to Carbon Dark/Graphite/Pewter/Silver Fog
- [ ] `:root` border tokens updated to Cloud Gray/Pale Silver
- [ ] All shadow tokens set to `none`; `--shadow-frosted` and `--shadow-overlay` added
- [ ] Border radius scale collapsed to 4px/12px
- [ ] `--ease-tesla` and `--duration-tesla` added
- [ ] `--font-size-hero: 2.5rem` added
- [ ] `--font-weight-semibold/bold/extrabold` removed
- [ ] Auction state `text` + `accent` tokens muted in light mode
- [ ] `.dark` surface tokens updated to Carbon Dark family
- [ ] `.dark` text tokens updated to warm-neutral
- [ ] `.dark` border tokens updated
- [ ] `.dark` auction state `text` + `accent` tokens muted
- [ ] `@theme inline` block updated: remove forwarding entries for `--font-weight-semibold/bold/extrabold`; add forwarding for `--font-size-hero`, `--ease-tesla`, `--duration-tesla`, `--shadow-frosted`, `--shadow-overlay`
- [ ] Visual regression check: homepage, auction listing, auction card, header render correctly
- [ ] No hardcoded hex colors introduced — all values via CSS variables

---

## Dependencies

- None. This is a pure CSS token change with no runtime or backend dependencies.
- Subsequent phases (Header refactor, homepage refactor, AuctionCard refactor) depend on this landing first.
