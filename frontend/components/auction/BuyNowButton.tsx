'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { classifyBidError, describeBidFailure, type BidFailure } from '@/lib/bid-errors'
import { formatCurrency } from '@/lib/format'
import { bidService } from '@/services/bid.service'
import { useAuctionStore } from '@/store/auctionStore'
import { useAuthStore } from '@/store/authStore'
import { useWallet } from '@/hooks/useWallet'

interface BuyNowButtonProps {
  auctionId:     string
  title:         string
  buyNowPrice:   number   // dollars
  depositAmount: number   // dollars
}

// describeBidFailure words some failures for bidding; this is a purchase dialog.
function buyNowFailureMessage(failure: BidFailure, fallback: string): string {
  switch (failure.kind) {
    case 'unknown':
      return 'Could not complete your purchase. Please try again.'
    case 'unavailable':
      return "We couldn't confirm your purchase. Please check the auction before trying again."
    case 'not-open':
      return 'This auction is no longer open.'
    case 'own-auction':
      return "You can't buy your own auction."
    default:
      return fallback
  }
}

export function BuyNowButton({ auctionId, title, buyNowPrice, depositAmount }: BuyNowButtonProps) {
  const router = useRouter()
  const user = useAuthStore((s) => s.user)
  const { refetch: refetchWallet } = useWallet()

  const [open, setOpen]           = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [failure, setFailure]     = useState<BidFailure | null>(null)
  const failureView = failure ? describeBidFailure(failure) : null
  const failureMessage = failure && failureView ? buyNowFailureMessage(failure, failureView.message) : null

  function handleOpen() {
    if (!user) {
      router.push('/login')
      return
    }
    setFailure(null)
    setOpen(true)
  }

  async function handleConfirm() {
    if (!user) {
      router.push('/login')
      return
    }
    setFailure(null)
    setIsLoading(true)
    try {
      const result = await bidService.buyNow(auctionId)
      useAuctionStore.getState().ownBuyNowCompleted(result, { id: user.id })
      toast.success(`You won ${title} for ${formatCurrency(result.price)}`)
      setOpen(false)
      void refetchWallet() // the click locked the deposit
    } catch (err) {
      const f = classifyBidError(err)
      if (f.kind === 'unauthenticated') {
        router.push('/login')
        return
      }
      void refetchWallet() // the deposit gate may have locked the deposit before the failure
      setFailure(f)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={handleOpen}
        className="w-full h-[46px] rounded-[10px] text-sm font-semibold bg-[var(--color-success-text)] text-[var(--color-success-subtle)] flex items-center justify-center transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:opacity-90"
      >
        Buy Now →
      </button>

      <Dialog open={open} onOpenChange={(next) => { if (!isLoading) setOpen(next) }}>
        <DialogContent showCloseButton={!isLoading}>
          <DialogHeader>
            <DialogTitle>Buy it now?</DialogTitle>
            <DialogDescription>{title}</DialogDescription>
          </DialogHeader>

          <dl className="flex flex-col gap-2 text-sm">
            <div className="flex items-center justify-between">
              <dt className="text-muted-foreground">Buy It Now price</dt>
              <dd className="font-mono font-medium">{formatCurrency(buyNowPrice)}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-muted-foreground">Deposit to lock</dt>
              <dd className="font-mono">{formatCurrency(depositAmount)}</dd>
            </div>
          </dl>

          <p className="text-xs text-muted-foreground">
            This ends the auction now. You then have 48 hours to pay the remaining balance;
            your deposit is forfeited if you don&apos;t.
          </p>

          {failureView && (
            <div role="alert" className="flex flex-col gap-1 text-xs text-[var(--color-danger-text)]">
              <p>{failureMessage}</p>
              {failureView.action && (
                <Link href={failureView.action.href} className="font-medium underline underline-offset-2">
                  {failureView.action.label}
                </Link>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" disabled={isLoading} onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="brand"
              disabled={isLoading || (failureView?.disablesForm ?? false)}
              onClick={() => void handleConfirm()}
            >
              {isLoading && <Loader2 className="size-4 animate-spin" />}
              Confirm purchase
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
