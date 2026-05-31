# Design Token Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `frontend/app/globals.css` design tokens to a Tesla-inspired system — white canvas, warm-charcoal text hierarchy, zero shadows, 4px radius, muted auction state colors — without touching any component files.

**Architecture:** Pure CSS token swap in one file. All changes are isolated to `:root`, `.dark`, and `@theme inline` blocks. No component code changes. Visual impact is immediate across all pages that consume these tokens via Tailwind utilities.

**Tech Stack:** Tailwind CSS v4, CSS custom properties, Next.js App Router (dev server for visual verification)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/app/globals.css` | Modify | All token changes — 9 targeted edits across `:root`, `.dark`, and `@theme inline` |

No other files change in this plan.

---

### Task 1: Update Layer 1 shadcn vars — `:root`

**Files:**
- Modify: `frontend/app/globals.css:12-32`

- [ ] **Step 1: Replace the `:root` shadcn block (lines 12–32)**

Replace this:
```css
:root {
  --background:             hsl(0, 0%, 100%);
  --foreground:             hsl(240, 6%, 7%);
  --card:                   hsl(0, 0%, 100%);
  --card-foreground:        hsl(240, 6%, 7%);
  --popover:                hsl(0, 0%, 100%);
  --popover-foreground:     hsl(240, 6%, 7%);
  --primary:                hsl(244, 72%, 58%);
  --primary-foreground:     hsl(0, 0%, 100%);
  --secondary:              hsl(240, 5%, 96%);
  --secondary-foreground:   hsl(240, 6%, 7%);
  --muted:                  hsl(240, 5%, 96%);
  --muted-foreground:       hsl(240, 4%, 46%);
  --accent:                 hsl(239, 84%, 97%);
  --accent-foreground:      hsl(244, 72%, 58%);
  --destructive:            hsl(0, 72%, 51%);
  --border:                 hsl(240, 6%, 90%);
  --input:                  hsl(240, 6%, 90%);
  --ring:                   hsl(244, 72%, 58%);
  --radius:                 0.5rem;
}
```

With:
```css
:root {
  --background:             hsl(0, 0%, 100%);
  --foreground:             hsl(220, 13%, 9%);
  --card:                   hsl(0, 0%, 100%);
  --card-foreground:        hsl(220, 13%, 9%);
  --popover:                hsl(0, 0%, 100%);
  --popover-foreground:     hsl(220, 13%, 9%);
  --primary:                hsl(244, 72%, 58%);
  --primary-foreground:     hsl(0, 0%, 100%);
  --secondary:              hsl(0, 0%, 96%);
  --secondary-foreground:   hsl(220, 13%, 9%);
  --muted:                  hsl(0, 0%, 96%);
  --muted-foreground:       hsl(214, 4%, 37%);
  --accent:                 hsl(0, 0%, 96%);
  --accent-foreground:      hsl(220, 13%, 9%);
  --destructive:            hsl(0, 72%, 51%);
  --border:                 hsl(0, 0%, 93%);
  --input:                  hsl(0, 0%, 93%);
  --ring:                   hsl(244, 72%, 58%);
  --radius:                 4px;
}
```

- [ ] **Step 2: Verify**

Run in `frontend/`:
```bash
npm run dev
```
Open `http://localhost:3000`. Body text should appear slightly warmer/darker. Input borders should be lighter (Cloud Gray). No purple tint on neutral surfaces.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: update shadcn root vars to Tesla-inspired warm palette"
```

---

### Task 2: Update Layer 1 shadcn vars — `.dark`

**Files:**
- Modify: `frontend/app/globals.css:35-54`

- [ ] **Step 1: Replace the `.dark` shadcn block (lines 35–54)**

Replace this:
```css
/* shadcn dark overrides */
.dark {
  --background:             hsl(240, 7%, 6%);
  --foreground:             hsl(240, 9%, 95%);
  --card:                   hsl(240, 6%, 12%);
  --card-foreground:        hsl(240, 9%, 95%);
  --popover:                hsl(240, 6%, 12%);
  --popover-foreground:     hsl(240, 9%, 95%);
  --primary:                hsl(239, 84%, 67%);
  --primary-foreground:     hsl(0, 0%, 100%);
  --secondary:              hsl(240, 5%, 15%);
  --secondary-foreground:   hsl(240, 9%, 95%);
  --muted:                  hsl(240, 5%, 15%);
  --muted-foreground:       hsl(240, 4%, 56%);
  --accent:                 hsl(244, 59%, 20%);
  --accent-foreground:      hsl(239, 84%, 67%);
  --destructive:            hsl(0, 84%, 60%);
  --border:                 hsl(240, 6%, 20%);
  --input:                  hsl(240, 6%, 20%);
  --ring:                   hsl(239, 84%, 67%);
}
```

With:
```css
/* shadcn dark overrides */
.dark {
  --background:             hsl(220, 14%, 10%);
  --foreground:             hsl(240, 5%, 91%);
  --card:                   hsl(220, 13%, 13%);
  --card-foreground:        hsl(240, 5%, 91%);
  --popover:                hsl(220, 13%, 13%);
  --popover-foreground:     hsl(240, 5%, 91%);
  --primary:                hsl(239, 84%, 67%);
  --primary-foreground:     hsl(0, 0%, 100%);
  --secondary:              hsl(220, 12%, 15%);
  --secondary-foreground:   hsl(240, 5%, 91%);
  --muted:                  hsl(220, 12%, 15%);
  --muted-foreground:       hsl(220, 3%, 39%);
  --accent:                 hsl(220, 13%, 16%);
  --accent-foreground:      hsl(240, 5%, 91%);
  --destructive:            hsl(0, 84%, 60%);
  --border:                 hsl(220, 10%, 19%);
  --input:                  hsl(220, 10%, 19%);
  --ring:                   hsl(239, 84%, 67%);
}
```

- [ ] **Step 2: Verify**

Toggle dark mode in browser. Dark background should shift from blue-black (`#0F0F0F`) to Carbon Dark warm near-black (`#171A20`). Text should be warm off-white rather than cool white.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: update shadcn dark vars to Carbon Dark family"
```

---

### Task 3: Update surface, text, and border tokens in `:root`

**Files:**
- Modify: `frontend/app/globals.css:72-134`

- [ ] **Step 1: Replace the surface token block (lines 72–80)**

Replace this:
```css
  /* Surface / Background */
  --color-bg-base:        #FFFFFF;
  --color-bg-elevated:    #F9F9FB;
  --color-bg-overlay:     #F3F3F7;
  --color-bg-card:        #FFFFFF;
  --color-bg-card-hover:  #F5F5FF;
  --color-bg-modal:       rgba(255, 255, 255, 0.97);
  --color-bg-backdrop:    rgba(0, 0, 0, 0.4);
  --color-bg-sidebar:     #F4F4F8;
```

With:
```css
  /* Surface / Background */
  --color-bg-base:        #FFFFFF;
  --color-bg-elevated:    #F4F4F4;
  --color-bg-overlay:     #F4F4F4;
  --color-bg-card:        #FFFFFF;
  --color-bg-card-hover:  #F4F4F4;
  --color-bg-modal:       rgba(255, 255, 255, 0.97);
  --color-bg-backdrop:    rgba(0, 0, 0, 0.4);
  --color-bg-sidebar:     #F4F4F4;
```

- [ ] **Step 2: Replace the text token block (lines 82–90)**

Replace this:
```css
  /* Text */
  --color-text-primary:    #111115;
  --color-text-secondary:  #52525C;
  --color-text-tertiary:   #8B8B9A;
  --color-text-disabled:   #C2C2D0;
  --color-text-inverse:    #FFFFFF;
  --color-text-brand:      #4F46E5;
  --color-text-link:       #4F46E5;
  --color-text-link-hover: #3730A3;
```

With:
```css
  /* Text */
  --color-text-primary:    #171A20;
  --color-text-secondary:  #393C41;
  --color-text-tertiary:   #5C5E62;
  --color-text-disabled:   #8E8E8E;
  --color-text-inverse:    #FFFFFF;
  --color-text-brand:      #4F46E5;
  --color-text-link:       #4F46E5;
  --color-text-link-hover: #3730A3;
```

- [ ] **Step 3: Replace the border token block (lines 130–134)**

Replace this:
```css
  /* Border */
  --color-border-default: #E2E2EC;
  --color-border-strong:  #C8C8D8;
  --color-border-focus:   #6366F1;
  --color-border-error:   #DC2626;
```

With:
```css
  /* Border */
  --color-border-default: #EEEEEE;
  --color-border-strong:  #D0D1D2;
  --color-border-focus:   #4F46E5;
  --color-border-error:   #DC2626;
```

- [ ] **Step 4: Verify**

In the running dev server, check `http://localhost:3000`:
- Primary text should be warmer charcoal (not blue-tinted dark)
- Secondary text should be Graphite gray (not purple-gray)
- Card borders should be lighter Cloud Gray
- Sidebar/elevated surfaces should be pure Light Ash (no purple tint)

- [ ] **Step 5: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: update surface, text, border tokens to Tesla warm-neutral palette"
```

---

### Task 4: Zero out shadow tokens + add frosted/overlay

**Files:**
- Modify: `frontend/app/globals.css:185-196`

- [ ] **Step 1: Replace the shadow token block (lines 185–196)**

Replace this:
```css
  /* Shadows */
  --shadow-none:    none;
  --shadow-xs:      0 1px 2px 0 rgba(0, 0, 0, 0.05);
  --shadow-sm:      0 1px 3px 0 rgba(0, 0, 0, 0.10), 0 1px 2px -1px rgba(0, 0, 0, 0.10);
  --shadow-md:      0 4px 6px -1px rgba(0, 0, 0, 0.10), 0 2px 4px -2px rgba(0, 0, 0, 0.10);
  --shadow-lg:      0 10px 15px -3px rgba(0, 0, 0, 0.10), 0 4px 6px -4px rgba(0, 0, 0, 0.10);
  --shadow-xl:      0 20px 25px -5px rgba(0, 0, 0, 0.10), 0 8px 10px -6px rgba(0, 0, 0, 0.10);
  --shadow-2xl:     0 25px 50px -12px rgba(0, 0, 0, 0.25);
  --shadow-inner:   inset 0 2px 4px 0 rgba(0, 0, 0, 0.05);
  --shadow-brand:   0 0 0 3px rgba(99, 102, 241, 0.20), 0 4px 14px 0 rgba(79, 70, 229, 0.25);
  --shadow-danger:  0 0 0 3px rgba(239, 68, 68, 0.20), 0 4px 14px 0 rgba(220, 38, 38, 0.25);
  --shadow-success: 0 0 0 3px rgba(34, 197, 94, 0.20), 0 4px 14px 0 rgba(22, 163, 74, 0.25);
```

With:
```css
  /* Shadows — none by default; depth via opacity and z-index only */
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
  --shadow-frosted: rgba(255, 255, 255, 0.75);
  --shadow-overlay: rgba(128, 128, 128, 0.65);
```

- [ ] **Step 2: Verify**

Check `http://localhost:3000`:
- Auction cards should have no drop shadow on hover
- Header scroll effect may lose its `shadow-sm` — this is expected and correct
- No glows anywhere

- [ ] **Step 3: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: zero all shadow tokens; add frosted/overlay opacity tokens"
```

---

### Task 5: Update border radius, typography tokens, and add Tesla transition tokens

**Files:**
- Modify: `frontend/app/globals.css:239-270` (font weight, border radius)
- Modify: `frontend/app/globals.css:219-237` (font size — add hero)
- Modify: `frontend/app/globals.css:278-295` (duration + easing — add Tesla tokens)

- [ ] **Step 1: Update font size block — add `--font-size-hero` (after line 231, before `--font-size-price-sm`)**

Replace this:
```css
  --font-size-4xl:      3rem;
  --font-size-5xl:      3.75rem;
  --font-size-6xl:      4.5rem;
  --font-size-price-sm: 1.5rem;
```

With:
```css
  --font-size-4xl:      3rem;
  --font-size-5xl:      3.75rem;
  --font-size-6xl:      4.5rem;
  --font-size-hero:     2.5rem;
  --font-size-price-sm: 1.5rem;
```

- [ ] **Step 2: Update font weight block — remove semibold/bold/extrabold (lines 239–244)**

Replace this:
```css
  /* Font weight */
  --font-weight-regular:   400;
  --font-weight-medium:    500;
  --font-weight-semibold:  600;
  --font-weight-bold:      700;
  --font-weight-extrabold: 800;
```

With:
```css
  /* Font weight — 400/500 only per Tesla design system */
  --font-weight-regular: 400;
  --font-weight-medium:  500;
```

- [ ] **Step 3: Update border radius block (lines 261–270)**

Replace this:
```css
  /* Border radius (full scale) */
  --radius-none: 0px;
  --radius-xs:   2px;
  --radius-sm:   calc(var(--radius) - 4px);
  --radius-md:   calc(var(--radius) - 2px);
  --radius-lg:   var(--radius);
  --radius-xl:   calc(var(--radius) + 4px);
  --radius-2xl:  16px;
  --radius-3xl:  24px;
  --radius-full: 9999px;
```

With:
```css
  /* Border radius (full scale) */
  --radius-none: 0px;
  --radius-xs:   2px;
  --radius-sm:   2px;
  --radius-md:   4px;
  --radius-lg:   4px;
  --radius-xl:   12px;
  --radius-2xl:  12px;
  --radius-3xl:  12px;
  --radius-full: 9999px;
```

- [ ] **Step 4: Add Tesla transition tokens to the easing block (lines 288–295)**

Replace this:
```css
  /* Easing */
  --ease-linear:     linear;
  --ease-in:         cubic-bezier(0.4, 0, 1, 1);
  --ease-out:        cubic-bezier(0, 0, 0.2, 1);
  --ease-in-out:     cubic-bezier(0.4, 0, 0.2, 1);
  --ease-spring:     cubic-bezier(0.34, 1.56, 0.64, 1);
  --ease-bounce-out: cubic-bezier(0.68, -0.55, 0.27, 1.55);
  --ease-snappy:     cubic-bezier(0.2, 0, 0, 1);
```

With:
```css
  /* Easing */
  --ease-linear:     linear;
  --ease-in:         cubic-bezier(0.4, 0, 1, 1);
  --ease-out:        cubic-bezier(0, 0, 0.2, 1);
  --ease-in-out:     cubic-bezier(0.4, 0, 0.2, 1);
  --ease-spring:     cubic-bezier(0.34, 1.56, 0.64, 1);
  --ease-bounce-out: cubic-bezier(0.68, -0.55, 0.27, 1.55);
  --ease-snappy:     cubic-bezier(0.2, 0, 0, 1);
  --ease-tesla:      cubic-bezier(0.5, 0, 0, 0.75);
```

Add `--duration-tesla` to the duration block (lines 278–286):

Replace this:
```css
  /* Duration */
  --duration-instant:    0ms;
  --duration-fast:       80ms;
  --duration-normal:     150ms;
  --duration-moderate:   250ms;
  --duration-slow:       350ms;
  --duration-extra-slow: 500ms;
  --duration-bid-pulse:  600ms;
  --duration-countdown:  1000ms;
```

With:
```css
  /* Duration */
  --duration-instant:    0ms;
  --duration-fast:       80ms;
  --duration-normal:     150ms;
  --duration-moderate:   250ms;
  --duration-slow:       350ms;
  --duration-extra-slow: 500ms;
  --duration-bid-pulse:  600ms;
  --duration-countdown:  1000ms;
  --duration-tesla:      333ms;
```

- [ ] **Step 5: Verify**

```bash
# Confirm removed weight tokens are gone
grep "font-weight-semibold\|font-weight-bold\|font-weight-extrabold" frontend/app/globals.css
# Expected: no output
```

Check `http://localhost:3000`: buttons and cards should have noticeably sharper corners (4px instead of 8px).

- [ ] **Step 6: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: update radius to 4px/12px, add Tesla timing tokens, trim font weight scale"
```

---

### Task 6: Mute auction state colors — light mode

**Files:**
- Modify: `frontend/app/globals.css:136-176`

- [ ] **Step 1: Replace auction state text/accent tokens in `:root` (lines 136–176)**

Replace this entire block:
```css
  /* Auction — Active */
  --color-auction-active-bg:     #F0F9FF;
  --color-auction-active-text:   #0284C7;
  --color-auction-active-border: #BAE6FD;
  --color-auction-active-accent: #0284C7;

  /* Auction — Ending Soon */
  --color-auction-ending-bg:     #FFFBEB;
  --color-auction-ending-text:   #B45309;
  --color-auction-ending-border: #FDE68A;
  --color-auction-ending-accent: #F59E0B;

  /* Auction — Critical */
  --color-auction-critical-bg:     #FEF2F2;
  --color-auction-critical-text:   #B91C1C;
  --color-auction-critical-border: #FECACA;
  --color-auction-critical-accent: #DC2626;

  /* Auction — Closed */
  --color-auction-closed-bg:     #F0F0F5;
  --color-auction-closed-text:   #6B6B82;
  --color-auction-closed-border: #E2E2EC;
  --color-auction-closed-accent: #9898AE;

  /* Auction — Won */
  --color-auction-won-bg:     #F0FDF4;
  --color-auction-won-text:   #14532D;
  --color-auction-won-border: #BBF7D0;
  --color-auction-won-accent: #16A34A;

  /* Auction — Lost */
  --color-auction-lost-bg:     #FEF2F2;
  --color-auction-lost-text:   #7F1D1D;
  --color-auction-lost-border: #FECACA;
  --color-auction-lost-accent: #DC2626;

  /* Auction — Outbid */
  --color-auction-outbid-bg:     #FFF7ED;
  --color-auction-outbid-text:   #9A3412;
  --color-auction-outbid-border: #FDBA74;
  --color-auction-outbid-accent: #EA580C;
```

With:
```css
  /* Auction — Active */
  --color-auction-active-bg:     #F0F9FF;
  --color-auction-active-text:   #3A7FBE;
  --color-auction-active-border: #BAE6FD;
  --color-auction-active-accent: #4A8DB8;

  /* Auction — Ending Soon */
  --color-auction-ending-bg:     #FFFBEB;
  --color-auction-ending-text:   #9A6B3A;
  --color-auction-ending-border: #FDE68A;
  --color-auction-ending-accent: #C8933A;

  /* Auction — Critical */
  --color-auction-critical-bg:     #FEF2F2;
  --color-auction-critical-text:   #9B4848;
  --color-auction-critical-border: #FECACA;
  --color-auction-critical-accent: #B55555;

  /* Auction — Closed */
  --color-auction-closed-bg:     #F0F0F5;
  --color-auction-closed-text:   #5C5E62;
  --color-auction-closed-border: #E2E2EC;
  --color-auction-closed-accent: #8E8E8E;

  /* Auction — Won */
  --color-auction-won-bg:     #F0FDF4;
  --color-auction-won-text:   #2A5E3A;
  --color-auction-won-border: #BBF7D0;
  --color-auction-won-accent: #3A8F5A;

  /* Auction — Lost */
  --color-auction-lost-bg:     #FEF2F2;
  --color-auction-lost-text:   #7A3A3A;
  --color-auction-lost-border: #FECACA;
  --color-auction-lost-accent: #B55555;

  /* Auction — Outbid */
  --color-auction-outbid-bg:     #FFF7ED;
  --color-auction-outbid-text:   #844530;
  --color-auction-outbid-border: #FDBA74;
  --color-auction-outbid-accent: #C06835;
```

- [ ] **Step 2: Verify**

Navigate to `http://localhost:3000` and find an auction card with a status badge. The badge colors should be recognisably the same hue but noticeably less saturated — softer blue, amber, red. They should read as supporting rather than competing with the indigo CTA.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: mute auction state text/accent colors ~30% saturation"
```

---

### Task 7: Update `.dark` surface, text, and border tokens

**Files:**
- Modify: `frontend/app/globals.css:312-361`

- [ ] **Step 1: Replace `.dark` surface block (lines 313–321)**

Replace this:
```css
  /* Surface / Background */
  --color-bg-base:        #0F0F0F;
  --color-bg-elevated:    #1A1A1F;
  --color-bg-overlay:     #242428;
  --color-bg-card:        #1E1E24;
  --color-bg-card-hover:  #25252E;
  --color-bg-modal:       rgba(18, 18, 22, 0.97);
  --color-bg-backdrop:    rgba(0, 0, 0, 0.6);
  --color-bg-sidebar:     #16161B;
```

With:
```css
  /* Surface / Background */
  --color-bg-base:        #171A20;
  --color-bg-elevated:    #1E2128;
  --color-bg-overlay:     #252830;
  --color-bg-card:        #1E2128;
  --color-bg-card-hover:  #252830;
  --color-bg-modal:       rgba(23, 26, 32, 0.97);
  --color-bg-backdrop:    rgba(0, 0, 0, 0.6);
  --color-bg-sidebar:     #171A20;
```

- [ ] **Step 2: Replace `.dark` text block (lines 323–331)**

Replace this:
```css
  /* Text */
  --color-text-primary:    #F0F0F4;
  --color-text-secondary:  #A0A0B0;
  --color-text-tertiary:   #6A6A7E;
  --color-text-disabled:   #3A3A4A;
  --color-text-inverse:    #0F0F0F;
  --color-text-brand:      #8483F5;
  --color-text-link:       #8483F5;
  --color-text-link-hover: #A5A4F8;
```

With:
```css
  /* Text */
  --color-text-primary:    #E8E8EC;
  --color-text-secondary:  #9A9A9E;
  --color-text-tertiary:   #62626A;
  --color-text-disabled:   #3A3A40;
  --color-text-inverse:    #171A20;
  --color-text-brand:      #8483F5;
  --color-text-link:       #8483F5;
  --color-text-link-hover: #A5A4F8;
```

- [ ] **Step 3: Replace `.dark` border block (lines 357–361)**

Replace this:
```css
  /* Border */
  --color-border-default: #2A2A35;
  --color-border-strong:  #3A3A4A;
  --color-border-focus:   #6366F1;
  --color-border-error:   #EF4444;
```

With:
```css
  /* Border */
  --color-border-default: #2A2D34;
  --color-border-strong:  #3A3D46;
  --color-border-focus:   #6366F1;
  --color-border-error:   #EF4444;
```

- [ ] **Step 4: Verify**

Toggle dark mode. Background should be Carbon Dark warm near-black (`#171A20`). Text should be warm off-white. Card surfaces should be slightly lighter than base but without the blue-purple tint.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: update dark mode surface, text, border tokens to Carbon Dark family"
```

---

### Task 8: Mute auction state colors — dark mode

**Files:**
- Modify: `frontend/app/globals.css:363-411`

- [ ] **Step 1: Replace `.dark` auction state block (lines 363–403)**

Replace this:
```css
  /* Auction — Active */
  --color-auction-active-bg:     #001B2E;
  --color-auction-active-text:   #38BDF8;
  --color-auction-active-border: #0369A1;
  --color-auction-active-accent: #38BDF8;

  /* Auction — Ending Soon */
  --color-auction-ending-bg:     #1C1200;
  --color-auction-ending-text:   #FCD34D;
  --color-auction-ending-border: #78350F;
  --color-auction-ending-accent: #F59E0B;

  /* Auction — Critical */
  --color-auction-critical-bg:     #1C0000;
  --color-auction-critical-text:   #F87171;
  --color-auction-critical-border: #7F1D1D;
  --color-auction-critical-accent: #EF4444;

  /* Auction — Closed */
  --color-auction-closed-bg:     #1A1A1F;
  --color-auction-closed-text:   #6A6A7E;
  --color-auction-closed-border: #2A2A35;
  --color-auction-closed-accent: #6A6A7E;

  /* Auction — Won */
  --color-auction-won-bg:     #052E16;
  --color-auction-won-text:   #86EFAC;
  --color-auction-won-border: #166534;
  --color-auction-won-accent: #22C55E;

  /* Auction — Lost */
  --color-auction-lost-bg:     #1C0000;
  --color-auction-lost-text:   #FCA5A5;
  --color-auction-lost-border: #7F1D1D;
  --color-auction-lost-accent: #EF4444;

  /* Auction — Outbid */
  --color-auction-outbid-bg:     #180900;
  --color-auction-outbid-text:   #FB923C;
  --color-auction-outbid-border: #7C2D12;
  --color-auction-outbid-accent: #F97316;
```

With:
```css
  /* Auction — Active */
  --color-auction-active-bg:     #001B2E;
  --color-auction-active-text:   #6AAAD4;
  --color-auction-active-border: #0369A1;
  --color-auction-active-accent: #5A9EC8;

  /* Auction — Ending Soon */
  --color-auction-ending-bg:     #1C1200;
  --color-auction-ending-text:   #C8A84A;
  --color-auction-ending-border: #78350F;
  --color-auction-ending-accent: #B8883A;

  /* Auction — Critical */
  --color-auction-critical-bg:     #1C0000;
  --color-auction-critical-text:   #C87878;
  --color-auction-critical-border: #7F1D1D;
  --color-auction-critical-accent: #BE5555;

  /* Auction — Closed */
  --color-auction-closed-bg:     #1A1A1F;
  --color-auction-closed-text:   #62626A;
  --color-auction-closed-border: #2A2A35;
  --color-auction-closed-accent: #62626A;

  /* Auction — Won */
  --color-auction-won-bg:     #052E16;
  --color-auction-won-text:   #5EA87A;
  --color-auction-won-border: #166534;
  --color-auction-won-accent: #3A9060;

  /* Auction — Lost */
  --color-auction-lost-bg:     #1C0000;
  --color-auction-lost-text:   #C88888;
  --color-auction-lost-border: #7F1D1D;
  --color-auction-lost-accent: #BE5555;

  /* Auction — Outbid */
  --color-auction-outbid-bg:     #180900;
  --color-auction-outbid-text:   #C87838;
  --color-auction-outbid-border: #7C2D12;
  --color-auction-outbid-accent: #B86030;
```

- [ ] **Step 2: Verify**

In dark mode, check any page with status badges. Badge accent colors should be visibly less vivid — blue, amber, red remain readable but no longer pop against the Carbon Dark background.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: mute dark mode auction state text/accent colors ~30% saturation"
```

---

### Task 9: Update `@theme inline` forwarding block

**Files:**
- Modify: `frontend/app/globals.css:558-570`

- [ ] **Step 1: Update the shadow forwarding section (lines 558–570)**

Replace this:
```css
  /* Shadows */
  --shadow-none:    var(--shadow-none);
  --shadow-xs:      var(--shadow-xs);
  --shadow-sm:      var(--shadow-sm);
  --shadow-md:      var(--shadow-md);
  --shadow-lg:      var(--shadow-lg);
  --shadow-xl:      var(--shadow-xl);
  --shadow-2xl:     var(--shadow-2xl);
  --shadow-inner:   var(--shadow-inner);
  --shadow-brand:   var(--shadow-brand);
  --shadow-danger:  var(--shadow-danger);
  --shadow-success: var(--shadow-success);
}
```

With:
```css
  /* Shadows */
  --shadow-none:    var(--shadow-none);
  --shadow-xs:      var(--shadow-xs);
  --shadow-sm:      var(--shadow-sm);
  --shadow-md:      var(--shadow-md);
  --shadow-lg:      var(--shadow-lg);
  --shadow-xl:      var(--shadow-xl);
  --shadow-2xl:     var(--shadow-2xl);
  --shadow-inner:   var(--shadow-inner);
  --shadow-brand:   var(--shadow-brand);
  --shadow-danger:  var(--shadow-danger);
  --shadow-success: var(--shadow-success);
  --shadow-frosted: var(--shadow-frosted);
  --shadow-overlay: var(--shadow-overlay);
}
```

- [ ] **Step 2: Verify the full token system**

```bash
# Confirm --font-size-hero is defined
grep "font-size-hero" frontend/app/globals.css
# Expected: 1 line — the :root definition

# Confirm old font weights are gone
grep "font-weight-semibold\|font-weight-bold\|font-weight-extrabold" frontend/app/globals.css
# Expected: no output

# Confirm shadow tokens resolve to none
grep "shadow-xs:\|shadow-sm:\|shadow-md:\|shadow-lg:\|shadow-brand:\|shadow-danger:\|shadow-success:" frontend/app/globals.css | grep -v "var("
# Expected: all show `none`

# Confirm border radius base
grep "\-\-radius:" frontend/app/globals.css
# Expected: --radius: 4px
```

- [ ] **Step 3: Final visual regression check**

With dev server running (`npm run dev` in `frontend/`):

| Page | URL | What to check |
|------|-----|---------------|
| Homepage | `http://localhost:3000` | No shadow on cards, warmer text, Light Ash elevated sections, sharp 4px button corners |
| Auctions | `http://localhost:3000/auctions` | Status badges muted but readable, no card hover shadow |
| Dark mode (any page) | Toggle `.dark` class | Carbon Dark base, warm text, no blue-purple tint on surfaces |
| Header | Scroll down | Frosted effect via backdrop-blur still works (no shadow but blur remains) |

- [ ] **Step 4: Commit**

```bash
git add frontend/app/globals.css
git commit -m "style: add frosted/overlay to @theme inline; complete Tesla token migration"
```

---

## Self-Review

**Spec coverage:**
- ✅ shadcn vars updated (Tasks 1–2)
- ✅ Surface tokens → white/Light Ash (Task 3)
- ✅ Text hierarchy → Carbon Dark/Graphite/Pewter/Silver Fog (Task 3)
- ✅ Border tokens → Cloud Gray/Pale Silver (Task 3)
- ✅ All shadow tokens → `none` (Task 4)
- ✅ `--shadow-frosted` and `--shadow-overlay` added (Tasks 4, 9)
- ✅ Border radius collapsed to 4px/12px (Task 5)
- ✅ `--ease-tesla` and `--duration-tesla` added (Task 5)
- ✅ `--font-size-hero: 2.5rem` added (Task 5)
- ✅ `--font-weight-semibold/bold/extrabold` removed (Task 5)
- ✅ Auction state text/accent muted in light mode (Task 6)
- ✅ Dark surface/text/border → Carbon Dark family (Task 7)
- ✅ Auction state text/accent muted in dark mode (Task 8)
- ✅ `@theme inline` updated with new tokens (Task 9)
- ✅ No component files changed — pure CSS token layer

**No placeholder steps, no "TBD" entries, all code shown in full.**
