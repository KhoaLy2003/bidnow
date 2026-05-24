# Phase 1 — Shared UI & Layout Tesla Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply Tesla design rules (weight 400/500 only, no tracking, 4px radius, Tesla transition curve, border-not-ring card surface) to the 7 shared UI and layout components that every page inherits.

**Architecture:** Pure class-string edits across 7 files. No component structure, prop interfaces, or logic changes. Each task is independent and self-contained — a failure in one does not block the others.

**Tech Stack:** Next.js 16.2 App Router, Tailwind CSS v4, Base UI, CVA (class-variance-authority)

---

## File Structure

| File | Action | What changes |
|------|--------|--------------|
| `frontend/components/ui/button.tsx` | Modify | `rounded-lg`→`rounded-md`, Tesla transition, remove shadow dead code |
| `frontend/components/ui/input.tsx` | Modify | `rounded-lg`→`rounded-md` |
| `frontend/components/ui/badge.tsx` | Modify | `font-semibold tracking-wide`→`font-medium`, Tesla transition |
| `frontend/components/ui/card.tsx` | Modify | `ring-1 ring-foreground/10`→`border border-border-default` |
| `frontend/components/layout/Header.tsx` | Modify | 5 typography fixes, transition update, 2 shadow strings removed |
| `frontend/components/layout/Footer.tsx` | Modify | 3 typography fixes |
| `frontend/components/layout/BottomNav.tsx` | Modify | Remove `[box-shadow:var(--shadow-brand)]` from Sell button |

---

### Task 1: button.tsx — radius, transition timing, dead code

**Files:**
- Modify: `frontend/components/ui/button.tsx:7-21`

- [ ] **Step 1: Replace the base className string (line 7)**

Replace this:
```tsx
  "group/button inline-flex shrink-0 items-center justify-center rounded-lg border border-transparent bg-clip-padding text-sm font-medium whitespace-nowrap transition-all outline-none select-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
```

With:
```tsx
  "group/button inline-flex shrink-0 items-center justify-center rounded-md border border-transparent bg-clip-padding text-sm font-medium whitespace-nowrap transition-[background-color,border-color,color,opacity,transform] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] outline-none select-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
```

- [ ] **Step 2: Remove shadow dead code from `brand` variant (line 13)**

Replace this:
```tsx
        brand:
          "bg-brand-500 text-white hover:bg-brand-600 hover:[box-shadow:var(--shadow-brand)] active:scale-[0.98] focus-visible:border-brand-500/40 focus-visible:ring-brand-500/20",
```

With:
```tsx
        brand:
          "bg-brand-500 text-white hover:bg-brand-600 active:scale-[0.98] focus-visible:border-brand-500/40 focus-visible:ring-brand-500/20",
```

- [ ] **Step 3: Remove shadow dead code from `destructive` variant (line 21)**

Replace this:
```tsx
        destructive:
          "bg-danger-default text-white hover:bg-danger-default/90 hover:[box-shadow:var(--shadow-danger)] focus-visible:border-danger-default/40 focus-visible:ring-danger-default/20",
```

With:
```tsx
        destructive:
          "bg-danger-default text-white hover:bg-danger-default/90 focus-visible:border-danger-default/40 focus-visible:ring-danger-default/20",
```

- [ ] **Step 4: Verify**

```bash
grep "rounded-lg\|transition-all\|shadow-brand\|shadow-danger" frontend/components/ui/button.tsx
# Expected: no output
```

- [ ] **Step 5: Commit**

```bash
git add frontend/components/ui/button.tsx
git commit -m "style(button): rounded-md, Tesla transition, remove shadow dead code"
```

---

### Task 2: input.tsx — border radius

**Files:**
- Modify: `frontend/components/ui/input.tsx:12`

- [ ] **Step 1: Replace `rounded-lg` with `rounded-md`**

Replace this:
```tsx
        "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
```

With:
```tsx
        "h-8 w-full min-w-0 rounded-md border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
```

- [ ] **Step 2: Verify**

```bash
grep "rounded-lg" frontend/components/ui/input.tsx
# Expected: no output
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/ui/input.tsx
git commit -m "style(input): rounded-md to match Tesla 4px radius"
```

---

### Task 3: badge.tsx — typography + transition timing

**Files:**
- Modify: `frontend/components/ui/badge.tsx:8,24-36`

- [ ] **Step 1: Replace `transition-all` in base className (line 8)**

Replace this:
```tsx
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 has-data-[icon=inline-end]:pr-1.5 has-data-[icon=inline-start]:pl-1.5 aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 [&>svg]:pointer-events-none [&>svg]:size-3!",
```

With:
```tsx
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-[background-color,border-color,color] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 has-data-[icon=inline-end]:pr-1.5 has-data-[icon=inline-start]:pl-1.5 aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 [&>svg]:pointer-events-none [&>svg]:size-3!",
```

- [ ] **Step 2: Replace auction state variants — remove `font-semibold tracking-wide`**

Replace the entire auction state variants block:
```tsx
        // Auction state variants
        active:
          "bg-auction-active-bg text-auction-active-text border-auction-active-border font-semibold tracking-wide uppercase",
        "ending-soon":
          "bg-auction-ending-bg text-auction-ending-text border-auction-ending-border font-semibold tracking-wide uppercase",
        critical:
          "bg-auction-critical-bg text-auction-critical-text border-auction-critical-border font-semibold tracking-wide uppercase",
        closed:
          "bg-auction-closed-bg text-auction-closed-text border-auction-closed-border font-semibold tracking-wide uppercase",
        won:
          "bg-auction-won-bg text-auction-won-text border-auction-won-border font-semibold tracking-wide uppercase",
        lost:
          "bg-auction-lost-bg text-auction-lost-text border-auction-lost-border font-semibold tracking-wide uppercase",
        outbid:
          "bg-auction-outbid-bg text-auction-outbid-text border-auction-outbid-border font-semibold tracking-wide uppercase",
```

With:
```tsx
        // Auction state variants
        active:
          "bg-auction-active-bg text-auction-active-text border-auction-active-border font-medium uppercase",
        "ending-soon":
          "bg-auction-ending-bg text-auction-ending-text border-auction-ending-border font-medium uppercase",
        critical:
          "bg-auction-critical-bg text-auction-critical-text border-auction-critical-border font-medium uppercase",
        closed:
          "bg-auction-closed-bg text-auction-closed-text border-auction-closed-border font-medium uppercase",
        won:
          "bg-auction-won-bg text-auction-won-text border-auction-won-border font-medium uppercase",
        lost:
          "bg-auction-lost-bg text-auction-lost-text border-auction-lost-border font-medium uppercase",
        outbid:
          "bg-auction-outbid-bg text-auction-outbid-text border-auction-outbid-border font-medium uppercase",
```

- [ ] **Step 3: Verify**

```bash
grep "font-semibold\|tracking-wide\|transition-all" frontend/components/ui/badge.tsx
# Expected: no output
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/ui/badge.tsx
git commit -m "style(badge): font-medium, remove tracking-wide, Tesla transition"
```

---

### Task 4: card.tsx — replace ring with border

**Files:**
- Modify: `frontend/components/ui/card.tsx:15`

- [ ] **Step 1: Replace `ring-1 ring-foreground/10` with `border border-border-default`**

Replace this:
```tsx
        "group/card flex flex-col gap-4 overflow-hidden rounded-xl bg-card py-4 text-sm text-card-foreground ring-1 ring-foreground/10 has-data-[slot=card-footer]:pb-0 has-[>img:first-child]:pt-0 data-[size=sm]:gap-3 data-[size=sm]:py-3 data-[size=sm]:has-data-[slot=card-footer]:pb-0 *:[img:first-child]:rounded-t-xl *:[img:last-child]:rounded-b-xl",
```

With:
```tsx
        "group/card flex flex-col gap-4 overflow-hidden rounded-xl bg-card py-4 text-sm text-card-foreground border border-border-default has-data-[slot=card-footer]:pb-0 has-[>img:first-child]:pt-0 data-[size=sm]:gap-3 data-[size=sm]:py-3 data-[size=sm]:has-data-[slot=card-footer]:pb-0 *:[img:first-child]:rounded-t-xl *:[img:last-child]:rounded-b-xl",
```

- [ ] **Step 2: Verify**

```bash
grep "ring-1\|ring-foreground" frontend/components/ui/card.tsx
# Expected: no output
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/ui/card.tsx
git commit -m "style(card): replace ring with border-border-default"
```

---

### Task 5: Header.tsx — typography, transition, dead code

**Files:**
- Modify: `frontend/components/layout/Header.tsx:55-58,64,66,124,136,145,148`

- [ ] **Step 1: Update header element — remove `shadow-sm`, update transition**

Replace this:
```tsx
      className={cn(
        "sticky top-0 z-[var(--z-index-sticky)] h-16 bg-background border-b border-border transition-[backdrop-filter,box-shadow] duration-150",
        scrolled && "backdrop-blur-sm shadow-sm",
      )}
```

With:
```tsx
      className={cn(
        "sticky top-0 z-[var(--z-index-sticky)] h-16 bg-background border-b border-border transition-[backdrop-filter] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]",
        scrolled && "backdrop-blur-sm",
      )}
```

- [ ] **Step 2: Fix wordmark "Bid" — `font-semibold` → `font-medium`**

Replace this:
```tsx
          <span className="hidden font-semibold sm:inline-block">
```

With:
```tsx
          <span className="hidden font-medium sm:inline-block">
```

- [ ] **Step 3: Fix wordmark "Now" — `font-bold` → `font-medium`**

Replace this:
```tsx
            <span className="text-[var(--color-text-brand)] font-bold">
```

With:
```tsx
            <span className="text-[var(--color-text-brand)] font-medium">
```

- [ ] **Step 4: Remove `shadow-xl` from DropdownMenuContent**

Replace this:
```tsx
                    className="w-64 p-2 shadow-xl border-border/50 backdrop-blur-md"
```

With:
```tsx
                    className="w-64 p-2 border-border/50 backdrop-blur-md"
```

- [ ] **Step 5: Fix dropdown username — `font-semibold` → `font-medium`**

Replace this:
```tsx
                              <p className="font-semibold text-sm truncate">
```

With:
```tsx
                              <p className="font-medium text-sm truncate">
```

- [ ] **Step 6: Fix "Status" label — `font-bold uppercase tracking-wider` → `font-medium uppercase`**

Replace this:
```tsx
                            <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
```

With:
```tsx
                            <span className="text-[10px] font-medium uppercase text-muted-foreground">
```

- [ ] **Step 7: Fix "Verified" label — `font-bold text-green-500` → `font-medium text-[var(--color-success-default)]`**

Replace this:
```tsx
                            <span className="text-[10px] font-bold uppercase text-green-500">
```

With:
```tsx
                            <span className="text-[10px] font-medium uppercase text-[var(--color-success-default)]">
```

- [ ] **Step 8: Verify**

```bash
grep "font-bold\|font-semibold\|tracking-wider\|shadow-sm\|shadow-xl\|text-green-500" frontend/components/layout/Header.tsx
# Expected: no output
```

- [ ] **Step 9: Commit**

```bash
git add frontend/components/layout/Header.tsx
git commit -m "style(header): font-medium, remove tracking, Tesla transition, clean shadow dead code"
```

---

### Task 6: Footer.tsx — typography

**Files:**
- Modify: `frontend/components/layout/Footer.tsx:33-34,45`

- [ ] **Step 1: Fix wordmark — `font-semibold` on "Bid" and `font-bold` on "Now" → both `font-medium`**

Replace this:
```tsx
            <span className="text-base font-semibold">
              Bid<span className="text-[var(--color-text-brand)] font-bold">Now</span>
            </span>
```

With:
```tsx
            <span className="text-base font-medium">
              Bid<span className="text-[var(--color-text-brand)] font-medium">Now</span>
            </span>
```

- [ ] **Step 2: Fix section headers — `font-semibold uppercase tracking-wider` → `font-medium uppercase`**

Replace this:
```tsx
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
```

With:
```tsx
              <p className="text-xs font-medium uppercase text-muted-foreground">
```

- [ ] **Step 3: Verify**

```bash
grep "font-bold\|font-semibold\|tracking-wider" frontend/components/layout/Footer.tsx
# Expected: no output
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/layout/Footer.tsx
git commit -m "style(footer): font-medium, remove tracking-wider"
```

---

### Task 7: BottomNav.tsx — dead code + final verification

**Files:**
- Modify: `frontend/components/layout/BottomNav.tsx:39`

- [ ] **Step 1: Remove `[box-shadow:var(--shadow-brand)]` from Sell button**

Replace this:
```tsx
                className="rounded-full -mt-4 [box-shadow:var(--shadow-brand)]"
```

With:
```tsx
                className="rounded-full -mt-4"
```

- [ ] **Step 2: Verify BottomNav**

```bash
grep "shadow-brand\|shadow-danger\|shadow-xl\|shadow-sm\|font-bold\|font-semibold\|tracking-wide\|tracking-wider\|rounded-lg" \
  frontend/components/layout/BottomNav.tsx \
  frontend/components/layout/Footer.tsx \
  frontend/components/layout/Header.tsx \
  frontend/components/ui/button.tsx \
  frontend/components/ui/card.tsx \
  frontend/components/ui/badge.tsx \
  frontend/components/ui/input.tsx
# Expected: no output
```

- [ ] **Step 3: Visual regression check**

Start the dev server (`npm run dev` in `frontend/`). Check:

| What | Where | Expected |
|------|-------|----------|
| Button corners | Any page with buttons | Visibly sharper (4px not 8px) |
| Card surface | Any card | Clean border line, no subtle ring glow |
| Auction status badges | Homepage or auctions list | Lighter weight label text, no letter-spacing expansion |
| Header wordmark | Top of any page | "BidNow" — medium weight, indigo "Now" |
| Header scroll | Scroll down any page | Frosted blur on scroll, no shadow |
| Footer section labels | Bottom of homepage | "PLATFORM", "ACCOUNT" — medium weight, no tracking |
| "Verified" chip in user dropdown | Open user menu | Uses success token color, not hardcoded green |

- [ ] **Step 4: Commit**

```bash
git add frontend/components/layout/BottomNav.tsx
git commit -m "style(bottom-nav): remove shadow-brand dead code; complete Phase 1 Tesla migration"
```

---

## Self-Review

**Spec coverage:**
- ✅ `button.tsx` — `rounded-lg`→`rounded-md`, Tesla transition, `shadow-brand`/`shadow-danger` removed (Tasks 1)
- ✅ `input.tsx` — `rounded-lg`→`rounded-md` (Task 2)
- ✅ `badge.tsx` — `font-semibold tracking-wide`→`font-medium`, Tesla transition (Task 3)
- ✅ `card.tsx` — `ring-1 ring-foreground/10`→`border border-border-default` (Task 4)
- ✅ `Header.tsx` — 5 typography fixes, transition update, `shadow-sm`/`shadow-xl` removed (Task 5)
- ✅ `Footer.tsx` — 3 typography fixes (Task 6)
- ✅ `BottomNav.tsx` — `shadow-brand` removed (Task 7)

**No placeholder steps. All code shown in full. All verify commands exact.**
