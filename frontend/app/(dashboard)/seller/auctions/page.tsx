'use client'

import { useEffect, useState, useMemo, useCallback } from 'react'
import Link                  from 'next/link'
import { Loader2, AlertCircle, Plus, SearchX } from 'lucide-react'
import { Button }            from '@/components/ui/button'
import { SellerAuctionRow, SELLER_ROW_GRID } from '@/components/seller/SellerAuctionRow'
import { AuctionFilters, AuctionResultsBar } from '@/components/seller/AuctionFilters'
import { EmptyAuctions }     from '@/components/seller/EmptyAuctions'
import type { SellerAuction }  from '@/types/ui/seller.ui'
import { auctionService } from '@/services/auction.service'
import { mapAuctionSummaryToSellerAuction } from '@/types/mappers/auction.mapper'
import { toast } from 'sonner'
import { cn, getErrorMessage } from '@/lib/utils'
import { formatQueueClock } from '@/lib/home-queue'
import { useNow } from '@/hooks/useNow'
import {
  DEFAULT_SELLER_FILTERS, SELLER_ENDED_PREVIEW,
  buildSellerGroups, filterBySearchAndCategory, nextToClose, statusCounts, summarizeSellerAuctions,
  type SellerFilterState, type SellerGroupKey,
} from '@/lib/seller-auctions'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'

const GROUP_DOT: Record<SellerGroupKey, string> = {
  live:     'bg-[var(--color-auction-active-accent)]',
  upcoming: 'bg-[var(--color-auction-scheduled-text)]',
  ended:    'bg-[var(--color-neutral-400)]',
}

function LoadingState() {
  return (
    <div className="flex items-center justify-center gap-2 py-20 text-muted-foreground">
      <Loader2 className="size-4 animate-spin" />
      <span className="text-sm">Loading auctions…</span>
    </div>
  )
}

function ErrorState({ onRetry }: { onRetry(): void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
      <AlertCircle className="size-5 text-[var(--color-danger-text)]" />
      <p className="text-sm text-muted-foreground">Failed to load auctions.</p>
      <Button variant="outline" size="sm" onClick={onRetry}>Try again</Button>
    </div>
  )
}

function NoMatches({ onClear }: { onClear(): void }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-[var(--color-border-strong)] bg-background px-6 py-14 text-center">
      <SearchX aria-hidden className="size-7 text-muted-foreground" />
      <p className="font-display font-medium text-[length:var(--font-size-lg)]">No auctions match</p>
      <p className="max-w-sm text-sm text-muted-foreground">
        Nothing in your auctions fits these filters. Try a different title, or clear the filters to see everything again.
      </p>
      <Button variant="outline" className="h-11 px-5" onClick={onClear}>Clear all filters</Button>
    </div>
  )
}

export default function SellerAuctionsPage() {
  const [auctions,  setAuctions]  = useState<SellerAuction[]>([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState<string | null>(null)

  const [filters,    setFilters]    = useState<SellerFilterState>(DEFAULT_SELLER_FILTERS)
  const [categories, setCategories] = useState<AuctionCategoryResponse[]>([])
  const [deletedIds, setDeletedIds] = useState<string[]>([])
  const [showAllEnded, setShowAllEnded] = useState(false)

  const now = useNow(1000)

  useEffect(() => {
    auctionService.getCategories()
      .then(res => setCategories(res.data))
      .catch(err => console.error('Failed to load categories:', err))
  }, [])

  // Everything is fetched once and filtered here, so search, category and the status chips
  // respond instantly and their counts always agree with what is on screen.
  const fetchAuctions = useCallback(() => {
    Promise.all([
      auctionService.getMyAuctions({ type: 'active',  size: 100 }),
      auctionService.getMyAuctions({ type: 'history', size: 100 }),
    ])
      .then(([activeRes, historyRes]) => {
        const merged = [...(activeRes.data.data || []), ...(historyRes.data.data || [])]
        setAuctions(merged.map(mapAuctionSummaryToSellerAuction))
        setError(null)
      })
      .catch((err) => {
        setError('failed')
        toast.error(getErrorMessage(err, 'Failed to load auctions.'))
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => { fetchAuctions() }, [fetchAuctions])

  const retryFetch = useCallback(() => {
    setLoading(true)
    fetchAuctions()
  }, [fetchAuctions])

  const updateFilters = useCallback((patch: Partial<SellerFilterState>) => {
    setFilters(prev => ({ ...prev, ...patch }))
  }, [])

  const clearFilters = useCallback(() => {
    setFilters(prev => ({ ...DEFAULT_SELLER_FILTERS, sort: prev.sort }))
  }, [])

  const visible = useMemo(
    () => auctions.filter(a => !deletedIds.includes(a.id)),
    [auctions, deletedIds],
  )
  const counts = useMemo(
    () => statusCounts(filterBySearchAndCategory(visible, filters.query, filters.categoryId)),
    [visible, filters.query, filters.categoryId],
  )
  const groups = useMemo(() => buildSellerGroups(visible, filters), [visible, filters])
  const shownCount = groups.reduce((sum, g) => sum + g.auctions.length, 0)
  const next = useMemo(() => nextToClose(visible), [visible])
  const nextSeconds = next ? Math.max(0, Math.floor((next.endsAt.getTime() - now) / 1000)) : 0

  return (
    <div className="flex flex-col gap-5">
      {/* Page header */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-1.5">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">My auctions</h1>
          <p className="text-sm text-muted-foreground">
            {loading ? 'Loading…' : summarizeSellerAuctions(visible)}
          </p>
          {!loading && !error && next && (
            <p className="flex flex-wrap items-center gap-2 text-[13px] text-muted-foreground">
              <span aria-hidden className="size-1.5 animate-pulse rounded-full bg-[var(--color-auction-active-accent)]" />
              Next to close:
              <Link href={`/seller/auctions/${next.id}/manage`} className="font-medium text-foreground hover:text-[var(--color-text-brand)]">
                {next.title}
              </Link>
              <span className="font-mono font-medium text-foreground">{formatQueueClock(nextSeconds)}</span>
            </p>
          )}
        </div>
        <Button variant="brand" size="lg" className="h-11" render={<Link href="/seller/auctions/new" />} nativeButton={false}>
          <Plus aria-hidden className="size-4" />
          Create auction
        </Button>
      </div>

      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState onRetry={retryFetch} />
      ) : visible.length === 0 ? (
        <div className="rounded-xl border border-[var(--color-border-default)] bg-background">
          <EmptyAuctions />
        </div>
      ) : (
        <>
          <AuctionFilters filters={filters} categories={categories} counts={counts} onChange={updateFilters} />
          <AuctionResultsBar
            shown={shownCount}
            total={visible.length}
            filters={filters}
            categories={categories}
            onChange={updateFilters}
            onClearAll={clearFilters}
          />

          {groups.length === 0 && <NoMatches onClear={clearFilters} />}

          {groups.map(group => {
            const collapsible = group.key === 'ended' && group.auctions.length > SELLER_ENDED_PREVIEW
            const rows = collapsible && !showAllEnded ? group.auctions.slice(0, SELLER_ENDED_PREVIEW) : group.auctions
            return (
              <section
                key={group.key}
                aria-label={group.label}
                className="rounded-xl border border-[var(--color-border-default)] bg-background"
              >
                <div className={cn(SELLER_ROW_GRID, 'rounded-t-xl bg-[var(--color-bg-elevated)] px-4 py-3')}>
                  <div className="col-span-3 flex min-w-0 flex-wrap items-center gap-2.5 md:col-span-2">
                    <span aria-hidden className={cn('size-2 rounded-full', GROUP_DOT[group.key], group.key === 'live' && 'animate-pulse')} />
                    <h2 className="text-sm font-medium">{group.label}</h2>
                    <span className="font-mono text-xs text-muted-foreground">{group.auctions.length}</span>
                    <span className="text-xs text-muted-foreground">{group.hint}</span>
                  </div>
                  <span className="hidden text-right text-xs text-muted-foreground md:block">{group.columns.price}</span>
                  <span className="hidden text-right text-xs text-muted-foreground md:block">Bids</span>
                  <span className="hidden text-xs text-muted-foreground md:block">{group.columns.time}</span>
                  <span className="hidden text-xs text-muted-foreground md:block">{group.columns.status}</span>
                </div>

                {rows.map(a => (
                  <SellerAuctionRow
                    key={a.id}
                    auction={a}
                    now={now}
                    onDeleted={() => setDeletedIds(prev => [...prev, a.id])}
                  />
                ))}

                {collapsible && (
                  <div className="border-t border-[var(--color-border-default)] px-4 py-2">
                    <Button
                      variant="ghost"
                      className="h-10 px-3 text-[13px] text-[var(--color-text-link)]"
                      onClick={() => setShowAllEnded(v => !v)}
                    >
                      {showAllEnded ? 'Show fewer' : `Show ${group.auctions.length - SELLER_ENDED_PREVIEW} more ended`}
                    </Button>
                  </div>
                )}
              </section>
            )
          })}
        </>
      )}
    </div>
  )
}
