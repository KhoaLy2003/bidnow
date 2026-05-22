'use client'

import { useState, useMemo } from 'react'
import { useRouter }       from 'next/navigation'
import Link                from 'next/link'
import { Loader2 }         from 'lucide-react'
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
import { INITIAL_FORM_DATA }   from '@/types/seller'
import type { CreateAuctionFormData } from '@/types/seller'
import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'

const STEPS = ['Basics', 'Images', 'Pricing', 'Review']

const CATEGORIES = [
  { id: 'watches',     label: 'Watches & Jewelry' },
  { id: 'electronics', label: 'Electronics' },
  { id: 'art',         label: 'Art & Collectibles' },
  { id: 'furniture',   label: 'Home & Furniture' },
  { id: 'fashion',     label: 'Fashion' },
  { id: 'vehicles',    label: 'Vehicles' },
  { id: 'music',       label: 'Music & Instruments' },
]

const CONDITIONS = ['Excellent — like new', 'Excellent — used', 'Good — used', 'Fair — used', 'For parts / not working']

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
    const max = Math.floor(data.startingPrice * 0.20)
    if (data.depositAmount < min || data.depositAmount > max)
      e.depositAmount = `Deposit must be ${formatCurrency(min)}–${formatCurrency(max)} (5–20% of starting price).`
  }
  if (data.durationDays < 1 || data.durationDays > 30)
    e.duration = 'Duration must be between 1 hour and 30 days.'
  return e
}

// ── Main component ──────────────────────────────────────────────
export default function CreateAuctionPage() {
  const router = useRouter()
  const [step,      setStep]      = useState(1)
  const [data,      setData]      = useState<CreateAuctionFormData>(INITIAL_FORM_DATA)
  const [errors,    setErrors]    = useState<Errors>({})
  const [submitting, setSubmitting] = useState(false)
  const [tag,       setTag]       = useState('')

  function update<K extends keyof CreateAuctionFormData>(key: K, val: CreateAuctionFormData[K]) {
    setData(prev => ({ ...prev, [key]: val }))
    setErrors(prev => { const n = { ...prev }; delete n[key as string]; return n })
  }

  function parseDollars(raw: string): number {
    const v = parseFloat(raw.replace(/[^0-9.]/g, ''))
    return isNaN(v) ? 0 : Math.round(v * 100)
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

  async function handleSubmit(_asDraft: boolean) {
    setSubmitting(true)
    // TODO: POST /api/v1/auctions
    await new Promise(r => setTimeout(r, 1200))
    setSubmitting(false)
    router.push('/auctions')
  }

  function addTag(e: React.KeyboardEvent) {
    if (e.key !== 'Enter' || !tag.trim()) return
    e.preventDefault()
    if (data.tags.length < 8 && !data.tags.includes(tag.trim()))
      update('tags', [...data.tags, tag.trim()])
    setTag('')
  }

  const [mountTime] = useState<number>(() => Date.now())
  const endsAt = useMemo(
    () => new Date(mountTime + data.durationDays * 86_400_000),
    [mountTime, data.durationDays],
  )

  return (
    <div className="flex flex-col gap-0 rounded-xl border border-[var(--color-border-default)] bg-background overflow-hidden">
      {/* Page title bar */}
      <div className="flex items-center justify-between gap-4 border-b border-[var(--color-border-default)] px-6 py-5">
        <div className="flex flex-col gap-0.5">
          <p className="text-xs text-muted-foreground">
            <Link href="/seller/auctions" className="hover:text-foreground transition-colors duration-[var(--duration-tesla)]">← My auctions</Link>
          </p>
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Create a new auction</h1>
        </div>
        <Button variant="ghost" size="sm" onClick={() => handleSubmit(true)} disabled={submitting}>
          Save as draft
        </Button>
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
                    <SelectValue placeholder="Select category" />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map(c => <SelectItem key={c.id} value={c.id}>{c.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </FieldWrap>

              <FieldWrap label="Condition">
                <Select value={data.condition} onValueChange={(v) => { if (v !== null) update('condition', v) }}>
                  <SelectTrigger className="text-sm">
                    <SelectValue placeholder="Select condition" />
                  </SelectTrigger>
                  <SelectContent>
                    {CONDITIONS.map(c => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                  </SelectContent>
                </Select>
              </FieldWrap>
            </div>

            <FieldWrap label="Tags" helper="Up to 8 tags. Press Enter to add.">
              <div className={cn(
                'flex flex-wrap items-center gap-1.5 min-h-9 rounded-md border border-input bg-background px-3 py-2 text-sm',
              )}>
                {data.tags.map(t => (
                  <span key={t} className="flex items-center gap-1 rounded-full bg-secondary px-2 py-0.5 text-xs font-medium">
                    {t}
                    <button type="button" onClick={() => update('tags', data.tags.filter(x => x !== t))} className="hover:text-destructive">×</button>
                  </span>
                ))}
                {data.tags.length < 8 && (
                  <input
                    className="flex-1 min-w-20 bg-transparent outline-none text-sm placeholder:text-muted-foreground"
                    placeholder="+ add tag"
                    value={tag}
                    onChange={e => setTag(e.target.value)}
                    onKeyDown={addTag}
                  />
                )}
              </div>
            </FieldWrap>
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
                <Input
                  value={data.startingPrice > 0 ? `$${(data.startingPrice / 100).toFixed(2)}` : ''}
                  onChange={e => update('startingPrice', parseDollars(e.target.value))}
                  placeholder="$ 0.00"
                  className={cn('font-mono text-sm', errors.startingPrice && 'border-destructive')}
                />
              </FieldWrap>

              <FieldWrap label="Bid increment" required
                helper="Minimum $0.01"
                error={errors.bidIncrement}
              >
                <Input
                  value={data.bidIncrement > 0 ? `$${(data.bidIncrement / 100).toFixed(2)}` : ''}
                  onChange={e => update('bidIncrement', parseDollars(e.target.value))}
                  placeholder="$ 0.00"
                  className={cn('font-mono text-sm', errors.bidIncrement && 'border-destructive')}
                />
              </FieldWrap>

              <FieldWrap label="Buy it now (optional)"
                helper="Must be greater than starting price"
                error={errors.buyNowPrice}
              >
                <Input
                  value={data.buyNowPrice > 0 ? `$${(data.buyNowPrice / 100).toFixed(2)}` : ''}
                  onChange={e => update('buyNowPrice', parseDollars(e.target.value))}
                  placeholder="$ 0.00"
                  className={cn('font-mono text-sm', errors.buyNowPrice && 'border-destructive')}
                />
              </FieldWrap>

              <FieldWrap label="Reserve price (optional)" helper="Hidden from bidders">
                <Input
                  value={data.bidIncrement > 0 ? '' : ''}
                  placeholder="$ 0.00"
                  className="font-mono text-sm"
                  readOnly
                />
              </FieldWrap>
            </div>

            {errors.depositAmount && <p className="text-sm text-destructive">{errors.depositAmount}</p>}
            <DepositRangeInput
              depositCents={data.depositAmount}
              startingPriceCents={data.startingPrice}
              onChange={cents => update('depositAmount', cents)}
            />

            <FieldWrap label="Duration" required
              helper={`Auction will end on ${endsAt.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })} at ${endsAt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })} ICT (GMT+7)`}
              error={errors.duration}
            >
              <div className="flex gap-2">
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
                <Input
                  readOnly
                  value={endsAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  className="flex-1 text-sm bg-[var(--color-bg-elevated)] cursor-default"
                />
              </div>
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
                data={{ ...data, endsAt }}
                onEditStep={setStep}
              />
            </div>

            <div className="flex flex-col gap-4 lg:w-72">
              <p className="font-display font-medium text-[length:var(--font-size-md)]">Publish options</p>

              {/* Mini preview card */}
              <div className="rounded-xl border border-[var(--color-border-default)] overflow-hidden">
                <div className="aspect-[4/3] bg-[var(--color-bg-elevated)]"
                  style={{ background: 'repeating-linear-gradient(135deg, #ECEDF2 0 1px, transparent 1px 8px), linear-gradient(180deg, #F4F4F8 0%, #ECEDF2 100%)' }}
                />
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
          <Button variant="outline" size="lg" onClick={() => handleSubmit(true)} disabled={submitting}>
            Save & exit
          </Button>
          {step < 4 ? (
            <Button variant="brand" size="lg" onClick={tryAdvance}>
              Next → {STEPS[step]}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
