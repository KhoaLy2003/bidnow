# Design Spec: Phase 1 — Shared UI & Layout Tesla Migration
**Date:** 2026-05-20
**Scope:** Shared UI primitives + layout shell components
**Reference:** `DESIGN.md` (Tesla design system), `docs/superpowers/specs/2026-05-20-design-tokens-refactor-design.md`
**Status:** Approved for implementation

---

## Overview

Apply Tesla design rules to the shared component layer — the primitives and layout shell that every page inherits. No page-level or auction-specific components change in this phase; those follow in Phase 2.

Three categories of change:
1. **Typography** — remove `font-bold`/`font-semibold`, remove all letter-spacing manipulation
2. **Border radius + transition timing** — fix hardcoded `rounded-lg` on primitives, apply Tesla curve
3. **Card surface + dead code** — replace ring with border on Card, remove shadow class strings that resolve to `none`

---

## Affected Files

| File | Category |
|------|----------|
| `frontend/components/ui/button.tsx` | Radius, timing, dead code |
| `frontend/components/ui/card.tsx` | Surface (ring → border), timing |
| `frontend/components/ui/badge.tsx` | Typography, timing |
| `frontend/components/ui/input.tsx` | Radius |
| `frontend/components/layout/Header.tsx` | Typography, timing, dead code |
| `frontend/components/layout/Footer.tsx` | Typography |
| `frontend/components/layout/BottomNav.tsx` | Dead code |

No other files change in this phase.

---

## Section 1 — Typography

### Rule
- Weight: `font-medium` (500) only. Remove all `font-bold` and `font-semibold`.
- Tracking: `letter-spacing: 0` only. Remove all `tracking-wide`, `tracking-wider`, `tracking-tight`.

### Changes

**`Header.tsx`**

| Location | Old | New |
|----------|-----|-----|
| Wordmark "Bid" (line 64) | `font-semibold` | `font-medium` |
| Wordmark "Now" span (line 66) | `font-bold` | `font-medium` |
| Dropdown username `<p>` (line 136) | `font-semibold text-sm` | `font-medium text-sm` |
| "Status" label (line 145) | `font-bold uppercase tracking-wider` | `font-medium uppercase` |
| "Verified" label (line 148) | `font-bold uppercase text-green-500` | `font-medium uppercase text-[var(--color-success-default)]` |

**`Footer.tsx`**

| Location | Old | New |
|----------|-----|-----|
| Wordmark "Bid" (line 33) | `font-semibold` | `font-medium` |
| Wordmark "Now" span (line 34) | `font-bold` | `font-medium` |
| Section headers `<p>` (line 45) | `font-semibold uppercase tracking-wider` | `font-medium uppercase` |

**`badge.tsx`**

All 7 auction state variants (`active`, `ending-soon`, `critical`, `closed`, `won`, `lost`, `outbid`):

| Old | New |
|-----|-----|
| `font-semibold tracking-wide uppercase` | `font-medium uppercase` |

---

## Section 2 — Border Radius + Transition Timing

### Border radius

Two primitives bypass the token system with hardcoded `rounded-lg`:

| File | Old | New | Resolves to |
|------|-----|-----|-------------|
| `button.tsx` base classes | `rounded-lg` | `rounded-md` | `--radius-md: 4px` |
| `input.tsx` | `rounded-lg` | `rounded-md` | `--radius-md: 4px` |

### Transition timing

Replace generic `transition-all` and hardcoded durations with Tesla-standard tokens:

| File | Old | New |
|------|-----|-----|
| `button.tsx` | `transition-all` | `transition-[background-color,border-color,color,opacity,transform] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]` |
| `badge.tsx` | `transition-all` | `transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]` |
| `Header.tsx` | `transition-[backdrop-filter,box-shadow] duration-150` | `transition-[backdrop-filter] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]` |

---

## Section 3 — Card Surface + Dead Code Cleanup

### Card surface

`card.tsx` uses `ring-1 ring-foreground/10` for surface separation. In the Tesla system, surfaces use clean borders — no ring, no shadow.

| Old | New |
|-----|-----|
| `ring-1 ring-foreground/10` | `border border-border-default` |

### Dead code cleanup

Class strings that resolve to `none` or no-ops after the token migration, removed for clarity:

| File | Remove | Reason |
|------|--------|--------|
| `Header.tsx` scroll state | `shadow-sm` | `--shadow-sm: none` |
| `Header.tsx` `DropdownMenuContent` | `shadow-xl` | `--shadow-xl: none` |
| `button.tsx` `brand` variant | `hover:[box-shadow:var(--shadow-brand)]` | `--shadow-brand: none` |
| `button.tsx` `destructive` variant | `hover:[box-shadow:var(--shadow-danger)]` | `--shadow-danger: none` |
| `BottomNav.tsx` Sell button | `[box-shadow:var(--shadow-brand)]` | `--shadow-brand: none` |

No visual change — these are already no-ops. Removing them prevents confusion for future developers.

---

## What Does NOT Change

- Component structure, layout, spacing — unchanged
- All color tokens and variant colors — unchanged (already updated in token layer)
- shadcn/Base UI prop interfaces — unchanged
- `AuctionCard`, `StatusBadge`, `CountdownTimer`, `SearchBar`, `WalletBadge`, `NotificationBell` — Phase 2
- All page files — Phase 2 and later
- Dark mode behavior — unchanged (ThemeProvider, class strategy unchanged)

---

## Definition of Done

- [ ] `button.tsx`: `rounded-lg` → `rounded-md`, Tesla transition timing, shadow class strings removed
- [ ] `card.tsx`: `ring-1 ring-foreground/10` → `border border-[--color-border-default]`, Tesla transition timing added
- [ ] `badge.tsx`: All 7 auction state variants — `font-semibold tracking-wide` → `font-medium`, Tesla transition timing
- [ ] `input.tsx`: `rounded-lg` → `rounded-md`
- [ ] `Header.tsx`: All 5 typography fixes applied, `shadow-sm`/`shadow-xl` removed, transition updated
- [ ] `Footer.tsx`: All 3 typography fixes applied
- [ ] `BottomNav.tsx`: `[box-shadow:var(--shadow-brand)]` removed from Sell button
- [ ] Visual check: wordmark renders cleanly at `font-medium`, auction badges readable, button corners visibly sharper
- [ ] No hardcoded font weights above 500 remain in any of the 7 files
- [ ] No `tracking-*` utilities remain in any of the 7 files (except `tracking-normal` if used explicitly)

---

## Dependencies

- Requires `frontend/app/globals.css` token migration (Phase 0) to be complete — shadow tokens must be `none`, `--radius-md` must be `4px`, `--duration-tesla` and `--ease-tesla` must be defined.
- Phase 2 (auction surfaces: AuctionCard, homepage, listing page) depends on this phase landing first.
