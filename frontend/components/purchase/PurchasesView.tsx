'use client'

import { useEffect, useMemo, useRef } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PaymentsDueBanner } from '@/components/purchase/PaymentsDueBanner'
import { PurchaseRow } from '@/components/purchase/PurchaseRow'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { usePurchases } from '@/hooks/usePurchases'
import {
  PURCHASE_FILTERS,
  applyPaidIds,
  buildPurchasesHref,
  parsePurchaseFilter,
  parsePurchasePage,
  type PurchaseFilter,
} from '@/lib/purchase-utils'
import { cn } from '@/lib/utils'

const CHIP_BASE =
  'inline-flex h-10 items-center rounded-full border px-4 text-sm font-medium transition-colors duration-[var(--duration-tesla)]'
const CHIP_ACTIVE = 'border-brand-600 bg-accent text-[var(--color-text-brand)]'
const CHIP_IDLE = 'border-border text-muted-foreground hover:border-foreground hover:text-foreground'

function SkeletonRows() {
  return (
    <div aria-busy="true" className="divide-y">
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center gap-4 px-5 py-4">
          <Skeleton className="size-16 rounded-xl" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3 w-1/4" />
          </div>
          <Skeleton className="h-6 w-28 rounded-full" />
          <Skeleton className="h-5 w-20" />
        </div>
      ))}
    </div>
  )
}

function EmptyState({ filter }: { filter: PurchaseFilter }) {
  if (filter !== 'all') {
    const label = PURCHASE_FILTERS.find((f) => f.value === filter)?.label.toLowerCase() ?? filter
    return (
      <div className="px-6 py-14 text-center">
        <p className="font-medium">Nothing {label}</p>
        <Button className="mt-4" variant="outline" nativeButton={false} render={<Link href="/purchases" />}>
          Show all purchases
        </Button>
      </div>
    )
  }
  return (
    <div className="px-6 py-16 text-center">
      <span className="mx-auto mb-4 flex size-12 items-center justify-center rounded-full bg-accent text-[var(--color-text-brand)]">
        <Receipt className="size-5" aria-hidden="true" />
      </span>
      <h2 className="font-display text-xl font-semibold tracking-tight">No purchases yet</h2>
      <p className="mx-auto mt-1.5 max-w-sm text-muted-foreground">
        Auctions you win, by bidding or Buy It Now, show up here with their payment status.
      </p>
      <Button className="mt-5" variant="brand" size="lg" nativeButton={false} render={<Link href="/auctions" />}>
        Browse auctions
      </Button>
    </div>
  )
}

export function PurchasesView() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const filter = parsePurchaseFilter(searchParams.get('status'))
  const pageIndex = parsePurchasePage(searchParams.get('page'))

  const { data, loading, failed, refetch } = usePurchases(filter, pageIndex)
  const { holds, paidIds } = usePendingPayments()

  // A payment made on this page: let the server list catch up with the optimistic "Paid" row.
  const paidCount = useRef(paidIds.size)
  useEffect(() => {
    if (paidIds.size === paidCount.current) return
    paidCount.current = paidIds.size
    void refetch()
  }, [paidIds, refetch])

  const items = useMemo(() => (data ? applyPaidIds(data.items, paidIds) : []), [data, paidIds])
  const holdsById = useMemo(() => new Map(holds.map((h) => [h.auctionId, h])), [holds])

  return (
    <div className="mx-auto w-full max-w-5xl">
      <h1 className="font-display text-[length:var(--font-size-3xl)] font-semibold tracking-tight">
        Purchase history
      </h1>
      <p className="mb-6 mt-1.5 text-muted-foreground">
        Every auction you&apos;ve won, by bidding or Buy It Now, and where payment stands.
      </p>

      {filter === 'all' && pageIndex === 0 && (
        <PaymentsDueBanner holds={holds} onShowPending={() => router.replace(buildPurchasesHref('pending', 0))} />
      )}

      <nav aria-label="Filter by payment status" className="mb-4 flex flex-wrap gap-2">
        {PURCHASE_FILTERS.map((f) => (
          <Link
            key={f.value}
            href={buildPurchasesHref(f.value, 0)}
            replace
            aria-current={f.value === filter ? 'page' : undefined}
            className={cn(CHIP_BASE, f.value === filter ? CHIP_ACTIVE : CHIP_IDLE)}
          >
            {f.label}
          </Link>
        ))}
      </nav>

      <section
        aria-label="Purchases"
        className={cn(
          'overflow-hidden rounded-[20px] border bg-card shadow-[0_1px_2px_rgb(20_22_26/0.04),0_12px_32px_rgb(20_22_26/0.06)]',
          loading && data && 'opacity-60',
        )}
      >
        {failed && !data ? (
          <div role="alert" className="flex items-center justify-between gap-3 px-5 py-4 text-sm">
            <span className="text-muted-foreground">Couldn&apos;t load your purchases.</span>
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              Retry
            </Button>
          </div>
        ) : !data ? (
          <SkeletonRows />
        ) : items.length === 0 ? (
          <EmptyState filter={filter} />
        ) : (
          <ul className="divide-y">
            {items.map((p) => (
              <li key={p.auctionId}>
                <PurchaseRow purchase={p} hold={holdsById.get(p.auctionId)} />
              </li>
            ))}
          </ul>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between gap-3 border-t bg-muted/40 px-5 py-3 text-sm">
            <span className="text-muted-foreground">
              Page {data.page + 1} of {data.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={!data.hasPrev}
                nativeButton={false}
                render={<Link href={buildPurchasesHref(filter, data.page - 1)} replace aria-label="Previous page" />}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={!data.hasNext}
                nativeButton={false}
                render={<Link href={buildPurchasesHref(filter, data.page + 1)} replace aria-label="Next page" />}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
