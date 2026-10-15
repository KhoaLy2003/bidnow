'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { CurrencyInput } from '@/components/ui/currency-input'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'
import { fromCents, toCents } from '@/lib/money'
import { classifyPaymentError, describePaymentFailure, type PaymentFailure } from '@/lib/payment-errors'
import { formatHoursLeft, getShortfall } from '@/lib/payment-utils'
import { getErrorMessage } from '@/lib/utils'
import { walletService } from '@/services/wallet.service'
import { usePaymentStore } from '@/store/paymentStore'
import type { PendingPayment } from '@/types/ui/wallet.ui'

interface PayNowDialogProps {
  hold:         PendingPayment
  title:        string
  open:         boolean
  onOpenChange: (open: boolean) => void
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-mono">{value}</dd>
    </div>
  )
}

export function PayNowDialog({ hold, title, open, onOpenChange }: PayNowDialogProps) {
  const router = useRouter()
  const { available, isLoading: walletLoading, refetch: refetchWallet, depositFunds } = useWallet()

  // What the user typed in the top-up field; null means "use the shortfall".
  const [typedAmount, setTypedAmount] = useState<number | null>(null)
  const [isToppingUp, setIsToppingUp] = useState(false)
  const [isPaying, setIsPaying]       = useState(false)
  const [failure, setFailure]         = useState<PaymentFailure | null>(null)
  const [prevOpen, setPrevOpen]       = useState(open)

  // Opening the dialog starts clean (state adjusted during render, as CurrencyInput does, not in an effect).
  if (open !== prevOpen) {
    setPrevOpen(open)
    if (open) {
      setFailure(null)
      setTypedAmount(null)
    }
  }

  const failureView = failure ? describePaymentFailure(failure) : null
  const busy        = isPaying || isToppingUp
  // Until the wallet has loaded, `available` is a meaningless 0: do not claim a shortfall.
  const shortfall   = walletLoading ? null : getShortfall(hold, available)
  const blocked     = hold.expired || (failureView?.disablesForm ?? false)
  // Prefilled with the missing amount; follows the shortfall after a partial top-up until the user types.
  const topUpAmount = typedAmount ?? shortfall ?? 0

  async function handleTopUp() {
    const amount = fromCents(toCents(topUpAmount))
    if (amount <= 0) return
    setIsToppingUp(true)
    try {
      await depositFunds(amount) // credits the mock wallet, then refetches it
      setTypedAmount(null)
      setFailure(null)
      toast.success('Funds added')
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Failed to add funds'))
    } finally {
      setIsToppingUp(false)
    }
  }

  async function handlePay() {
    setFailure(null)
    setIsPaying(true)
    try {
      await walletService.confirmPayment(hold.auctionId)
      usePaymentStore.getState().markPaid(hold.auctionId)
      toast.success('Payment complete')
      onOpenChange(false)
      void refetchWallet()
      void usePaymentStore.getState().refresh()
    } catch (err: unknown) {
      const f = classifyPaymentError(err)
      if (f.kind === 'unauthenticated') {
        router.push('/login')
        return
      }
      if (
        f.kind === 'insufficient-balance' ||
        f.kind === 'expired' ||
        f.kind === 'no-longer-due' ||
        f.kind === 'unknown' ||
        f.kind === 'unavailable'
      ) {
        // Our view of the balance or of the hold may be stale, or the outcome of the request is
        // unknown (a gateway timeout may have succeeded server-side): resync both.
        void refetchWallet()
        void usePaymentStore.getState().refresh()
      }
      setFailure(f)
    } finally {
      setIsPaying(false)
    }
  }

  const payDisabled = busy || walletLoading || shortfall !== null || blocked

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent showCloseButton={!busy}>
        <DialogHeader>
          <DialogTitle>Complete payment</DialogTitle>
          <DialogDescription>{title}</DialogDescription>
        </DialogHeader>

        <dl className="flex flex-col gap-2 text-sm">
          <SummaryRow label="Winning price" value={formatCurrency(hold.totalAmount)} />
          <SummaryRow label="Deposit applied" value={`− ${formatCurrency(hold.depositApplied)}`} />
          <div className="flex items-center justify-between border-t pt-2">
            <dt className="font-medium">Remaining to pay</dt>
            <dd className="font-mono font-medium">{formatCurrency(hold.remaining)}</dd>
          </div>
          <SummaryRow label="Time left" value={hold.expired ? 'Deadline passed' : formatHoursLeft(hold.hoursLeft)} />
        </dl>

        {shortfall !== null && !blocked && (
          <div className="flex flex-col gap-2 rounded-lg bg-[var(--color-wallet-bg)] p-3">
            <p className="text-xs text-muted-foreground">
              You have {formatCurrency(available)} available and need {formatCurrency(hold.remaining)}.
              Add at least {formatCurrency(shortfall)} to continue.
            </p>
            <div className="flex gap-2">
              <div className="flex-1">
                <CurrencyInput value={topUpAmount} onChange={setTypedAmount} placeholder="0.00" aria-label="Top-up amount" disabled={busy} />
              </div>
              <Button
                variant="outline"
                disabled={busy || topUpAmount <= 0}
                onClick={() => void handleTopUp()}
              >
                {isToppingUp && <Loader2 className="size-4 animate-spin" />}
                Add funds
              </Button>
            </div>
          </div>
        )}

        {hold.expired && (
          <p role="alert" className="text-xs text-[var(--color-danger-text)]">
            The payment deadline has passed.
          </p>
        )}

        {failureView && (
          <p role="alert" className="text-xs text-[var(--color-danger-text)]">
            {failureView.message}
          </p>
        )}

        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="brand" disabled={payDisabled} onClick={() => void handlePay()}>
            {isPaying && <Loader2 className="size-4 animate-spin" />}
            {hold.remaining > 0 ? `Pay ${formatCurrency(hold.remaining)}` : 'Confirm payment'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
