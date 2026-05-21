You are designing the **Seller Auction Management** interface for BidNow, an auction platform.

## Design System (REQUIRED — follow strictly)

This interface must follow the **BidNow Tesla-inspired design system** defined in the attached DESIGN.md and docs/design-system.md. Key rules that override any default instincts:

- **Font weight:** `font-medium` (500) maximum. Never bold or semibold.
- **Letter spacing:** Zero everywhere. No tracking utilities.
- **Shadows:** None. All `--shadow-*` tokens are `none`. Depth through borders and z-index only.
- **Border radius:** 4px for buttons/inputs (`rounded-md`), 12px for larger cards/panels (`rounded-xl`).
- **Transitions:** `transition-[properties] duration-[333ms] ease-[cubic-bezier(0.5,0,0,0.75)]` — never `transition-all`.
- **Hover:** No lift transforms (`hover:-translate-y-*`). State changes via color transitions only.

## Existing UI Context

The attached screenshots show the current pages this feature must integrate with. The seller pages will live inside the existing `(dashboard)` layout shell: **Header + centered content area (max-w-1280px) + Footer + BottomNav**.

Existing components to reuse (do not redesign):

- `StatusBadge` — auction state pill using tokens: `active`, `ending-soon`, `critical`, `closed`, `won`, `lost`
- `CountdownTimer` — 3 states (normal / warning / critical), always `font-mono font-medium`
- `Button` (brand / outline / ghost variants), `Card`, `Input`, `Badge`, `Tabs`, `Dialog`, `Select` — all from shadcn/ui

## Context

This is a feature epic enabling sellers to create, manage, and monitor auction listings.

## User Story

As a registered user with seller role, I want to:

1. **Create new auction listings** — title, description, images, pricing, duration
2. **View my active auctions** — dashboard with real-time metrics
3. **View my historical auctions** — completed, failed, cancelled
4. **Monitor auction performance** — current price, bids, time remaining
5. **Manage auction settings** — update/delete only before auction starts (status = DRAFT)

## New Routes Needed

| Route                                             | Description                                  |
| ------------------------------------------------- | -------------------------------------------- |
| `app/(dashboard)/auctions/page.tsx`             | Seller dashboard — active + historical tabs |
| `app/(dashboard)/auctions/new/page.tsx`         | Multi-step creation form                     |
| `app/(dashboard)/auctions/[id]/manage/page.tsx` | Individual auction management view           |

Entry point: the existing **"Sell" button** in BottomNav (mobile center) and Header (desktop) navigates to `/auctions/new`.

## Screen Scope

Design these 3 screens:

### Screen 1: Seller Dashboard (`/auctions`)

Two-tab layout using shadcn `<Tabs>`:

**Active Tab** — table list with per-row:

- Primary image thumbnail (48×48 or aspect-4/3 card)
- Auction title (truncated, `font-medium text-sm`)
- Current bid / Starting price (`font-mono font-medium`)
- Total bids count
- `<CountdownTimer>` (reuse existing)
- `<StatusBadge>` (reuse existing)
- Quick actions: View / Edit / Delete (only if DRAFT or not yet started)

**Historical Tab** — same layout, fields:

- Final price (`font-mono font-medium`)
- Winner info (if completed)
- `<StatusBadge>` for closed/failed/cancelled/won/lost
- End date

Filters: Category dropdown (`<Select>`), Date range — above the tab content.
Pagination: 20 per page.

### Screen 2: Create Auction (`/auctions/new`)

Multi-step form with step indicator (4 steps):

**Step 1 — Basic Info**

- Title (5–255 chars)
- Description (min 20 chars, textarea)
- Category (`<Select>`)

**Step 2 — Images**

- Drag-and-drop upload zone + file picker fallback
- Preview grid (max 10, JPEG/PNG, 5MB each)
- Min 1 required

**Step 3 — Pricing & Duration**

- Starting Price (positive decimal, 2dp)
- Bid Increment (≥ 0.01)
- Buy It Now Price (optional, must be > starting price)
- Deposit Amount (5–20% of starting price — make this prominent with helper text)
- Duration: picker from 1 hour to 30 days
- Anti-sniping info notice: "Auction extends 5 min if bid placed in the final 5 min"

**Step 4 — Review & Submit**

- Summary of all fields (read-only)
- Submit to create as ACTIVE (or Save as DRAFT)

### Screen 3: Manage Auction (`/auctions/[id]/manage`)

- Auction detail (image, title, description, pricing — read-only if started)
- Real-time: `<CurrentBidDisplay>`, `<CountdownTimer>`, bid count, watchers
- Bid history list
- Edit / Delete buttons — visible only if DRAFT or before start time (show locked state otherwise)
- Status history / audit trail (if relevant)

## Form Validation

| Field            | Rule                                       |
| ---------------- | ------------------------------------------ |
| Title            | 5–255 chars, required                     |
| Description      | min 20 chars, required                     |
| Images           | 1–10 files, JPEG/PNG only, 5MB each       |
| Starting Price   | positive decimal, max 2dp                  |
| Bid Increment    | ≥ 0.01                                    |
| Buy It Now Price | optional; if set, must be > starting price |
| Deposit Amount   | 5–20% of starting price                   |
| Duration         | 1 hour – 30 days                          |

All validation: inline, immediate, linked to input field (no toast-only errors).

## Auction State Colors

Use the existing BidNow token system — do NOT use raw green/red/orange:

| State                       | Token                          | Visual      |
| --------------------------- | ------------------------------ | ----------- |
| Active                      | `--color-auction-active-*`   | Muted blue  |
| Ending Soon                 | `--color-auction-ending-*`   | Muted amber |
| Critical (< 1 min)          | `--color-auction-critical-*` | Muted red   |
| Closed / Failed / Cancelled | `--color-auction-closed-*`   | Gray        |
| Won                         | `--color-auction-won-*`      | Muted green |

## Interaction Patterns

- **Edit gate:** Show Edit/Delete only when `status === DRAFT` or `startTime > now`. Otherwise show a locked state with explanation.
- **Delete:** Confirmation dialog (`<Dialog>`), reason required (textarea).
- **Image upload:** Drag-and-drop zone → preview grid → reorder/remove before confirm.
- **Step navigation:** "Next" validates current step before advancing. "Back" preserves entered data.

## Loading & Error States

- **List loading:** Skeleton rows (use shadcn `<Skeleton>`)
- **Image upload:** Per-image progress indicator
- **Form submission:** Button disabled + spinner (`<Loader2>`)
- **API errors:** Inline field errors or a dismissible error banner at page top

## Platform Context

- Timezone: Vietnam (VN) — display times in local timezone
- Currency: USD — all amounts use `formatCurrency(cents)` from `lib/format.ts`
- Amounts stored as cents (integers) — display with Geist Mono
- Language: English

## API Endpoints (for reference)

- `POST /api/v1/auctions` — create auction
- `GET /api/v1/auctions/my-auctions` — list seller's auctions (paginated)
- `PUT /api/v1/auctions/{id}` — update (pre-start only)
- `DELETE /api/v1/auctions/{id}` — soft delete (pre-start only)

## Deliverables

1. **Desktop + mobile mockups** for all 3 screens
2. **Component inventory** — which existing components are reused vs. new
3. **Step flow diagram** for the creation form
4. **State inventory** for the seller dashboard (empty state, loading, error, populated)
5. **Handoff specs** — component props, conditional states (edit-locked vs. editable), form validation states

## Resources to Attach

Before sending this prompt, attach the following files directly in the Claude Design chat:

1. `DESIGN.md` — Tesla design principle reference
2. `docs/design-system.md` — BidNow token layer and component patterns
