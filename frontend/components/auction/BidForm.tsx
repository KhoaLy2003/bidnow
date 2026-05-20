'use client'

import { useState, useCallback } from 'react'
import { Zap } from 'lucide-react'
import { BidInput } from './BidInput'
import { BidButton } from './BidButton'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BidFormProps {
  auctionId:    string
  currentBid:   number        // cents
  minIncrement?: number       // cents, default $1.00
  onBidPlaced?: (amount: number) => void
  className?:   string
}

const DEFAULT_INCREMENT = 1_00  // $1.00

export function BidForm({
  auctionId,
  currentBid,
  minIncrement = DEFAULT_INCREMENT,
  onBidPlaced,
  className,
}: BidFormProps) {
  const minBid = currentBid + minIncrement

  const [bidValue, setBidValue]       = useState<string>('')
  const [autoBid, setAutoBid]         = useState(false)
  const [maxBidValue, setMaxBidValue] = useState<string>('')
  const [isLoading, setIsLoading]     = useState(false)
  const [error, setError]             = useState<string>()

  const parsedBid    = bidValue    ? Math.round(parseFloat(bidValue) * 100) : 0
  const parsedMaxBid = maxBidValue ? Math.round(parseFloat(maxBidValue) * 100) : 0

  function validate(): string | undefined {
    if (!bidValue) return 'Enter a bid amount'
    if (parsedBid < minBid)
      return `Minimum bid is ${formatCurrency(minBid)}`
    if (autoBid && parsedMaxBid < parsedBid)
      return `Max auto-bid must be ≥ ${formatCurrency(parsedBid)}`
    return undefined
  }

  const handleIncrement = useCallback(() => {
    const current = bidValue ? parseFloat(bidValue) : currentBid / 100
    setBidValue(String((current + minIncrement / 100).toFixed(2)))
  }, [bidValue, currentBid, minIncrement])

  const handleDecrement = useCallback(() => {
    const current = bidValue ? parseFloat(bidValue) : minBid / 100
    const next = Math.max(minBid / 100, current - minIncrement / 100)
    setBidValue(String(next.toFixed(2)))
  }, [bidValue, minBid, minIncrement])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const err = validate()
    if (err) { setError(err); return }
    setError(undefined)
    setIsLoading(true)
    try {
      // Placeholder — real submit goes here
      await new Promise<void>((r) => setTimeout(r, 600))
      onBidPlaced?.(parsedBid)
      setBidValue('')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className={cn('flex flex-col gap-3', className)}>
      <BidInput
        value={bidValue}
        onChange={(e) => { setBidValue(e.target.value); setError(undefined) }}
        placeholder={`${(minBid / 100).toFixed(2)}`}
        min={minBid / 100}
        step={minIncrement / 100}
        error={error}
        onIncrement={handleIncrement}
        onDecrement={handleDecrement}
        showSteppers
      />

      <BidButton
        amount={parsedBid || undefined}
        isLoading={isLoading}
        disabled={!bidValue}
      />

      <Separator />

      {/* Auto-bid panel */}
      <div className="flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <Label htmlFor={`autobid-${auctionId}`} className="flex items-center gap-1.5 cursor-pointer">
            <Zap className="size-3.5 text-[var(--color-text-brand)]" />
            Auto-bid
          </Label>
          <Switch
            id={`autobid-${auctionId}`}
            checked={autoBid}
            onCheckedChange={(checked: boolean) => setAutoBid(checked)}
          />
        </div>

        {/* Collapsible max-bid input */}
        <div
          className={cn(
            'overflow-hidden transition-[max-height,opacity] duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]',
            autoBid ? 'max-h-24 opacity-100' : 'max-h-0 opacity-0',
          )}
        >
          <div className="pt-1">
            <Label className="mb-1 block text-xs text-muted-foreground">
              Max auto-bid
            </Label>
            <BidInput
              value={maxBidValue}
              onChange={(e) => setMaxBidValue(e.target.value)}
              placeholder={`${(parsedBid / 100 || minBid / 100 + 5).toFixed(2)}`}
              min={parsedBid / 100}
              step={minIncrement / 100}
            />
          </div>
        </div>
      </div>

      <p className="text-xs text-muted-foreground text-center">
        Minimum next bid: {formatCurrency(minBid)}
      </p>
    </form>
  )
}
