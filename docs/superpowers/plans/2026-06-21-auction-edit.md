# Auction Edit Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the broken auction edit flow — pre-populated fields, per-image add/remove, date/time pickers, and a publish action that actually transitions auction status.

**Architecture:** Six isolated changes in dependency order: backend guard readability → service method → format utility → new image component → form refactor → success state. Each task produces something independently testable before the next begins.

**Tech Stack:** Next.js 16.2 (App Router), TypeScript strict, Tailwind CSS v4, shadcn/ui, Lucide icons, Spring Boot 3 (backend)

## Global Constraints

- No `any` types in TypeScript
- Use Lucide icons only — no other icon library
- Follow existing thumbnail grid style from `ImageUploadGrid` (aspect-[4/3], `rounded-md`, `border-[var(--color-border-default)]`)
- Accept only `image/jpeg` and `image/png` (matches existing `ImageUploadGrid` restriction)
- Backend image limit: 1–10 images per auction
- Do not commit without explicit user approval

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `backend/.../AuctionServiceImpl.java` | Modify | Guard readability — same semantics, positive expression |
| `frontend/services/auction.service.ts` | Modify | Add `publishAuction` method |
| `frontend/lib/format.ts` | Modify | Add `toDatetimeLocal` utility |
| `frontend/components/seller/DraftImageManager.tsx` | Create | Mixed existing+new image grid with remove + add |
| `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx` | Modify | Pre-population, datetime pickers, DraftImageManager, publish flow, success state |

> **Note:** The frontend has no test suite (`npm test` is not configured). Verification steps are manual browser checks. The backend has a real test suite.

---

## Task 1: Backend — guard readability in `updateAuction`

**Files:**
- Modify: `backend/auction-service/src/main/java/com/bidnow/auction/service/impl/AuctionServiceImpl.java:316-319`
- Test: `backend/auction-service/src/test/java/com/bidnow/auction/service/impl/AuctionServiceImplTest.java` (run existing, no changes)

**Interfaces:**
- Produces: nothing new — pure readability change, same runtime behaviour

- [ ] **Step 1: Run existing tests to establish baseline**

```bash
cd backend
mvn test -pl auction-service -Dtest=AuctionServiceImplTest -q
```

Expected: `BUILD SUCCESS` with all tests passing.

- [ ] **Step 2: Replace the double-negative guard**

In `AuctionServiceImpl.java`, find lines 316–319:

```java
        if (auction.getStatus() != AuctionStatus.DRAFT &&
                !auction.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Auction cannot be modified after it has started", ErrorCodes.INVALID_INPUT);
        }
```

Replace with:

```java
        boolean isEditable = auction.getStatus() == AuctionStatus.DRAFT ||
                (auction.getStatus() == AuctionStatus.SCHEDULED &&
                 auction.getStartTime().isAfter(OffsetDateTime.now()));
        if (!isEditable) {
            throw new BadRequestException("Auction cannot be modified after it has started", ErrorCodes.INVALID_INPUT);
        }
```

- [ ] **Step 3: Run tests again to confirm no regression**

```bash
mvn test -pl auction-service -Dtest=AuctionServiceImplTest -q
```

Expected: `BUILD SUCCESS` — same result as Step 1.

---

## Task 2: `publishAuction` service method

**Files:**
- Modify: `frontend/services/auction.service.ts`

**Interfaces:**
- Produces: `auctionService.publishAuction(id: string): Promise<ApiResponse<AuctionResponse>>`
- Consumed by: Task 5 (`submitForm` in `DraftEditForm`)

- [ ] **Step 1: Add the method to `auctionService`**

In `frontend/services/auction.service.ts`, add after `updateAuction`:

```ts
  async publishAuction(id: string): Promise<ApiResponse<AuctionResponse>> {
    const response = await apiFetch(`/api/v1/auctions/${id}/publish`, { method: 'POST' })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend
npx tsc --noEmit
```

Expected: no errors.

---

## Task 3: `toDatetimeLocal` utility

**Files:**
- Modify: `frontend/lib/format.ts`

**Interfaces:**
- Produces: `toDatetimeLocal(date: Date): string` — formats a Date as `YYYY-MM-DDTHH:mm` for `<input type="datetime-local">`
- Consumed by: Task 5 (initial state of `startTime` / `endTime` in `DraftEditForm`)

- [ ] **Step 1: Add the function to `lib/format.ts`**

Append to the end of `frontend/lib/format.ts`:

```ts
export function toDatetimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend
npx tsc --noEmit
```

Expected: no errors.

---

## Task 4: `DraftImageManager` component

**Files:**
- Create: `frontend/components/seller/DraftImageManager.tsx`

**Interfaces:**
- Produces:
  ```ts
  export type ManagedImage =
    | { kind: 'existing'; id: string; url: string }
    | { kind: 'new'; file: File; preview: string }

  export function DraftImageManager(props: {
    images:   ManagedImage[]
    onChange: (images: ManagedImage[]) => void
  }): JSX.Element
  ```
- Consumed by: Task 5 (`DraftEditForm` in `manage/page.tsx`)

- [ ] **Step 1: Create the component file**

Create `frontend/components/seller/DraftImageManager.tsx`:

```tsx
'use client'

import { useRef, useEffect } from 'react'
import { X, Star } from 'lucide-react'
import { cn } from '@/lib/utils'

export type ManagedImage =
  | { kind: 'existing'; id: string; url: string }
  | { kind: 'new'; file: File; preview: string }

interface DraftImageManagerProps {
  images:   ManagedImage[]
  onChange: (images: ManagedImage[]) => void
}

const MAX_FILES = 10
const ACCEPT    = ['image/jpeg', 'image/png']

export function DraftImageManager({ images, onChange }: DraftImageManagerProps) {
  const inputRef   = useRef<HTMLInputElement>(null)
  const createdRef = useRef<string[]>([])

  useEffect(() => {
    return () => {
      createdRef.current.forEach(url => URL.revokeObjectURL(url))
    }
  }, [])

  function handleFiles(files: FileList | null) {
    if (!files) return
    const remaining = MAX_FILES - images.length
    const newEntries: ManagedImage[] = Array.from(files)
      .slice(0, remaining)
      .filter(f => ACCEPT.includes(f.type))
      .map(file => {
        const preview = URL.createObjectURL(file)
        createdRef.current.push(preview)
        return { kind: 'new' as const, file, preview }
      })
    onChange([...images, ...newEntries])
  }

  function remove(index: number) {
    const img = images[index]
    if (img.kind === 'new') {
      URL.revokeObjectURL(img.preview)
      createdRef.current = createdRef.current.filter(u => u !== img.preview)
    }
    onChange(images.filter((_, i) => i !== index))
  }

  const canAdd = images.length < MAX_FILES

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-5 gap-2">
        {images.map((img, i) => {
          const src = img.kind === 'existing' ? img.url : img.preview
          return (
            <div
              key={img.kind === 'existing' ? img.id : img.preview}
              className="relative aspect-[4/3] overflow-hidden rounded-md border border-[var(--color-border-default)] bg-[var(--color-bg-elevated)]"
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={src} alt="" className="size-full object-cover" />
              {i === 0 && (
                <div className="absolute top-1 left-1 flex items-center gap-0.5 rounded-sm bg-foreground px-1 py-0.5 text-[8px] font-medium text-background">
                  <Star className="size-2" />
                  PRIMARY
                </div>
              )}
              <button
                type="button"
                onClick={() => remove(i)}
                className={cn(
                  'absolute top-1 right-1 flex size-5 items-center justify-center rounded-full',
                  'bg-foreground/80 text-background hover:bg-foreground',
                  'transition-colors duration-[var(--duration-tesla)]',
                )}
              >
                <X className="size-3" />
              </button>
            </div>
          )
        })}

        {canAdd && (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="aspect-[4/3] flex flex-col items-center justify-center gap-1 rounded-md border border-dashed border-border text-muted-foreground hover:border-[var(--color-border-strong)] transition-colors duration-[var(--duration-tesla)]"
          >
            <span className="text-lg leading-none">+</span>
            <span className="text-[10px]">Add more</span>
          </button>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPT.join(',')}
        className="hidden"
        onChange={e => handleFiles(e.target.files)}
      />

      <p className="text-xs text-muted-foreground">
        {images.length} / {MAX_FILES} images · first image is the primary thumbnail
      </p>
    </div>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend
npx tsc --noEmit
```

Expected: no errors.

---

## Task 5: `DraftEditForm` — pre-population, datetime pickers, image manager, publish

**Files:**
- Modify: `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx`

**Interfaces:**
- Consumes:
  - `ManagedImage`, `DraftImageManager` from `@/components/seller/DraftImageManager`
  - `toDatetimeLocal` from `@/lib/format`
  - `auctionService.publishAuction(id)` from Task 2
- Produces: working save-draft and publish flows (verified manually)

- [ ] **Step 1: Update imports at the top of `manage/page.tsx`**

Replace:
```ts
import { ImageUploadGrid }      from '@/components/seller/ImageUploadGrid'
```
With:
```ts
import { DraftImageManager }    from '@/components/seller/DraftImageManager'
import type { ManagedImage }    from '@/components/seller/DraftImageManager'
```

Add `toDatetimeLocal` to the format import:
```ts
import { formatCurrency, toDatetimeLocal } from '@/lib/format'
```

- [ ] **Step 2: Update all state initialisers in `DraftEditForm`**

Replace the entire state block (lines 68–77 in the current file) with:

```ts
  const [title,         setTitle]         = useState(auction.title)
  const [description,   setDescription]   = useState(auction.description)
  const [categoryId,    setCategoryId]    = useState(auction.categoryId)
  const [startingPrice, setStartingPrice] = useState(auction.startingPrice)
  const [bidIncrement,  setBidIncrement]  = useState(auction.bidIncrement)
  const [depositAmount, setDepositAmount] = useState(auction.depositAmount)
  const [startTime,     setStartTime]     = useState(toDatetimeLocal(auction.startsAt))
  const [endTime,       setEndTime]       = useState(toDatetimeLocal(auction.endsAt))
  const [managedImages, setManagedImages] = useState<ManagedImage[]>(() =>
    auction.images
      .slice()
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .map(img => ({ kind: 'existing' as const, id: img.id, url: img.imageUrl }))
  )
  const [submitting,    setSubmitting]    = useState(false)
  const [categories,    setCategories]    = useState<AuctionCategoryResponse[]>([])
```

- [ ] **Step 3: Update checklist derived values**

Replace:
```ts
  const primaryImageUrl = auction.images.find(img => img.isPrimary)?.imageUrl
  const hasImages      = images.length > 0 || !!primaryImageUrl
```
With:
```ts
  const hasImages = managedImages.length > 0
```

- [ ] **Step 4: Rewrite `submitForm`**

Replace the entire `submitForm` function with:

```ts
  const submitForm = async (publish: boolean) => {
    setSubmitting(true)
    try {
      const keptUrls = managedImages
        .filter((img): img is { kind: 'existing'; id: string; url: string } => img.kind === 'existing')
        .map(img => img.url)

      const newUrls: string[] = []
      for (const img of managedImages.filter(
        (img): img is { kind: 'new'; file: File; preview: string } => img.kind === 'new'
      )) {
        const presigned = await mediaService.getPresignedUrl(img.file.name, img.file.type)
        await mediaService.uploadToS3(presigned.uploadUrl, img.file)
        newUrls.push(presigned.publicUrl)
      }

      await auctionService.updateAuction(auction.id, {
        title,
        description,
        categoryId,
        startingPrice,
        bidIncrement,
        depositAmount,
        startTime: new Date(startTime).toISOString(),
        endTime:   new Date(endTime).toISOString(),
        imageUrls: [...keptUrls, ...newUrls],
      })

      if (publish) {
        await auctionService.publishAuction(auction.id)
        onPublish()
      } else {
        onSave()
      }
    } catch (error) {
      toast.error(getErrorMessage(error, 'Failed to save auction. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }
```

- [ ] **Step 5: Replace the Images field in the JSX**

Replace:
```tsx
        <div className="flex flex-col gap-1.5">
          <Label>Images</Label>
          <p className="text-xs text-muted-foreground mb-1">Upload new images to replace existing ones.</p>
          <ImageUploadGrid images={images} onChange={setImages} />
        </div>
```
With:
```tsx
        <div className="flex flex-col gap-1.5">
          <Label>Images</Label>
          <DraftImageManager images={managedImages} onChange={setManagedImages} />
        </div>
```

- [ ] **Step 6: Replace the Duration selector with datetime pickers**

Replace:
```tsx
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="duration">Duration (days)</Label>
          <Select value={String(durationDays)} onValueChange={v => setDurationDays(Number(v))}>
            <SelectTrigger id="duration"><SelectValue /></SelectTrigger>
            <SelectContent>
              {[1, 3, 5, 7, 10, 14].map(d => (
                <SelectItem key={d} value={String(d)}>{d} day{d !== 1 ? 's' : ''}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
```
With:
```tsx
        <div className="grid grid-cols-2 gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="start-time">Start time</Label>
            <Input
              id="start-time"
              type="datetime-local"
              value={startTime}
              onChange={e => setStartTime(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="end-time">End time</Label>
            <Input
              id="end-time"
              type="datetime-local"
              value={endTime}
              onChange={e => setEndTime(e.target.value)}
            />
          </div>
        </div>
```

- [ ] **Step 7: Update the checklist item for images**

Replace:
```tsx
        <ChecklistItem done={!!primaryImageUrl} label="At least one image uploaded" />
```
With:
```tsx
        <ChecklistItem done={hasImages} label="At least one image uploaded" />
```

- [ ] **Step 8: Verify TypeScript compiles**

```bash
cd frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 9: Manual verification — save draft**

With the dev server running at `http://localhost:3000`:

1. Navigate to a Draft auction's manage page (`/seller/auctions/{id}/manage`)
2. Confirm all fields are pre-populated (title, description, category, starting price, bid increment, deposit, start time, end time)
3. Confirm existing images appear as thumbnails with ✕ buttons
4. Remove one image — confirm it disappears immediately
5. Add a new image via the "+" tile — confirm preview appears
6. Click **Save draft** — confirm toast success appears and page reloads with the updated data (including the merged image set)

---

## Task 6: Publish success state

**Files:**
- Modify: `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx`

**Interfaces:**
- Consumes: `published` state flag, `auction.startsAt` and `auction.status` after reload
- Produces: inline success UI replacing the edit form after successful publish

- [ ] **Step 1: Add `published` state and `handlePublish` callback in `ManageAuctionPage`**

After the existing state declarations (`deleteOpen`, `deleting`, `deleted`), add:

```ts
  const [published,  setPublished]  = useState(false)
```

Make `loadAuction` return its promise (needed to `await` before showing success):

```ts
  const loadAuction = useCallback((): Promise<void> => {
    setLoading(true)
    setFetchError(null)
    return auctionService.getAuctionById(id)
      .then(detail => {
        if (!detail) { setFetchError('Auction not found.'); return }
        setAuction(detail)
      })
      .catch(() => setFetchError('Failed to load auction.'))
      .finally(() => setLoading(false))
  }, [id])
```

Add the handler after the state declarations:

```ts
  const handlePublish = useCallback(async () => {
    await loadAuction()
    setPublished(true)
  }, [loadAuction])
```

- [ ] **Step 2: Update `onPublish` prop on `DraftEditForm`**

Replace:
```tsx
            <DraftEditForm
              auction={auction}
              onSave={loadAuction}
              onPublish={loadAuction}
            />
```
With:
```tsx
            <DraftEditForm
              auction={auction}
              onSave={loadAuction}
              onPublish={handlePublish}
            />
```

- [ ] **Step 3: Add the success state to the edit section**

Replace:
```tsx
        {/* ── Draft mode: edit form (with optional lock overlay) ── */}
        {!isLive && (
          <section id="edit-section" className="relative flex flex-col gap-3">
            <h2 className="font-medium text-sm">Edit auction</h2>
            {!editable && (
              <EditLockOverlay reason="This auction is live and has active bids — editing is locked." />
            )}
            <DraftEditForm
              auction={auction}
              onSave={loadAuction}
              onPublish={handlePublish}
            />
          </section>
        )}
```
With:
```tsx
        {/* ── Draft / Scheduled mode ── */}
        {!isLive && (
          <section id="edit-section" className="relative flex flex-col gap-3">
            {published ? (
              <div className="flex flex-col items-center justify-center gap-4 py-20 text-center">
                <div className="flex size-12 items-center justify-center rounded-full bg-[var(--color-success-subtle)]">
                  <CheckCircle2 className="size-6 text-[var(--color-success-text)]" />
                </div>
                <div className="flex flex-col gap-1">
                  <p className="font-medium">Auction published</p>
                  <p className="text-sm text-muted-foreground">
                    {auction.status === AuctionStatus.Scheduled
                      ? `Scheduled to go live on ${auction.startsAt.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}.`
                      : 'Your auction is now live.'}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" render={<Link href={`/auctions/${auction.id}`} />} nativeButton={false}>
                    View auction
                  </Button>
                  <Button variant="outline" size="sm" render={<Link href="/seller/auctions" />} nativeButton={false}>
                    Back to my auctions
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <h2 className="font-medium text-sm">Edit auction</h2>
                {!editable && (
                  <EditLockOverlay reason="This auction is live and has active bids — editing is locked." />
                )}
                <DraftEditForm
                  auction={auction}
                  onSave={loadAuction}
                  onPublish={handlePublish}
                />
              </>
            )}
          </section>
        )}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Manual verification — publish flow**

With the dev server running at `http://localhost:3000`:

1. Navigate to a Draft auction's manage page
2. Fill in all checklist fields (title, description, category, pricing, at least one image)
3. Confirm **Publish auction** button is enabled
4. Click **Publish auction**
5. Confirm:
   - Button shows loading state during submission
   - Success panel appears with a checkmark and the scheduled start date
   - Header badge changes from **Draft** to **Scheduled** (or **Active**)
   - **View auction** link navigates to the public auction page
   - **Back to my auctions** link navigates to `/seller/auctions`
6. Navigate away and back to the manage page — confirm status badge is correct and edit form is gone (Scheduled shows edit form with or without lock; Active shows monitor)
