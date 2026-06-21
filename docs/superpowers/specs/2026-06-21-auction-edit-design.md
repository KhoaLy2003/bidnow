# Auction Edit Feature Design

**Date:** 2026-06-21
**Status:** Approved

## Overview

Fix the broken auction edit flow on the seller manage page and deliver a fully operational edit experience: pre-populated fields, per-image add/remove management, explicit start/end time pickers, and a publish action that actually transitions auction status.

---

## Context & Bugs Being Fixed

The verification run identified four blocking issues in `app/(dashboard)/seller/auctions/[id]/manage/page.tsx`:

1. `parseStatus` had no `DRAFT` case → draft auctions rendered as Completed, edit form hidden.
2. `DraftEditForm` did not pre-populate `description`, `bidIncrement`, or `depositAmount`.
3. `submitForm(publish=true)` called `updateAuction` only — never called `POST /{id}/publish`.
4. Image save always sent `imageUrls: []` when no new files selected → backend 400.

Items 1 and the `SellerAuction` → `AuctionDetail` refactor have already been applied. Items 2–4 remain.

---

## Scope

**In scope:**
- Pre-populate all form fields from `AuctionDetail`
- Replace `durationDays` selector with explicit start/end datetime pickers
- `DraftImageManager`: per-image remove + add new, merged on save
- Publish flow: `updateAuction` → `POST /{id}/publish` → inline success state
- `auctionService.publishAuction` method
- Backend guard readability improvement in `AuctionServiceImpl.updateAuction`

**Out of scope:**
- Buy-now price editing
- Multi-step / wizard form
- Drag-to-reorder images

---

## Editable Statuses

The frontend `canEdit` and backend `updateAuction` both permit edits for:
- `DRAFT` — always editable
- `SCHEDULED` — editable while `startTime > now`

`ACTIVE` and all terminal statuses (`CANCELLED`, `COMPLETED`, `FAILED`, `REJECTED`) are blocked by the backend. The manage page shows the live monitor for `ACTIVE` and the edit form (with lock overlay) for everything else non-live.

---

## Section 1 — Data model & status mapping (already done)

- `AuctionStatus.Draft = 'draft'` added to `lib/design-tokens.ts`
- `parseStatus` in `types/mappers/auction.mapper.ts` handles `DRAFT`
- Manage page stores `AuctionDetail` directly (no `SellerAuction` wrapper)
- `canEdit` / `canDelete` operate on `AuctionDetail`

---

## Section 2 — DraftEditForm pre-population & timing

### Pre-population

All state initialises from the `AuctionDetail` prop:

```ts
const [title,         setTitle]         = useState(auction.title)
const [description,   setDescription]   = useState(auction.description)
const [categoryId,    setCategoryId]    = useState(auction.categoryId)
const [startingPrice, setStartingPrice] = useState(auction.startingPrice)
const [bidIncrement,  setBidIncrement]  = useState(auction.bidIncrement)
const [depositAmount, setDepositAmount] = useState(auction.depositAmount)
const [startTime,     setStartTime]     = useState(toDatetimeLocal(auction.startsAt))
const [endTime,       setEndTime]       = useState(toDatetimeLocal(auction.endsAt))
```

`toDatetimeLocal(date: Date): string` formats as `YYYY-MM-DDTHH:mm` for `<input type="datetime-local">`.

### Timing fields

The `durationDays` selector is removed. Two `datetime-local` inputs replace it:

- **Start time** — defaults to `auction.startsAt`; user may change
- **End time** — defaults to `auction.endsAt`; user may change

On save, these are sent as ISO strings. For Scheduled auctions, passing the unchanged values is safe — the backend reschedules only when `startTime` differs from the stored value.

### Backend timing behaviour (confirmed)

- Start time change on Scheduled: backend cancels old activation job, schedules new one at new start time. ✅
- End time change on Scheduled: stored in DB; closure job is scheduled at activation time using the current DB value. ✅
- No frontend workaround needed.

---

## Section 3 — DraftImageManager

Replaces `ImageUploadGrid` for the edit form. A new `DraftImageManager` component in `components/seller/`.

### Image state type

```ts
type ManagedImage =
  | { kind: 'existing'; id: string; url: string }
  | { kind: 'new';      file: File; preview: string }  // preview = objectURL
```

### Initial state

```ts
const [managedImages, setManagedImages] = useState<ManagedImage[]>(
  auction.images
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map(img => ({ kind: 'existing', id: img.id, url: img.imageUrl }))
)
```

### Component behaviour

- Renders each image as a fixed-size thumbnail with an ✕ button (both existing and new look identical)
- A "+" tile at the end opens a file picker; each selected file becomes a `kind: 'new'` entry
- Removing an entry filters it from state — no API call; server is only updated on form save
- "+" tile is hidden when `managedImages.length >= 10` (backend max)
- `objectURL`s are revoked on unmount via `useEffect` cleanup

### Props

```ts
interface DraftImageManagerProps {
  images:    ManagedImage[]
  onChange:  (images: ManagedImage[]) => void
}
```

### On save — image resolution

```ts
const keptUrls   = managedImages
  .filter(img => img.kind === 'existing')
  .map(img => img.url)

const newUrls: string[] = []
for (const img of managedImages.filter(img => img.kind === 'new')) {
  const presigned = await mediaService.getPresignedUrl(img.file.name, img.file.type)
  await mediaService.uploadToS3(presigned.uploadUrl, img.file)
  newUrls.push(presigned.publicUrl)
}

const imageUrls = [...keptUrls, ...newUrls]
// imageUrls.length is guaranteed >= 1 if managedImages.length >= 1
```

### Checklist item

`hasImages = managedImages.length > 0` (replaces `!!auction.primaryImageUrl`).

---

## Section 4 — Publish flow & success state

### auctionService.publishAuction

New method added to `services/auction.service.ts`:

```ts
async publishAuction(id: string): Promise<ApiResponse<AuctionResponse>> {
  const response = await apiFetch(`/api/v1/auctions/${id}/publish`, { method: 'POST' })
  if (!response.ok) {
    const error = await response.json()
    throw error
  }
  return response.json()
}
```

### submitForm

```ts
const submitForm = async (publish: boolean) => {
  setSubmitting(true)
  try {
    // 1. Resolve images
    const imageUrls = await resolveImages(managedImages)

    // 2. Save fields
    await auctionService.updateAuction(auction.id, {
      title, description, categoryId,
      startingPrice, bidIncrement, depositAmount,
      startTime: new Date(startTime).toISOString(),
      endTime:   new Date(endTime).toISOString(),
      imageUrls,
    })

    // 3. Publish if requested
    if (publish) {
      await auctionService.publishAuction(auction.id)
      onPublish()   // triggers success state on parent
    } else {
      onSave()      // triggers loadAuction on parent
    }
  } catch (error) {
    toast.error(getErrorMessage(error, 'Failed to save auction. Please try again.'))
  } finally {
    setSubmitting(false)
  }
}
```

### Success state

`onPublish` in the manage page sets a `published` flag (distinct from `deleted`). When `published` is true, the edit form section is replaced with:

```
┌──────────────────────────────────────┐
│           ✓                          │
│     Auction published                │
│  Your auction is now scheduled to    │
│  go live on Jun 25 at 10:00 AM.      │
│                                      │
│  [View auction]  [Back to my auctions]│
└──────────────────────────────────────┘
```

The page header badge updates to Scheduled/Active because `loadAuction` is called before showing the success state, reflecting the true status.

---

## Section 5 — Backend guard readability

In `AuctionServiceImpl.updateAuction`, replace the double-negative guard:

```java
// Before
if (auction.getStatus() != AuctionStatus.DRAFT &&
        !auction.getStartTime().isAfter(OffsetDateTime.now())) {
    throw new BadRequestException("Auction cannot be modified after it has started", ...);
}

// After
boolean isEditable = auction.getStatus() == AuctionStatus.DRAFT ||
        (auction.getStatus() == AuctionStatus.SCHEDULED &&
         auction.getStartTime().isAfter(OffsetDateTime.now()));
if (!isEditable) {
    throw new BadRequestException("Auction cannot be modified after it has started", ...);
}
```

Same semantics, explicit allowed set.

---

## UI Layout

```
┌─────────────────────────────────────────────────────────┐
│ ← My auctions                                           │
│ Vintage Rolex Submariner                    [Draft]     │
│ a3f8...                       [View] [Edit] [Delete]    │
├─────────────────────────────────────────────────────────┤
│ Edit auction                                            │
│                                                         │
│ Title          [Vintage Rolex Submariner              ] │
│ Description    [1967 Rolex Submariner ref. 5513...    ] │
│                [                                      ] │
│ Images         [img ✕][img ✕][img ✕][preview ✕][ + ]  │
│ Category       [Watches & Accessories              ▼ ] │
│ Starting price [$ 5,000.00    ]  Bid increment [$ 250] │
│ Deposit        ──────●──────── $500 (10%)               │
│ Start time     [2026-06-25  10:00                     ] │
│ End time       [2026-07-02  10:00                     ] │
│ ⚡ Anti-snipe notice                                    │
│                                                         │
│ ┌ Publish checklist ──────────────────────────────────┐ │
│ │ ✓ Title set                                         │ │
│ │ ✓ Description written                               │ │
│ │ ✓ Category selected                                 │ │
│ │ ✓ At least one image                                │ │
│ │ ✓ Pricing complete                                  │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ [Save draft]                    [Publish auction]       │
└─────────────────────────────────────────────────────────┘
```

---

## Files Changed

| File | Change |
|---|---|
| `lib/design-tokens.ts` | `AuctionStatus.Draft` added ✅ done |
| `types/mappers/auction.mapper.ts` | `parseStatus` handles DRAFT ✅ done |
| `app/(dashboard)/seller/auctions/[id]/manage/page.tsx` | Pre-population, datetime pickers, DraftImageManager, publish flow, success state |
| `components/seller/DraftImageManager.tsx` | New component |
| `services/auction.service.ts` | `publishAuction` method |
| `backend/.../AuctionServiceImpl.java` | Guard readability rewrite |
