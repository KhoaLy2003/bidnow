'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams }     from 'next/navigation'
import Link              from 'next/link'
import { CheckCircle2, Circle, Trash2, Eye, Pencil, Loader2 } from 'lucide-react'
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
import { AuctionStatus }        from '@/lib/design-tokens'
import { SellerAuctionStatus }  from '@/types/ui/seller.ui'
import type { SellerAuction, AuditEvent } from '@/types/ui/seller.ui'
import type { AuctionDetail }   from '@/types/ui/auction.ui'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'
import { ImageUploadGrid }      from '@/components/seller/ImageUploadGrid'
import { CurrencyInput }        from '@/components/ui/currency-input'
import { auctionService }       from '@/services/auction.service'
import { mediaService }         from '@/services/media.service'

function mapToSellerAuction(detail: AuctionDetail): SellerAuction {
  let status: SellerAuctionStatus
  switch (detail.status) {
    case AuctionStatus.Scheduled:
      status = SellerAuctionStatus.Scheduled; break
    case AuctionStatus.Active:
    case AuctionStatus.EndingSoon:
    case AuctionStatus.Critical:
      status = SellerAuctionStatus.Active; break
    default:
      status = SellerAuctionStatus.Completed
  }
  return {
    id:              detail.id,
    title:           detail.title,
    primaryImageUrl: detail.images.find(img => img.isPrimary)?.imageUrl,
    categoryId:      detail.categoryId,
    categoryName:    detail.categoryName,
    sellerId:        detail.seller?.id ?? '',
    startingPrice:   detail.startingPrice,
    currentBid:      detail.currentBid,
    totalBids:       detail.totalBids,
    startsAt:        detail.startsAt,
    endsAt:          detail.endsAt,
    createdAt:       detail.createdAt,
    status,
  }
}

function canEdit(auction: SellerAuction): boolean {
  return (
    auction.status === SellerAuctionStatus.Draft ||
    auction.status === SellerAuctionStatus.Scheduled
  )
}

function canDelete(auction: SellerAuction): boolean {
  return (
    auction.status === SellerAuctionStatus.Draft ||
    auction.status === SellerAuctionStatus.Scheduled ||
    auction.status === SellerAuctionStatus.Active
  )
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
  const [description, setDescription] = useState('')
  const [categoryId,  setCategoryId]  = useState(auction.categoryId)
  const [startingPrice, setStartingPrice] = useState(auction.startingPrice)
  const [bidIncrement,  setBidIncrement]  = useState(0)
  const [depositAmount, setDepositAmount] = useState(0)
  const [durationDays,  setDurationDays]  = useState(7)
  const [images,        setImages]        = useState<File[]>([])
  const [submitting,    setSubmitting]    = useState(false)
  const [categories,    setCategories]    = useState<AuctionCategoryResponse[]>([])

  useEffect(() => {
    auctionService.getCategories()
      .then(res => setCategories(res.data))
      .catch(err => console.error('Failed to load categories:', err))
  }, [])

  const hasTitle       = title.trim().length > 0
  const hasDescription = description.trim().length > 0
  const hasCategory    = categoryId.trim().length > 0
  const hasPricing     = startingPrice > 0 && bidIncrement > 0 && depositAmount > 0
  const hasImages      = images.length > 0 || !!auction.primaryImageUrl
  const readyToPublish = hasTitle && hasDescription && hasCategory && hasPricing && hasImages

  const submitForm = async (publish: boolean) => {
    setSubmitting(true)
    try {
      const uploadedUrls: string[] = []
      for (const file of images) {
        try {
          const presigned = await mediaService.getPresignedUrl(file.name, file.type)
          await mediaService.uploadToS3(presigned.uploadUrl, file)
          uploadedUrls.push(presigned.s3Key)
        } catch (e) {
          console.error('Failed to upload image:', file.name, e)
          throw new Error('Image upload failed.')
        }
      }

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
        imageUrls: uploadedUrls,
      })

      if (publish) onPublish()
      else onSave()
    } catch (error) {
      console.error('Failed to update auction:', error)
      alert('An error occurred while saving the auction.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
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
              value={startingPrice}
              onChange={setStartingPrice}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="increment">Bid increment</Label>
            <CurrencyInput
              id="increment"
              value={bidIncrement}
              onChange={setBidIncrement}
            />
          </div>
        </div>

        <DepositRangeInput
          deposit={depositAmount}
          startingPrice={startingPrice}
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

      <div className="rounded-xl border border-[var(--color-border-default)] p-4 flex flex-col gap-3 max-w-2xl">
        <p className="text-sm font-medium">Publish checklist</p>
        <ChecklistItem done={hasTitle}       label="Title set" />
        <ChecklistItem done={hasDescription} label="Description written" />
        <ChecklistItem done={hasCategory}    label="Category selected" />
        <ChecklistItem done={!!auction.primaryImageUrl} label="At least one image uploaded" />
        <ChecklistItem done={hasPricing}     label="Pricing complete (starting, increment, deposit)" />
      </div>

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

  const [auction,    setAuction]    = useState<SellerAuction | null>(null)
  const [loading,    setLoading]    = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting,   setDeleting]   = useState(false)
  const [deleted,    setDeleted]    = useState(false)

  const loadAuction = useCallback(() => {
    setLoading(true)
    setFetchError(null)
    auctionService.getAuctionById(id)
      .then(detail => {
        if (!detail) { setFetchError('Auction not found.'); return }
        setAuction(mapToSellerAuction(detail))
      })
      .catch(() => setFetchError('Failed to load auction.'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => { loadAuction() }, [loadAuction])

  const handleDelete = async () => {
    if (!auction) return
    setDeleting(true)
    try {
      await auctionService.deleteAuction(auction.id)
      setDeleteOpen(false)
      setDeleted(true)
    } catch {
      alert('Failed to delete auction. Please try again.')
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-32 gap-2 text-muted-foreground">
        <Loader2 className="size-4 animate-spin" />
        <span className="text-sm">Loading auction…</span>
      </div>
    )
  }

  if (fetchError || !auction) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-32 text-center">
        <p className="text-sm text-muted-foreground">{fetchError ?? 'Auction not found.'}</p>
        <Button variant="outline" size="sm" render={<Link href="/seller/auctions" />} nativeButton={false}>
          ← Back to my auctions
        </Button>
      </div>
    )
  }

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

  const editable  = canEdit(auction)
  const deletable = canDelete(auction)
  const isLive    = auction.status !== SellerAuctionStatus.Draft

  const EMPTY_EVENTS: AuditEvent[] = []

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

        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="outline"
            size="sm"
            render={<Link href={`/auctions/${auction.id}`} />}
            nativeButton={false}
          >
            <Eye className="mr-1.5 size-3.5" />
            View
          </Button>

          <Button
            variant="outline"
            size="sm"
            disabled={!editable}
            onClick={() => {
              document.getElementById('edit-section')?.scrollIntoView({ behavior: 'smooth' })
            }}
          >
            <Pencil className="mr-1.5 size-3.5" />
            Edit
          </Button>

          <Button
            variant="ghost"
            size="sm"
            disabled={!deletable || deleting}
            className="text-[var(--color-danger-text)] hover:bg-[var(--color-danger-subtle)] disabled:opacity-50 disabled:pointer-events-none"
            onClick={() => setDeleteOpen(true)}
          >
            <Trash2 className="mr-1.5 size-3.5" />
            Delete
          </Button>
        </div>
      </div>

      <div className="px-6 py-6 flex flex-col gap-8">
        {/* ── Live mode: monitor panel ── */}
        {isLive && (
          <section className="flex flex-col gap-3">
            <h2 className="font-medium text-sm">Live monitor</h2>
            <AuctionMonitorPanel auction={auction} bids={[]} />
          </section>
        )}

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
              onPublish={loadAuction}
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
            <AuditTrail events={EMPTY_EVENTS} />
          </div>
        </section>
      </div>

      <DeleteAuctionDialog
        open={deleteOpen}
        auctionTitle={auction.title}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDelete}
      />
    </div>
  )
}
