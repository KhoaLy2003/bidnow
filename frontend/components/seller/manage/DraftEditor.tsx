'use client'

import { useEffect, useMemo, useState } from 'react'
import { CheckCircle2, Circle, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { CurrencyInput } from '@/components/ui/currency-input'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { AntiSnipeNotice } from '@/components/seller/AntiSnipeNotice'
import { DateTimePicker } from '@/components/seller/DateTimePicker'
import { DepositRangeInput } from '@/components/seller/DepositRangeInput'
import { ImageUploadGrid } from '@/components/seller/ImageUploadGrid'
import { AuctionStatus } from '@/lib/design-tokens'
import { formatCurrency } from '@/lib/format'
import { draftChecklist, validateDraft } from '@/lib/seller-manage'
import { cn, getErrorMessage } from '@/lib/utils'
import { auctionService } from '@/services/auction.service'
import { mediaService } from '@/services/media.service'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'
import type { ManagedImage } from '@/types/ui/seller.ui'
import type { AuctionDetail } from '@/types/ui/auction.ui'

const DURATION_FORMAT = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 })

function describeDuration(ms: number): string {
  if (ms <= 0) return '—'
  const hours = ms / 3_600_000
  return hours < 24 ? `${DURATION_FORMAT.format(hours)} hours` : `${DURATION_FORMAT.format(hours / 24)} days`
}

function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null
  return <p id={id} role="alert" className="text-xs text-[var(--color-danger-text)]">{message}</p>
}

function Card({ title, aside, children }: { title: string; aside?: string; children: React.ReactNode }) {
  const id = `${title.toLowerCase().replace(/\W+/g, '-')}-heading`
  return (
    <section aria-labelledby={id} className="flex flex-col gap-5 rounded-xl border border-[var(--color-border-default)] p-6">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id={id} className="text-base font-medium">{title}</h2>
        {aside && <span className="text-xs text-muted-foreground">{aside}</span>}
      </div>
      {children}
    </section>
  )
}

interface DraftEditorProps {
  auction:      AuctionDetail
  onSaved():    void
  onPublished(): void
}

export function DraftEditor({ auction, onSaved, onPublished }: DraftEditorProps) {
  const isDraft = auction.status === AuctionStatus.Draft

  const [title, setTitle] = useState(auction.title)
  const [description, setDescription] = useState(auction.description)
  const [categoryId, setCategoryId] = useState(auction.categoryId)
  const [startingPrice, setStartingPrice] = useState(auction.startingPrice)
  const [bidIncrement, setBidIncrement] = useState(auction.bidIncrement)
  const [buyNowPrice, setBuyNowPrice] = useState(auction.buyNowPrice ?? 0)
  const [depositAmount, setDepositAmount] = useState(auction.depositAmount)
  const [startTime, setStartTime] = useState<Date>(auction.startsAt)
  const [endTime, setEndTime] = useState<Date>(auction.endsAt)
  const [images, setImages] = useState<ManagedImage[]>(() =>
    [...auction.images]
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .map((img) => ({ kind: 'existing' as const, id: img.id, url: img.imageUrl })),
  )
  const [categories, setCategories] = useState<AuctionCategoryResponse[]>([])
  const [submitting, setSubmitting] = useState<'save' | 'publish' | null>(null)

  useEffect(() => {
    auctionService.getCategories()
      .then((res) => setCategories(res.data))
      .catch((err) => console.error('Failed to load categories:', err))
  }, [])

  const errors = useMemo(
    () => validateDraft({
      title, description, categoryId, imageCount: images.length,
      startingPrice, bidIncrement, buyNowPrice, depositAmount, startTime, endTime,
    }),
    [title, description, categoryId, images.length, startingPrice, bidIncrement, buyNowPrice, depositAmount, startTime, endTime],
  )
  const checklist = draftChecklist(errors)
  const doneCount = checklist.filter((c) => c.done).length
  const ready = doneCount === checklist.length
  const busy = submitting !== null

  const coverSrc = images[0] ? (images[0].kind === 'existing' ? images[0].url : images[0].preview) : undefined

  async function submit(publish: boolean) {
    setSubmitting(publish ? 'publish' : 'save')
    try {
      const keptUrls = images
        .filter((img): img is Extract<ManagedImage, { kind: 'existing' }> => img.kind === 'existing')
        .map((img) => img.url)

      const newUrls: string[] = []
      for (const img of images.filter((i): i is Extract<ManagedImage, { kind: 'new' }> => i.kind === 'new')) {
        const presigned = await mediaService.getPresignedUrl(img.file.name, img.file.type)
        await mediaService.uploadToS3(presigned.uploadUrl, img.file)
        newUrls.push(presigned.publicUrl)
      }

      await auctionService.updateAuction(auction.id, {
        title: title.trim(),
        description: description.trim(),
        categoryId,
        startingPrice,
        bidIncrement,
        buyNowPrice: buyNowPrice > 0 ? buyNowPrice : undefined,
        depositAmount,
        startTime: startTime.toISOString(),
        endTime: endTime.toISOString(),
        imageUrls: [...keptUrls, ...newUrls],
      })

      if (publish) {
        await auctionService.publishAuction(auction.id)
        onPublished()
      } else {
        toast.success('Auction saved.')
        onSaved()
      }
    } catch (error) {
      toast.error(getErrorMessage(error, 'Failed to save auction. Please try again.'))
    } finally {
      setSubmitting(null)
    }
  }

  return (
    <div className="flex flex-wrap items-start gap-6">
      {/* ── Form ── */}
      <form
        onSubmit={(e) => e.preventDefault()}
        className="flex min-w-0 flex-[999_1_620px] flex-col gap-6"
        noValidate
      >
        <Card title="Basic info">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="draft-title">Title</Label>
            <Input
              id="draft-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              aria-invalid={Boolean(errors.title)}
              aria-describedby="draft-title-help"
              className="h-10"
            />
            {errors.title
              ? <FieldError id="draft-title-help" message={errors.title} />
              : (
                <p id="draft-title-help" className="flex justify-between text-xs text-muted-foreground">
                  <span>5–255 characters</span>
                  <span className="font-mono">{title.length}/255</span>
                </p>
              )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="draft-description">Description</Label>
            <Textarea
              id="draft-description"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              aria-invalid={Boolean(errors.description)}
              aria-describedby="draft-description-help"
            />
            {errors.description
              ? <FieldError id="draft-description-help" message={errors.description} />
              : <p id="draft-description-help" className="text-xs text-muted-foreground">Minimum 20 characters</p>}
          </div>

          <div className="flex max-w-xs flex-col gap-1.5">
            <Label htmlFor="draft-category">Category</Label>
            <Select value={categoryId} onValueChange={(v) => { if (v !== null) setCategoryId(v) }}>
              <SelectTrigger id="draft-category" className="h-10 w-full" aria-invalid={Boolean(errors.categoryId)}>
                <SelectValue placeholder="Select category">
                  {categories.find((c) => c.id === categoryId)?.name ?? auction.categoryName}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {categories.map((c) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <FieldError id="draft-category-error" message={errors.categoryId} />
          </div>
        </Card>

        <Card title="Photos" aside="1–10 images · JPEG or PNG · 5 MB each">
          {errors.images && (
            <p
              role="alert"
              className="rounded-md border border-[var(--color-danger-border)] bg-[var(--color-danger-subtle)] px-3 py-2.5 text-[13px] text-[var(--color-danger-text)]"
            >
              {errors.images}
            </p>
          )}
          <ImageUploadGrid images={images} onChange={setImages} />
        </Card>

        <Card title="Pricing">
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="draft-starting">Starting price</Label>
              <CurrencyInput
                id="draft-starting"
                value={startingPrice}
                onChange={setStartingPrice}
                hasError={Boolean(errors.startingPrice)}
                aria-describedby="draft-starting-help"
              />
              {errors.startingPrice
                ? <FieldError id="draft-starting-help" message={errors.startingPrice} />
                : <p id="draft-starting-help" className="text-xs text-muted-foreground">Up to 2 decimals</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="draft-increment">Bid increment</Label>
              <CurrencyInput
                id="draft-increment"
                value={bidIncrement}
                onChange={setBidIncrement}
                hasError={Boolean(errors.bidIncrement)}
                aria-describedby="draft-increment-help"
              />
              {errors.bidIncrement
                ? <FieldError id="draft-increment-help" message={errors.bidIncrement} />
                : <p id="draft-increment-help" className="text-xs text-muted-foreground">At least $0.01</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="draft-buynow">
                Buy It Now <span className="font-normal text-muted-foreground">(optional)</span>
              </Label>
              <CurrencyInput
                id="draft-buynow"
                value={buyNowPrice}
                onChange={setBuyNowPrice}
                hasError={Boolean(errors.buyNowPrice)}
                aria-describedby="draft-buynow-help"
              />
              {errors.buyNowPrice
                ? <FieldError id="draft-buynow-help" message={errors.buyNowPrice} />
                : <p id="draft-buynow-help" className="text-xs text-muted-foreground">Must be above the starting price</p>}
            </div>
          </div>

          <DepositRangeInput deposit={depositAmount} startingPrice={startingPrice} onChange={setDepositAmount} />
        </Card>

        <Card title="Schedule">
          <div className="grid gap-4 sm:grid-cols-2">
            <DateTimePicker id="draft-start" label="Start time" value={startTime} onChange={setStartTime} />
            <DateTimePicker id="draft-end" label="End time" value={endTime} onChange={setEndTime} />
          </div>
          {errors.schedule
            ? <FieldError id="draft-schedule-error" message={errors.schedule} />
            : (
              <p className="text-xs text-muted-foreground">
                Duration: <span className="font-mono text-foreground">{describeDuration(endTime.getTime() - startTime.getTime())}</span>
                {' '}· allowed 1 hour to 30 days
              </p>
            )}
          <AntiSnipeNotice />
        </Card>
      </form>

      {/* ── Checklist + preview ── */}
      <aside className="flex min-w-0 flex-[1_1_320px] flex-col gap-6 lg:sticky lg:top-4">
        <section aria-labelledby="publish-checklist-heading" className="flex flex-col gap-3.5 rounded-xl border border-[var(--color-border-default)] p-5">
          <div className="flex items-baseline justify-between">
            <h2 id="publish-checklist-heading" className="text-sm font-medium">
              {isDraft ? 'Publish checklist' : 'Before you save'}
            </h2>
            <span className="font-mono text-xs text-muted-foreground">{doneCount}/{checklist.length}</span>
          </div>

          <div className="h-1 overflow-hidden rounded-full bg-[var(--color-border-default)]" aria-hidden>
            <div
              className="h-1 bg-[var(--color-auction-won-accent)] transition-[width] duration-[var(--duration-tesla)]"
              style={{ width: `${(doneCount / checklist.length) * 100}%` }}
            />
          </div>

          <ul className="flex flex-col gap-2.5">
            {checklist.map((item) => (
              <li key={item.key} className={cn('flex items-center gap-2.5 text-[13px]', !item.done && 'text-muted-foreground')}>
                {item.done
                  ? <CheckCircle2 className="size-4 shrink-0 text-[var(--color-auction-won-accent)]" aria-hidden />
                  : <Circle className="size-4 shrink-0" aria-hidden />}
                {item.label}
                <span className="sr-only">{item.done ? '(done)' : '(not done)'}</span>
              </li>
            ))}
          </ul>

          <div className="flex flex-col gap-2 pt-1.5">
            {isDraft && (
              <Button variant="brand" size="xl" disabled={!ready || busy} onClick={() => submit(true)}>
                {submitting === 'publish' && <Loader2 className="size-4 animate-spin" aria-hidden />}
                Publish auction
              </Button>
            )}
            <Button variant="outline" size="xl" disabled={busy} onClick={() => submit(false)}>
              {submitting === 'save' && <Loader2 className="size-4 animate-spin" aria-hidden />}
              {isDraft ? 'Save draft' : 'Save changes'}
            </Button>
            <p className="text-xs leading-relaxed text-muted-foreground">
              {isDraft
                ? (ready ? 'Ready to publish.' : 'Complete the checklist to publish. You can save a draft at any time.')
                : 'Edits are allowed until the auction starts.'}
            </p>
          </div>
        </section>

        <section aria-labelledby="listing-preview-heading" className="overflow-hidden rounded-xl border border-[var(--color-border-default)]">
          <h2 id="listing-preview-heading" className="border-b border-[var(--color-border-default)] px-5 py-3.5 text-sm font-medium">
            How bidders will see it
          </h2>
          <div className="relative flex aspect-[4/3] items-center justify-center bg-[var(--color-bg-elevated)] text-xs text-muted-foreground">
            {coverSrc
              // eslint-disable-next-line @next/next/no-img-element -- blob: previews of unsaved uploads cannot go through next/image
              ? <img src={coverSrc} alt="" className="size-full object-cover" />
              : 'No photo yet'}
          </div>
          <div className="flex flex-col gap-1 px-5 py-3.5">
            <p className="text-sm font-medium break-words">{title.trim() || 'Untitled auction'}</p>
            <p className="text-xs text-muted-foreground">Starting bid</p>
            <p className="font-mono text-xl font-medium">{startingPrice > 0 ? formatCurrency(startingPrice) : '—'}</p>
          </div>
        </section>
      </aside>
    </div>
  )
}
