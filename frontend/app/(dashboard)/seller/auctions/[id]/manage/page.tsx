'use client'

import { useState, useEffect } from 'react'
import { useParams }     from 'next/navigation'
import Link              from 'next/link'
import { CheckCircle2, Circle, Trash2 } from 'lucide-react'
import { Button }        from '@/components/ui/button'
import { Input }         from '@/components/ui/input'
import { Textarea }      from '@/components/ui/textarea'
import { Label }         from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { AuctionMonitorPanel }  from '@/components/seller/AuctionMonitorPanel'
import { AuditTrail }           from '@/components/seller/AuditTrail'
import { EditLockOverlay }      from '@/components/seller/EditLockOverlay'
import { DeleteAuctionDialog }  from '@/components/seller/DeleteAuctionDialog'
import { AntiSnipeNotice }      from '@/components/seller/AntiSnipeNotice'
import { SellerStatusBadge }    from '@/components/seller/SellerStatusBadge'
import { DepositRangeInput }    from '@/components/seller/DepositRangeInput'
import { formatCurrency }       from '@/lib/format'
import { SellerAuctionStatus }  from '@/types/ui/seller.ui'
import type { SellerAuction, SellerBidItem, AuditEvent } from '@/types/ui/seller.ui'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'
import { ImageUploadGrid }      from '@/components/seller/ImageUploadGrid'
import { CurrencyInput }        from '@/components/ui/currency-input'
import { auctionService }       from '@/services/auction.service'
import { mediaService }         from '@/services/media.service'
import { useAuthStore }         from '@/store/authStore'

// ── Mock data ──────────────────────────────────────────────────
const now = Date.now()

const MOCK_AUCTIONS: Record<string, SellerAuction> = {
  'au-29481': {
    id: 'au-29481', title: 'Hasselblad 500C/M Medium Format — body + 80mm',
    categoryId: 'electronics', categoryName: 'Electronics',
    sellerId: 'me', startingPrice: 120_000, currentBid: 142_000,
    totalBids: 18,
    startsAt: new Date(now - 2 * 86_400_000),
    endsAt:   new Date(now + 2 * 3_600_000 + 14 * 60_000),
    createdAt: new Date(now - 3 * 86_400_000),
    status: SellerAuctionStatus.Active,
  },
  'au-29490': {
    id: 'au-29490', title: "Vintage Persian rug, 6'×9' — Tabriz",
    categoryId: 'furniture', categoryName: 'Home & Furniture',
    sellerId: 'me', startingPrice: 80_000, currentBid: 0,
    totalBids: 0,
    startsAt: new Date(now + 86_400_000),
    endsAt:   new Date(now + 6 * 86_400_000),
    createdAt: new Date(now - 86_400_000),
    status: SellerAuctionStatus.Draft,
  },
}

const MOCK_BIDS: SellerBidItem[] = [
  { id: 'b1', bidderName: 'mauve_42',   amount: 142_000, placedAt: new Date(now - 4 * 60_000),  isAutoBid: false, isWinning: true  },
  { id: 'b2', bidderName: 'dlrt',       amount: 137_000, placedAt: new Date(now - 11 * 60_000), isAutoBid: true,  isWinning: false },
  { id: 'b3', bidderName: 'noor.k',     amount: 132_000, placedAt: new Date(now - 22 * 60_000), isAutoBid: false, isWinning: false },
  { id: 'b4', bidderName: 'mauve_42',   amount: 127_000, placedAt: new Date(now - 38 * 60_000), isAutoBid: true,  isWinning: false },
  { id: 'b5', bidderName: 'user_xt91',  amount: 122_000, placedAt: new Date(now - 55 * 60_000), isAutoBid: false, isWinning: false },
  { id: 'b6', bidderName: 'dlrt',       amount: 120_000, placedAt: new Date(now - 90 * 60_000), isAutoBid: false, isWinning: false },
]

const MOCK_EVENTS: AuditEvent[] = [
  { timestamp: new Date(now - 4 * 60_000),        message: 'New bid — ₫142,000 by @mauve_42' },
  { timestamp: new Date(now - 11 * 60_000),       message: 'Auto-bid triggered — ₫137,000 by @dlrt' },
  { timestamp: new Date(now - 2 * 86_400_000),    message: 'Auction went live' },
  { timestamp: new Date(now - 3 * 86_400_000),    message: 'Auction published by seller' },
  { timestamp: new Date(now - 3 * 86_400_000 - 600_000), message: 'Auction created as draft' },
]

const DRAFT_EVENTS: AuditEvent[] = [
  { timestamp: new Date(now - 86_400_000),   message: 'Draft created' },
  { timestamp: new Date(now - 86_400_000 + 300_000), message: 'Images uploaded (0 files)' },
]
// ──────────────────────────────────────────────────────────────

function canEdit(auction: SellerAuction): boolean {
  if (auction.status === SellerAuctionStatus.Draft) return true
  if (auction.status === SellerAuctionStatus.Active && auction.startsAt > new Date()) return true
  return false
}

function canDelete(auction: SellerAuction): boolean {
  return canEdit(auction) && auction.totalBids === 0
}

function ChecklistItem({ done, label }: { done: boolean; label: string }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      {done
        ? <CheckCircle2 className="size-4 shrink-0 text-[var(--color-auction-won-text)]" />
        : <Circle className="size-4 shrink-0 text-muted-foreground" />
      }
      <span className={done ? 'text-foreground' : 'text-muted-foreground'}>{label}</span>
    </div>
  )
}

// ── Draft edit form ─────────────────────────────────────────────
interface DraftFormProps {
  auction:     SellerAuction
  onSave():    void
  onPublish(): void
}

function DraftEditForm({ auction, onSave, onPublish }: DraftFormProps) {
  const [title,       setTitle]       = useState(auction.title)
  const [description, setDescription] = useState('') // Fetch from details API later
  const [categoryId,  setCategoryId]  = useState(auction.categoryId)
  const [startingPrice, setStartingPrice] = useState(auction.startingPrice)
  const [bidIncrement,  setBidIncrement]  = useState(0) // Fetch from details API later
  const [depositAmount, setDepositAmount] = useState(0) // Fetch from details API later
  const [durationDays,  setDurationDays]  = useState(7)
  const [images,        setImages]        = useState<File[]>([])
  const [submitting,    setSubmitting]    = useState(false)
  const [categories,    setCategories]    = useState<AuctionCategoryResponse[]>([])
  const { accessToken } = useAuthStore()

  useEffect(() => {
    if (!accessToken) return;
    auctionService.getCategories()
      .then(res => setCategories(res.data))
      .catch(err => console.error("Failed to load categories:", err))
  }, [accessToken])

  const hasTitle       = title.trim().length > 0
  const hasDescription = description.trim().length > 0
  const hasCategory    = categoryId.trim().length > 0
  const hasPricing     = startingPrice > 0 && bidIncrement > 0 && depositAmount > 0
  // Require images if the auction has no primaryImageUrl, otherwise they're optional (means keeping existing if not replaced, though the current API might just replace all)
  const hasImages      = images.length > 0 || !!auction.primaryImageUrl
  const readyToPublish = hasTitle && hasDescription && hasCategory && hasPricing && hasImages

  const submitForm = async (publish: boolean) => {
    if (!accessToken) {
      alert("You must be logged in to update an auction.");
      return;
    }
    setSubmitting(true)
    try {
      const uploadedUrls: string[] = []
      // Upload images
      for (const file of images) {
        try {
          const presigned = await mediaService.getPresignedUrl(accessToken, file.name, file.type)
          await mediaService.uploadToS3(presigned.uploadUrl, file)
          uploadedUrls.push(presigned.s3Key)
        } catch (e) {
          console.error("Failed to upload image:", file.name, e)
          throw new Error("Image upload failed.")
        }
      }

      // If no new images uploaded, we should ideally keep the old ones,
      // but without the full array of existing images, we'll just pass what we have
      // or empty array if none uploaded (which might clear them, depending on backend logic)

      // Calculate endTime from durationDays
      const now = new Date()
      const endsAt = new Date(now.getTime() + durationDays * 86_400_000)

      await auctionService.updateAuction(auction.id, {
        title,
        description,
        categoryId,
        startingPrice,
        bidIncrement,
        depositAmount,
        startTime: now.toISOString(),
        endTime: endsAt.toISOString(),
        imageUrls: uploadedUrls
      }, accessToken)

      if (publish) {
        // If we need to publish, we could call another endpoint, but currently updating status is not in UpdateAuctionRequest
        // We'll call onPublish callback instead
      }
      
      onSave()
    } catch (error) {
      console.error('Failed to update auction:', error)
      alert("An error occurred while saving the auction.")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Form fields */}
      <div className="flex flex-col gap-5 max-w-2xl">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="title">Title</Label>
          <Input
            id="title"
            value={title}
            onChange={e => setTitle(e.target.value)}
            placeholder="Item title"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="desc">Description</Label>
          <Textarea
            id="desc"
            value={description}
            onChange={e => setDescription(e.target.value)}
            rows={4}
            placeholder="Describe the item in detail…"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>Images</Label>
          <p className="text-xs text-muted-foreground mb-1">Upload new images to replace existing ones.</p>
          <ImageUploadGrid images={images} onChange={setImages} />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="category">Category</Label>
          <Select value={categoryId} onValueChange={(v) => { if (v !== null) setCategoryId(v) }}>
            <SelectTrigger id="category">
              <SelectValue placeholder="Select category">
                {categories.find(c => c.id === categoryId)?.name}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {categories.map(c => (
                <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="starting">Starting price</Label>
            <CurrencyInput
              id="starting"
              valueCents={startingPrice}
              onChangeCents={setStartingPrice}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="increment">Bid increment</Label>
            <CurrencyInput
              id="increment"
              valueCents={bidIncrement}
              onChangeCents={setBidIncrement}
            />
          </div>
        </div>

        <DepositRangeInput
          depositCents={depositAmount}
          startingPriceCents={startingPrice}
          onChange={setDepositAmount}
        />

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

        <AntiSnipeNotice />
      </div>

      {/* Publish readiness checklist */}
      <div className="rounded-xl border border-[var(--color-border-default)] p-4 flex flex-col gap-3 max-w-2xl">
        <p className="text-sm font-medium">Publish checklist</p>
        <ChecklistItem done={hasTitle}       label="Title set" />
        <ChecklistItem done={hasDescription} label="Description written" />
        <ChecklistItem done={hasCategory}    label="Category selected" />
        <ChecklistItem done={!!auction.primaryImageUrl} label="At least one image uploaded" />
        <ChecklistItem done={hasPricing}     label="Pricing complete (starting, increment, deposit)" />
      </div>

      {/* Action bar */}
      <div className="flex items-center gap-3 max-w-2xl">
        <Button variant="outline" size="lg" disabled={submitting} onClick={() => submitForm(false)}>
          {submitting ? 'Saving...' : 'Save draft'}
        </Button>
        <Button variant="brand" size="lg" disabled={!readyToPublish || submitting} onClick={() => submitForm(true)}>
          Publish auction
        </Button>
        {!readyToPublish && (
          <p className="text-xs text-muted-foreground">Complete the checklist above to publish.</p>
        )}
      </div>
    </div>
  )
}

// ── Page ────────────────────────────────────────────────────────
export default function ManageAuctionPage() {
  const { id } = useParams<{ id: string }>()
  const auction = MOCK_AUCTIONS[id] ?? MOCK_AUCTIONS['au-29481']

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleted,    setDeleted]    = useState(false)

  const editable  = canEdit(auction)
  const deletable = canDelete(auction)
  const isLive    = auction.status !== SellerAuctionStatus.Draft
  const bids      = isLive ? MOCK_BIDS : []
  const events    = isLive ? MOCK_EVENTS : DRAFT_EVENTS

  if (deleted) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-32 text-center">
        <p className="font-medium text-sm">Auction deleted.</p>
        <Button variant="outline" size="sm" render={<Link href="/seller/auctions" />} nativeButton={false}>
          ← Back to my auctions
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-0 rounded-xl border border-[var(--color-border-default)] bg-background overflow-hidden">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 border-b border-[var(--color-border-default)] px-6 py-5">
        <div className="flex flex-col gap-0.5">
          <p className="text-xs text-muted-foreground">
            <Link href="/seller/auctions" className="hover:text-foreground transition-colors duration-[var(--duration-tesla)]">
              ← My auctions
            </Link>
          </p>
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)] line-clamp-1">
            {auction.title}
          </h1>
          <div className="flex items-center gap-2 mt-1">
            <SellerStatusBadge status={auction.status} />
            <span className="text-xs text-muted-foreground font-mono">{auction.id}</span>
          </div>
        </div>

        {deletable && (
          <Button
            variant="ghost"
            size="sm"
            className="shrink-0 text-[var(--color-danger-text)] hover:bg-[var(--color-danger-subtle)]"
            onClick={() => setDeleteOpen(true)}
          >
            <Trash2 className="mr-1.5 size-3.5" />
            Delete
          </Button>
        )}
      </div>

      <div className="px-6 py-6 flex flex-col gap-8">
        {/* ── Live mode: monitor panel ── */}
        {isLive && (
          <section className="flex flex-col gap-3">
            <h2 className="font-medium text-sm">Live monitor</h2>
            <AuctionMonitorPanel auction={auction} bids={bids} />
          </section>
        )}

        {/* ── Draft mode: edit form (with optional lock overlay) ── */}
        {!isLive && (
          <section className="relative flex flex-col gap-3">
            <h2 className="font-medium text-sm">Edit auction</h2>
            {!editable && (
              <EditLockOverlay reason="This auction is live and has active bids — editing is locked." />
            )}
            <DraftEditForm
              auction={auction}
              onSave={() => {/* Refresh data here in the future */}}
              onPublish={() => {/* Refresh data here in the future */}}
            />
          </section>
        )}

        {/* ── Live mode: locked edit notice ── */}
        {isLive && !editable && (
          <section className="relative flex flex-col gap-3">
            <h2 className="font-medium text-sm">Auction details</h2>
            <div className="rounded-xl border border-[var(--color-border-default)] p-4 flex flex-col gap-3">
              <div className="grid grid-cols-[140px_1fr] gap-y-2 text-sm">
                <span className="text-muted-foreground">Starting price</span>
                <span className="font-mono">{formatCurrency(auction.startingPrice)}</span>
                <span className="text-muted-foreground">Category</span>
                <span>{auction.categoryName}</span>
                <span className="text-muted-foreground">Ends at</span>
                <span className="font-mono">
                  {auction.endsAt.toLocaleString('en-US', {
                    month: 'short', day: 'numeric', year: 'numeric',
                    hour: '2-digit', minute: '2-digit',
                  })}
                </span>
              </div>
            </div>
          </section>
        )}

        {/* ── Audit trail ── */}
        <section className="flex flex-col gap-3">
          <h2 className="font-medium text-sm">Activity log</h2>
          <div className="rounded-xl border border-[var(--color-border-default)] p-4">
            <AuditTrail events={events} />
          </div>
        </section>
      </div>

      <DeleteAuctionDialog
        open={deleteOpen}
        auctionTitle={auction.title}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => { setDeleteOpen(false); setDeleted(true) }}
      />
    </div>
  )
}
