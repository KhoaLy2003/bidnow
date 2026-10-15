'use client'

import { useEffect, useState } from 'react'
import { CheckCircle2, Clock, MinusCircle, RefreshCw, XCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCurrency } from '@/lib/format'
import { IDLE_LOOKUP } from '@/lib/payment-utils'
import {
  deriveSellerPaymentState,
  formatPaymentTimeLeft,
  type SellerPaymentState,
} from '@/lib/seller-payment'
import { usePaymentStore } from '@/store/paymentStore'

/** How often the "time left" figure is recomputed from the deadline. */
const CLOCK_TICK_MS = 30_000

type BadgeVariant = 'ending-soon' | 'critical' | 'won' | 'lost' | 'closed'
type StepKind = 'done' | 'now' | 'next' | 'fail' | 'skip'

const BADGE: Record<SellerPaymentState, { label: string; variant: BadgeVariant } | null> = {
  loading:   null,
  error:     null,
  awaiting:  { label: 'Awaiting payment', variant: 'ending-soon' },
  overdue:   { label: 'Payment overdue',  variant: 'critical' },
  paid:      { label: 'Paid',             variant: 'won' },
  forfeited: { label: 'Forfeited',        variant: 'lost' },
  cancelled: { label: 'Cancelled',        variant: 'closed' },
  none:      null,
}

interface Step { title: string; body: string; kind: StepKind }

function stepsFor(state: SellerPaymentState, amount: string, who: string): Step[] {
  const closed: Step = { title: 'Auction closed', body: `${who} won with a bid of ${amount}.`, kind: 'done' }
  switch (state) {
    case 'paid':
      return [
        closed,
        { title: 'Winner paid', body: 'The full amount was paid.', kind: 'done' },
        { title: 'Funds credited to you', body: `${amount} was added to your wallet.`, kind: 'done' },
      ]
    case 'forfeited':
      return [
        closed,
        { title: 'Winner did not pay in time', body: "The payment window passed and the winner's deposit was forfeited.", kind: 'fail' },
        { title: 'Funds credited to you', body: 'Not applicable: no payment was made.', kind: 'skip' },
      ]
    case 'cancelled':
      return [
        closed,
        { title: 'Sale cancelled', body: 'The payment hold was cancelled and nothing was charged.', kind: 'fail' },
        { title: 'Funds credited to you', body: 'Not applicable: no payment was made.', kind: 'skip' },
      ]
    case 'overdue':
      return [
        closed,
        { title: 'Winner has not paid', body: 'The payment window has passed. The sale will be closed as no sale shortly.', kind: 'fail' },
        { title: 'Funds credited to you', body: 'Not applicable unless the winner pays.', kind: 'skip' },
      ]
    default:
      return [
        closed,
        { title: 'Winner pays', body: `Waiting for ${who} to pay.`, kind: 'now' },
        { title: 'Funds credited to you', body: "Added to your wallet once the payment is confirmed. You'll be notified.", kind: 'next' },
      ]
  }
}

function StepIcon({ kind }: { kind: StepKind }) {
  const cls = 'mt-0.5 size-[18px] shrink-0'
  switch (kind) {
    case 'done': return <CheckCircle2 className={`${cls} text-[var(--color-auction-won-accent)]`} aria-hidden />
    case 'now':  return <Clock className={`${cls} text-[var(--color-auction-ending-accent)]`} aria-hidden />
    case 'fail': return <XCircle className={`${cls} text-[var(--color-auction-critical-accent)]`} aria-hidden />
    default:     return <MinusCircle className={`${cls} text-muted-foreground`} aria-hidden />
  }
}

const NOTE: Partial<Record<SellerPaymentState, string>> = {
  awaiting:  "If the winner doesn't pay in time, their deposit is forfeited and the auction is closed as no sale.",
  overdue:   'The winner can no longer pay. Their deposit is forfeited once the sale is processed.',
  paid:      'Payment is complete. Arrange delivery with the buyer.',
  forfeited: 'The sale did not complete, so the auction is closed as no sale. You can list the item again.',
}

interface SellerPaymentPanelProps {
  auctionId:        string
  /** The winning bid, in dollars. */
  amount:           number
  paymentDeadline?: Date
  winnerPaidAt?:    Date
  /** The winner's display name, when known. */
  winnerName?:      string
}

export function SellerPaymentPanel({ auctionId, amount, paymentDeadline, winnerPaidAt, winnerName }: SellerPaymentPanelProps) {
  const who = winnerName ? `@${winnerName}` : 'the winner'
  // IDLE_LOOKUP is a module constant on purpose: a selector returning a fresh object makes zustand re-render forever.
  const lookup = usePaymentStore((s) => s.statuses[auctionId] ?? IDLE_LOOKUP)
  const loadStatus = usePaymentStore((s) => s.loadStatus)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    void loadStatus(auctionId)
  }, [auctionId, loadStatus])

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS)
    return () => clearInterval(t)
  }, [])

  const state = deriveSellerPaymentState({ lookup, paymentDeadline, winnerPaidAt, now })
  const badge = BADGE[state]
  const amountText = formatCurrency(amount)
  const timeLeft = formatPaymentTimeLeft(paymentDeadline, now)
  const pending = state === 'awaiting' || state === 'overdue'

  return (
    <section aria-labelledby="seller-payment-heading" className="rounded-xl border border-[var(--color-border-default)] overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border-default)] px-5 py-4">
        <h2 id="seller-payment-heading" className="text-sm font-medium">Winner payment</h2>
        {badge && <Badge variant={badge.variant}>{badge.label}</Badge>}
      </div>

      {state === 'loading' && (
        <div className="p-5" role="status" aria-label="Loading payment status">
          <Skeleton className="h-24 w-full" />
        </div>
      )}

      {state === 'error' && (
        <div className="flex flex-col items-start gap-3 p-5">
          <p className="text-sm text-muted-foreground">We couldn&apos;t load the payment status.</p>
          <Button variant="outline" size="sm" onClick={() => void loadStatus(auctionId)}>
            Try again
          </Button>
        </div>
      )}

      {state === 'none' && (
        <div className="flex flex-col items-start gap-3 p-5">
          <p className="text-sm text-muted-foreground">
            No payment is on record for this sale yet. It appears here once the winner&apos;s payment hold is created.
          </p>
          <Button variant="ghost" size="sm" onClick={() => void loadStatus(auctionId)}>
            <RefreshCw className="mr-1.5 size-3.5" aria-hidden />
            Refresh status
          </Button>
        </div>
      )}

      {badge && (
        <div className="flex flex-col gap-5 p-5">
          <div className="flex flex-wrap gap-6">
            <div className="min-w-[180px] flex-1">
              <p className="text-xs uppercase text-muted-foreground">Sale amount</p>
              <p className="mt-1 font-mono text-2xl font-medium">{amountText}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">winning bid by {who}</p>
            </div>
            {pending && (
              <div className="min-w-[180px] flex-1">
                <p className="text-xs uppercase text-muted-foreground">Time left to pay</p>
                <p className={`mt-1 font-mono text-2xl font-medium ${state === 'overdue' ? 'text-[var(--color-danger-text)]' : 'text-[var(--color-warning-text)]'}`}>
                  {timeLeft ?? (state === 'overdue' ? 'Deadline passed' : '—')}
                </p>
              </div>
            )}
            {state === 'paid' && winnerPaidAt && (
              <div className="min-w-[180px] flex-1">
                <p className="text-xs uppercase text-muted-foreground">Paid on</p>
                <p className="mt-1 font-mono text-2xl font-medium">
                  {winnerPaidAt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                </p>
              </div>
            )}
          </div>

          <ol className="flex flex-col gap-3.5">
            {stepsFor(state, amountText, who).map((step) => (
              <li key={step.title} className="flex items-start gap-3">
                <StepIcon kind={step.kind} />
                <div>
                  <p className={`text-sm font-medium ${step.kind === 'next' || step.kind === 'skip' ? 'text-muted-foreground' : ''}`}>
                    {step.title}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{step.body}</p>
                </div>
              </li>
            ))}
          </ol>

          {NOTE[state] && (
            <p className="rounded-md border border-[var(--color-auction-closed-border)] bg-[var(--color-auction-draft-bg)] px-3.5 py-3 text-[13px] leading-relaxed text-[var(--color-text-secondary)]">
              {NOTE[state]}
            </p>
          )}

          {pending && (
            <Button variant="ghost" size="sm" className="self-start" onClick={() => void loadStatus(auctionId)}>
              <RefreshCw className="mr-1.5 size-3.5" aria-hidden />
              Refresh status
            </Button>
          )}
        </div>
      )}
    </section>
  )
}
