'use client'

import { useState, useCallback } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { BidInput } from './BidInput'
import { BidButton } from './BidButton'
import { Label } from '@/components/ui/label'
import { formatCurrency } from '@/lib/format'
import { addDollars, isLessThan, parseDollarInput } from '@/lib/money'
import { minimumNextBid } from '@/lib/auction-utils'
import { quickBidAmounts } from '@/lib/auction-display'
import { classifyBidError, describeBidFailure, type BidFailure } from '@/lib/bid-errors'
import { bidService } from '@/services/bid.service'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { useWallet } from '@/hooks/useWallet'
import { cn } from '@/lib/utils'

interface BidFormProps {
  auctionId:     string
  currentBid:    number   // dollars
  bidIncrement:  number   // dollars
  startingPrice: number   // dollars
  totalBids:     number
  className?:    string
}

export function BidForm({ auctionId, currentBid, bidIncrement, startingPrice, totalBids, className }: BidFormProps) {
  const router = useRouter()
  const user = useAuthStore((s) => s.user)
  const { refetch: refetchWallet } = useWallet()

  const [bidValue, setBidValue]           = useState('')
  const [inputError, setInputError]       = useState<string>()
  const [failure, setFailure]             = useState<BidFailure | null>(null)
  const [serverMinimum, setServerMinimum] = useState<number | null>(null)
  const [isLoading, setIsLoading]         = useState(false)

  const computedMinimum = minimumNextBid({ currentBid, bidIncrement, startingPrice, totalBids })
  const minBid = serverMinimum !== null && isLessThan(computedMinimum, serverMinimum) ? serverMinimum : computedMinimum
  const parsedBid = parseDollarInput(bidValue)
  const failureView = failure ? describeBidFailure(failure) : null
  const formDisabled = failureView?.disablesForm ?? false

  const handleIncrement = useCallback(() => {
    // Empty or below-minimum input snaps to the minimum; otherwise step up by one increment.
    const next = parsedBid === null || isLessThan(parsedBid, minBid) ? minBid : addDollars(parsedBid, bidIncrement)
    setBidValue(next.toFixed(2))
    setInputError(undefined)
  }, [parsedBid, minBid, bidIncrement])

  const handleDecrement = useCallback(() => {
    const next = addDollars(parsedBid ?? minBid, -bidIncrement)
    setBidValue((isLessThan(next, minBid) ? minBid : next).toFixed(2))
    setInputError(undefined)
  }, [parsedBid, minBid, bidIncrement])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!user) {
      router.push('/login')
      return
    }
    if (parsedBid === null) {
      setInputError('Enter a valid amount (up to 2 decimals)')
      return
    }
    if (isLessThan(parsedBid, minBid)) {
      setInputError(`Minimum bid is ${formatCurrency(minBid)}`)
      return
    }

    setInputError(undefined)
    setFailure(null)
    setIsLoading(true)
    try {
      const placed = await bidService.placeBid({ auctionId, amount: parsedBid })
      useAuctionStore.getState().ownBidPlaced(placed, { id: user.id, name: 'You' })
      toast.success(`Bid placed: ${formatCurrency(placed.amount)}`)
      setBidValue('')
      setServerMinimum(null)
      void refetchWallet() // the first bid locks the deposit
    } catch (err) {
      const f = classifyBidError(err)
      if (f.kind === 'unauthenticated') {
        router.push('/login')
        return
      }
      if (f.kind === 'too-low' && f.minimumBid !== null) setServerMinimum(f.minimumBid)
      setFailure(f)
    } finally {
      setIsLoading(false)
    }
  }

  function pickQuickBid(amount: number) {
    setBidValue(amount.toFixed(2))
    setInputError(undefined)
    if (!failureView?.disablesForm) setFailure(null)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className={cn('flex flex-col gap-3', className)}>
      <Label htmlFor={`bid-${auctionId}`} className="font-semibold">Your bid</Label>
      <BidInput
        id={`bid-${auctionId}`}
        value={bidValue}
        onChange={(e) => {
          setBidValue(e.target.value)
          setInputError(undefined)
          if (!failureView?.disablesForm) setFailure(null)
        }}
        placeholder={minBid.toFixed(2)}
        min={minBid}
        step={bidIncrement}
        error={inputError}
        onIncrement={handleIncrement}
        onDecrement={handleDecrement}
        disabled={formDisabled || isLoading}
        showSteppers
      />

      <div role="group" aria-label="Quick bid amounts" className="flex flex-wrap gap-2">
        {quickBidAmounts(minBid, bidIncrement).map((amount) => (
          <button
            key={amount}
            type="button"
            onClick={() => pickQuickBid(amount)}
            disabled={formDisabled || isLoading}
            className="min-h-9 rounded-full border bg-background px-3.5 font-mono text-[13px] font-medium text-foreground/80 transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:border-[var(--color-text-brand)] hover:text-[var(--color-text-brand)] disabled:pointer-events-none disabled:opacity-50"
          >
            {formatCurrency(amount)}
          </button>
        ))}
      </div>

      <BidButton
        type="submit"
        amount={parsedBid ?? undefined}
        isLoading={isLoading}
        disabled={!bidValue || formDisabled}
      />

      {failureView && (
        <div role="alert" className="flex flex-col gap-1 text-xs text-[var(--color-danger-text)]">
          <p>{failureView.message}</p>
          {failureView.action && (
            <Link href={failureView.action.href} className="font-medium underline underline-offset-2">
              {failureView.action.label}
            </Link>
          )}
        </div>
      )}

      <p className="text-xs text-muted-foreground text-center">
        Minimum next bid: <span className="font-mono">{formatCurrency(minBid)}</span>
      </p>
    </form>
  )
}
