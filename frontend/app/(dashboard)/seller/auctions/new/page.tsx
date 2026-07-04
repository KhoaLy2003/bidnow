'use client'

import { useState, useMemo, useEffect } from 'react'
import { useRouter }       from 'next/navigation'
import Link                from 'next/link'
import { CalendarIcon, Loader2 } from 'lucide-react'
import { format }          from 'date-fns'
import { Calendar }        from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button }          from '@/components/ui/button'
import { Input }           from '@/components/ui/input'
import { Textarea }        from '@/components/ui/textarea'
import { Label }           from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { StepIndicator }       from '@/components/seller/StepIndicator'
import { ImageUploadGrid }     from '@/components/seller/ImageUploadGrid'
import { DepositRangeInput }   from '@/components/seller/DepositRangeInput'
import { AntiSnipeNotice }     from '@/components/seller/AntiSnipeNotice'
import { AuctionReviewSummary } from '@/components/seller/AuctionReviewSummary'
import { ImageThumbnail } from '@/components/shared/ImageThumbnail'
import { INITIAL_FORM_DATA }   from '@/types/ui/seller.ui'
import type { CreateAuctionFormData, ManagedImage } from '@/types/ui/seller.ui'
import { formatCurrency } from '@/lib/format'
import { cn, getErrorMessage } from '@/lib/utils'
import { toast } from 'sonner'
import { auctionService } from '@/services/auction.service'
import { mediaService } from '@/services/media.service'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'
import { CurrencyInput } from '@/components/ui/currency-input'

const STEPS = ['Basics', 'Images', 'Pricing', 'Review']
const DURATION_OPTIONS = [
  { label: '1 day',   days: 1 },
  { label: '3 days',  days: 3 },
  { label: '5 days',  days: 5 },
  { label: '7 days',  days: 7 },
  { label: '14 days', days: 14 },
  { label: '30 days', days: 30 },
]

interface FieldWrapProps {
  label:     string
  required?: boolean
  helper?:   string
  error?:    string
  children:  React.ReactNode
}

function FieldWrap({ label, required, helper, error, children }: FieldWrapProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label className="text-xs font-medium text-muted-foreground">
        {label}{required && <span className="ml-0.5 text-[var(--color-text-brand)]">*</span>}
      </Label>
      {children}
      {error  && <p className="text-xs text-destructive">{error}</p>}
      {!error && helper && <p className="text-xs text-muted-foreground">{helper}</p>}
    </div>
  )
}

// ── Step validation ─────────────────────────────────────────────
interface Errors { [k: string]: string }

function validateStep1(data: CreateAuctionFormData): Errors {
  const e: Errors = {}
  if (data.title.length < 5)   e.title = 'Title must be at least 5 characters.'
  if (data.title.length > 255) e.title = 'Title cannot exceed 255 characters.'
  if (data.description.length < 20) e.description = `Description needs at least 20 characters (currently ${data.description.length}).`
  if (!data.categoryId) e.categoryId = 'Please select a category.'
  return e
}

function validateStep2(data: CreateAuctionFormData): Errors {
  const e: Errors = {}
  if (data.images.length === 0) e.images = 'At least 1 image is required.'
  return e
}

function validateStep3(data: CreateAuctionFormData): Errors {
  const e: Errors = {}
  if (data.startingPrice <= 0) e.startingPrice = 'Starting price must be greater than zero.'
  if (data.bidIncrement < 1)   e.bidIncrement  = 'Bid increment must be at least $0.01.'
  if (data.buyNowPrice > 0 && data.buyNowPrice <= data.startingPrice)
    e.buyNowPrice = 'Buy-it-now must be greater than starting price.'
  if (data.startingPrice > 0) {
    const min = Math.ceil(data.startingPrice * 0.05)
    const max = Math.floor(data.startingPrice * 0.2)
    if (data.depositAmount < min || data.depositAmount > max)
      e.depositAmount = `Deposit must be ${formatCurrency(min)}–${formatCurrency(max)} (5–20% of starting price).`
  }
  if (data.durationDays < 1 || data.durationDays > 30)
    e.duration = 'Duration must be between 1 and 30 days.'
  if (data.startType === 'scheduled') {
    if (!data.scheduledStartTime) {
      e.startTime = 'Please select a start date and time.'
    } else if (data.scheduledStartTime <= new Date()) {
      e.startTime = 'Scheduled start time must be in the future.'
    }
  }
  return e
}

// ── Main component ──────────────────────────────────────────────
export default function CreateAuctionPage() {
  const router = useRouter()
  const [step,      setStep]      = useState(1)
  const [data,      setData]      = useState<CreateAuctionFormData>(INITIAL_FORM_DATA)
  const [errors,    setErrors]    = useState<Errors>({})
  const [submitting, setSubmitting] = useState(false)
  const [categories, setCategories] = useState<AuctionCategoryResponse[]>([])
  const [nowMs,      setNowMs]      = useState(() => Date.now())
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  useEffect(() => {
    if (data.images.length === 0) { setPreviewUrl(null); return }
    const img = data.images[0]
    setPreviewUrl(img.kind === 'existing' ? img.url : img.preview)
  }, [data.images])

  // Keep nowMs ticking so endsAt display stays accurate for "start now" auctions
  useEffect(() => {
    if (data.startType !== 'now') return
    const id = setInterval(() => setNowMs(Date.now()), 60_000)
    return () => clearInterval(id)
  }, [data.startType])

  // Fetch categories on mount
  useEffect(() => {
    auctionService.getCategories()
      .then(res => setCategories(res.data))
      .catch(err => console.error("Failed to load categories:", err))
  }, [])

  function update<K extends keyof CreateAuctionFormData>(key: K, val: CreateAuctionFormData[K]) {
    setData(prev => ({ ...prev, [key]: val }))
    setErrors(prev => { const n = { ...prev }; delete n[key as string]; return n })
  }

  function tryAdvance() {
    let errs: Errors = {}
    if (step === 1) errs = validateStep1(data)
    if (step === 2) errs = validateStep2(data)
    if (step === 3) errs = validateStep3(data)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    setErrors({})
    setStep(s => Math.min(4, s + 1))
  }

  async function handleSubmit(asDraft: boolean) {
    setSubmitting(true)
    try {
      const uploadedUrls = await Promise.all(
        data.images.map(async (img: ManagedImage) => {
          if (img.kind === 'existing') return img.url
          try {
            const presigned = await mediaService.getPresignedUrl(img.file.name, img.file.type)
            await mediaService.uploadToS3(presigned.uploadUrl, img.file)
            return presigned.publicUrl
          } catch (e) {
            console.error('Failed to upload image:', img.file.name, e)
            throw new Error(`Image upload failed for "${img.file.name}".`)
          }
        })
      )

      const actualStartTime = data.startType === 'scheduled' && data.scheduledStartTime ? data.scheduledStartTime : new Date();
      const endTime = data.startType === 'scheduled' && data.scheduledStartTime
        ? new Date(data.scheduledStartTime.getTime() + data.durationDays * 86_400_000)
        : new Date(Date.now() + data.durationDays * 86_400_000);
      let auctionStatus: 'DRAFT' | 'SCHEDULED' | 'ACTIVE'
      if (asDraft) {
        auctionStatus = 'DRAFT'
      } else if (data.startType === 'scheduled') {
        auctionStatus = 'SCHEDULED'
      } else {
        auctionStatus = 'ACTIVE'
      }
      // Create auction
      await auctionService.createAuction({
        title: data.title,
        description: data.description,
        categoryId: data.categoryId,
        startingPrice: data.startingPrice,
        bidIncrement: data.bidIncrement,
        buyNowPrice: data.buyNowPrice > 0 ? data.buyNowPrice : undefined,
        depositAmount: data.depositAmount,
        startTime: actualStartTime.toISOString(),
        endTime: endTime.toISOString(),
        imageUrls: uploadedUrls,
        status: auctionStatus
      })
      
      router.push('/seller/auctions')
    } catch (error) {
      console.error('Failed to create auction:', error)
      toast.error(getErrorMessage(error, 'Failed to create auction. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }



  const endsAt = useMemo(() => {
    const baseTime = data.startType === 'scheduled' && data.scheduledStartTime
      ? data.scheduledStartTime.getTime()
      : nowMs;
    return new Date(baseTime + data.durationDays * 86_400_000);
  }, [nowMs, data.durationDays, data.startType, data.scheduledStartTime])

  return (
    <div className="flex flex-col gap-0 rounded-xl border border-[var(--color-border-default)] bg-background overflow-hidden">
      {/* Page title bar */}
      <div className="flex items-center gap-4 border-b border-[var(--color-border-default)] px-6 py-5">
        <div className="flex flex-col gap-0.5">
          <p className="text-xs text-muted-foreground">
            <Link href="/seller/auctions" className="hover:text-foreground transition-colors duration-[var(--duration-tesla)]">← My auctions</Link>
          </p>
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Create a new auction</h1>
        </div>
      </div>

      {/* Stepper */}
      <div className="border-b border-[var(--color-border-default)] px-6 py-4">
        <StepIndicator steps={STEPS} current={step} />
      </div>

      {/* Step content */}
      <div className="px-6 py-6">

        {/* ── Step 1: Basics ── */}
        {step === 1 && (
          <div className="flex flex-col gap-5 max-w-2xl">
            <div className="flex flex-col gap-1">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Tell us what you&apos;re selling</p>
              <p className="text-sm text-muted-foreground">Be specific. Bidders search by exact titles and keywords.</p>
            </div>

            <FieldWrap label="Title" required
              helper={`${data.title.length} / 255 characters · 5 min, 255 max`}
              error={errors.title}
            >
              <Input
                value={data.title}
                onChange={e => update('title', e.target.value)}
                placeholder="e.g. 1968 Omega Speedmaster Pre-Moon (Cal. 321)"
                className={cn('text-sm', errors.title && 'border-destructive')}
              />
            </FieldWrap>

            <FieldWrap label="Description" required
              helper={`${data.description.length} characters · 20 min`}
              error={errors.description}
            >
              <Textarea
                value={data.description}
                onChange={e => update('description', e.target.value)}
                placeholder="Describe the item — condition, provenance, included accessories..."
                rows={5}
                className={cn('resize-none text-sm', errors.description && 'border-destructive')}
              />
            </FieldWrap>

            <div className="grid grid-cols-2 gap-4">
              <FieldWrap label="Category" required error={errors.categoryId}>
                <Select value={data.categoryId} onValueChange={(v) => { if (v !== null) update('categoryId', v) }}>
                  <SelectTrigger className={cn('text-sm', errors.categoryId && 'border-destructive')}>
                    <SelectValue placeholder="Select category">
                      {categories.find(c => c.id === data.categoryId)?.name}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map(c => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </FieldWrap>
            </div>


          </div>
        )}

        {/* ── Step 2: Images ── */}
        {step === 2 && (
          <div className="flex flex-col gap-5 max-w-2xl">
            <div className="flex flex-col gap-1">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Add photos</p>
              <p className="text-sm text-muted-foreground">Good photos are the #1 factor in getting bids. Use natural light, multiple angles.</p>
            </div>

            {errors.images && (
              <p className="text-sm text-destructive">{errors.images}</p>
            )}
            <ImageUploadGrid
              images={data.images}
              onChange={files => update('images', files)}
            />
          </div>
        )}

        {/* ── Step 3: Pricing & Duration ── */}
        {step === 3 && (
          <div className="flex flex-col gap-5 max-w-2xl">
            <div className="flex flex-col gap-1">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Pricing & duration</p>
              <p className="text-sm text-muted-foreground">Set a competitive starting price. Lower starting prices attract more bidders.</p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <FieldWrap label="Starting price" required
                helper="USD, 2 decimal places"
                error={errors.startingPrice}
              >
                <CurrencyInput
                  value={data.startingPrice}
                  onChange={v => update('startingPrice', v)}
                  placeholder="0.00"
                  hasError={!!errors.startingPrice}
                />
              </FieldWrap>

              <FieldWrap label="Bid increment" required
                helper="Minimum $0.01"
                error={errors.bidIncrement}
              >
                <CurrencyInput
                  value={data.bidIncrement}
                  onChange={v => update('bidIncrement', v)}
                  placeholder="0.00"
                  hasError={!!errors.bidIncrement}
                />
              </FieldWrap>

              <FieldWrap label="Buy it now (optional)"
                helper="Must be greater than starting price"
                error={errors.buyNowPrice}
              >
                <CurrencyInput
                  value={data.buyNowPrice}
                  onChange={v => update('buyNowPrice', v)}
                  placeholder="0.00"
                  hasError={!!errors.buyNowPrice}
                />
              </FieldWrap>

            </div>

            {errors.depositAmount && <p className="text-sm text-destructive">{errors.depositAmount}</p>}
            <DepositRangeInput
              deposit={data.depositAmount}
              startingPrice={data.startingPrice}
              onChange={v => update('depositAmount', v)}
            />

            <FieldWrap label="Start time" required error={errors.startTime}>
              <Tabs
                value={data.startType}
                onValueChange={(v) => update('startType', v as 'now' | 'scheduled')}
                className="w-full"
              >
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="now">Start immediately</TabsTrigger>
                  <TabsTrigger value="scheduled">Schedule for later</TabsTrigger>
                </TabsList>
              </Tabs>
              {data.startType === 'scheduled' && (
                <div className="mt-3">
                  <Popover>
                    <PopoverTrigger
                      render={
                        <Button
                          variant={"outline"}
                          className={cn(
                            "w-full justify-start text-left font-normal",
                            !data.scheduledStartTime && "text-muted-foreground",
                            errors.startTime && "border-destructive"
                          )}
                        />
                      }
                    >
                      <CalendarIcon className="mr-2 h-4 w-4" />
                      {data.scheduledStartTime ? format(data.scheduledStartTime, "PPP p") : <span>Pick a date and time</span>}
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0" align="start">
                      <Calendar
                        mode="single"
                        selected={data.scheduledStartTime || undefined}
                        onSelect={(d) => {
                          if (d) {
                            // Keep the existing time if editing date, otherwise set a default time (e.g., noon)
                            const newDate = new Date(d);
                            if (data.scheduledStartTime) {
                              newDate.setHours(data.scheduledStartTime.getHours());
                              newDate.setMinutes(data.scheduledStartTime.getMinutes());
                            } else {
                              newDate.setHours(12, 0, 0, 0);
                            }
                            update('scheduledStartTime', newDate);
                          } else {
                            update('scheduledStartTime', null);
                          }
                        }}
                        disabled={(date) => date < new Date(new Date().setHours(0,0,0,0))}
                      />
                      {data.scheduledStartTime && (
                        <div className="p-3 border-t border-[var(--color-border-default)]">
                          <Label className="text-xs mb-2 block text-muted-foreground">Time</Label>
                          <Input 
                            type="time" 
                            className="w-full text-sm"
                            value={format(data.scheduledStartTime, "HH:mm")}
                            onChange={(e) => {
                              const [hours, minutes] = e.target.value.split(':');
                              const newDate = new Date(data.scheduledStartTime!);
                              newDate.setHours(Number.parseInt(hours, 10), Number.parseInt(minutes, 10));
                              update('scheduledStartTime', newDate);
                            }}
                          />
                        </div>
                      )}
                    </PopoverContent>
                  </Popover>
                </div>
              )}
            </FieldWrap>

            <FieldWrap label="Duration" required
              helper={`Auction will end on ${endsAt.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })} at ${endsAt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })} ICT (GMT+7)`}
              error={errors.duration}
            >
              <Select
                value={String(data.durationDays)}
                onValueChange={v => update('durationDays', Number(v))}
              >
                <SelectTrigger className="w-32 text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DURATION_OPTIONS.map(o => (
                    <SelectItem key={o.days} value={String(o.days)}>{o.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FieldWrap>

            <AntiSnipeNotice />
          </div>
        )}

        {/* ── Step 4: Review ── */}
        {step === 4 && (
          <div className="flex flex-col gap-6 lg:flex-row lg:gap-8">
            <div className="flex flex-col gap-4 flex-1 min-w-0">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Listing summary</p>
              <AuctionReviewSummary
                data={data}
                endsAt={endsAt}
                onEditStep={setStep}
              />
            </div>

            <div className="flex flex-col gap-4 lg:w-72">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Publish options</p>

              {/* Mini preview card */}
              <div className="rounded-xl border border-[var(--color-border-default)] overflow-hidden">
                {previewUrl ? (
                  <div className="relative aspect-[4/3] w-full">
                    <ImageThumbnail
                      src={previewUrl}
                      alt="Auction preview"
                      fill
                      unoptimized
                      className="object-cover"
                    />
                  </div>
                ) : (
                  <div className="aspect-[4/3]"
                    style={{ background: 'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)' }}
                  />
                )}
                <div className="flex flex-col gap-1.5 p-4">
                  <p className="font-medium text-sm line-clamp-2">{data.title || 'Untitled auction'}</p>
                  <p className="font-mono font-medium text-[length:var(--font-size-md)]">
                    {data.startingPrice > 0 ? formatCurrency(data.startingPrice) : '$0.00'}
                  </p>
                  <p className="text-xs text-muted-foreground">{data.durationDays}d · ends {endsAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</p>
                </div>
              </div>

              {/* Agreements */}
              <div className="flex flex-col gap-2 rounded-md bg-[var(--color-bg-elevated)] p-3">
                {[
                  'I have the legal right to sell this item.',
                  "I agree to BidNow’s seller terms and 2.5% fee on completed sales.",
                ].map((text, i) => (
                  <label key={i} className="flex items-start gap-2 cursor-pointer">
                    <input type="checkbox" defaultChecked className="mt-0.5 accent-primary" />
                    <span className="text-xs text-muted-foreground">{text}</span>
                  </label>
                ))}
              </div>

              <Button
                variant="brand"
                size="lg"
                className="w-full"
                onClick={() => handleSubmit(false)}
                disabled={submitting}
              >
                {submitting ? <Loader2 className="size-4 animate-spin" /> : 'Publish auction now'}
              </Button>
              <Button
                variant="outline"
                className="w-full"
                onClick={() => handleSubmit(true)}
                disabled={submitting}
              >
                Save as DRAFT
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                You can edit a draft any time. Once published, only timing & description can change before the first bid.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Footer nav */}
      <div className="flex items-center justify-between border-t border-[var(--color-border-default)] px-6 py-4">
        <Button
          variant="ghost"
          onClick={() => setStep(s => Math.max(1, s - 1))}
          disabled={step === 1}
        >
          ← Back
        </Button>
        <div className="flex gap-2">
          {step < 4 && (
            <Button variant="brand" size="lg" onClick={tryAdvance}>
              Next → {STEPS[step]}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
