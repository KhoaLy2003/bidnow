'use client'

import { useEffect, useState, useMemo, useCallback } from 'react'
import Link                  from 'next/link'
import { Loader2, AlertCircle } from 'lucide-react'
import { Button }            from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ActiveAuctionRow, HistoricalAuctionRow } from '@/components/seller/SellerAuctionRow'
import { AuctionFilters }    from '@/components/seller/AuctionFilters'
import { EmptyAuctions }     from '@/components/seller/EmptyAuctions'
import { SellerAuctionStatus } from '@/types/ui/seller.ui'
import type { SellerAuction }  from '@/types/ui/seller.ui'
import { auctionService } from '@/services/auction.service'
import { mapAuctionSummaryToSellerAuction } from '@/types/mappers/auction.mapper'
import { toast } from 'sonner'
import { getErrorMessage } from '@/lib/utils'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'

const ITEMS_PER_PAGE = 20

function TableHead({ children, right }: { readonly children: React.ReactNode; readonly right?: boolean }) {
  return (
    <th className={`border-b border-[var(--color-border-default)] py-2 text-[10px] font-medium uppercase tracking-wide text-muted-foreground ${right ? 'pr-4 text-right' : 'pr-4 text-left'}`}>
      {children}
    </th>
  )
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

export default function SellerAuctionsPage() {
  const [auctions,  setAuctions]  = useState<SellerAuction[]>([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState<string | null>(null)

  const [search,       setSearch]       = useState('')
  const [category,     setCategory]     = useState('') // selected category id, '' = All categories
  const [categories,   setCategories]   = useState<AuctionCategoryResponse[]>([])
  const [activePage,   setActivePage]   = useState(1)
  const [histPage,     setHistPage]     = useState(1)
  const [deletedIds,   setDeletedIds]   = useState<string[]>([])

  useEffect(() => {
    auctionService.getCategories()
      .then(res => setCategories(res.data))
      .catch(err => console.error('Failed to load categories:', err))
  }, [])

  const fetchAuctions = useCallback(() => {
    Promise.all([
      auctionService.getMyAuctions({ type: 'active', categoryId: category || undefined, size: 100 }),
      auctionService.getMyAuctions({ type: 'history', categoryId: category || undefined, size: 100 }),
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
  }, [category])

  useEffect(() => { fetchAuctions() }, [fetchAuctions])

  const retryFetch = useCallback(() => {
    setLoading(true)
    fetchAuctions()
  }, [fetchAuctions])

  const handleCategoryChange = useCallback((v: string) => {
    setLoading(true)
    setCategory(v)
    setActivePage(1)
    setHistPage(1)
  }, [])

  const { allActive, allHistorical } = useMemo(() => {
    const active: SellerAuction[] = []
    const historical: SellerAuction[] = []
    auctions.forEach(a => {
      if ([SellerAuctionStatus.Completed, SellerAuctionStatus.Failed, SellerAuctionStatus.Cancelled, SellerAuctionStatus.Rejected].includes(a.status)) {
        historical.push(a)
      } else {
        active.push(a)
      }
    })
    return { allActive: active, allHistorical: historical }
  }, [auctions])

  const activeAuctions = useMemo(() => {
    return allActive.filter(a => !deletedIds.includes(a.id)).filter(a => {
      if (search && !a.title.toLowerCase().includes(search.toLowerCase())) return false
      return true
    })
  }, [allActive, search, deletedIds])

  const histAuctions = useMemo(() => {
    return allHistorical.filter(a => {
      if (search && !a.title.toLowerCase().includes(search.toLowerCase())) return false
      return true
    })
  }, [allHistorical, search])

  const activePage_items = activeAuctions.slice((activePage - 1) * ITEMS_PER_PAGE, activePage * ITEMS_PER_PAGE)
  const histPage_items   = histAuctions.slice((histPage - 1)  * ITEMS_PER_PAGE, histPage  * ITEMS_PER_PAGE)
  const activePageCount  = Math.max(1, Math.ceil(activeAuctions.length / ITEMS_PER_PAGE))
  const histPageCount    = Math.max(1, Math.ceil(histAuctions.length  / ITEMS_PER_PAGE))

  return (
    <div className="flex flex-col gap-0 rounded-xl border border-[var(--color-border-default)] bg-background overflow-hidden">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4 px-6 py-5">
        <div className="flex flex-col gap-0.5">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">My auctions</h1>
          <p className="text-sm text-muted-foreground">
            {loading
              ? 'Loading…'
              : `${allActive.length + allHistorical.length} total · ${allActive.length} active · ${allHistorical.length} completed`
            }
          </p>
        </div>
        <Button variant="brand" size="lg" render={<Link href="/seller/auctions/new" />} nativeButton={false}>
          + Create auction
        </Button>
      </div>

      <Tabs defaultValue="active">
        <div className="px-6">
          <TabsList className="h-auto gap-0 rounded-none border-0 bg-transparent p-0">
            <TabsTrigger
              value="active"
              className="rounded-none border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground data-[state=active]:border-foreground data-[state=active]:text-foreground data-[state=active]:shadow-none bg-transparent"
            >
              Active <span className="ml-1.5 font-mono text-xs text-muted-foreground">{allActive.length}</span>
            </TabsTrigger>
            <TabsTrigger
              value="historical"
              className="rounded-none border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground data-[state=active]:border-foreground data-[state=active]:text-foreground data-[state=active]:shadow-none bg-transparent"
            >
              Historical <span className="ml-1.5 font-mono text-xs text-muted-foreground">{allHistorical.length}</span>
            </TabsTrigger>
          </TabsList>
        </div>

        {/* ── Active tab ── */}
        <TabsContent value="active" className="mt-0">
          <AuctionFilters
            search={search}
            category={category}
            categories={categories}
            onSearch={setSearch}
            onCategory={handleCategoryChange}
            total={activeAuctions.length}
            shown={activePage_items.length}
          />

          {loading ? (
            <LoadingState />
          ) : error ? (
            <ErrorState onRetry={retryFetch} />
          ) : activeAuctions.length === 0 ? (
            <EmptyAuctions />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr>
                    <th className="w-14 pl-6 py-2" />
                    <TableHead>Item</TableHead>
                    <TableHead right>Current bid ↓</TableHead>
                    <TableHead right>Bids</TableHead>
                    <TableHead>Time left</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead right>Actions</TableHead>
                  </tr>
                </thead>
                <tbody>
                  {activePage_items.map(a => (
                    <ActiveAuctionRow
                      key={a.id}
                      auction={a}
                      onDeleted={() => setDeletedIds(prev => [...prev, a.id])}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!loading && !error && activePageCount > 1 && (
            <Pagination page={activePage} total={activePageCount} onChange={setActivePage} count={activeAuctions.length} pageSize={ITEMS_PER_PAGE} />
          )}
        </TabsContent>

        {/* ── Historical tab ── */}
        <TabsContent value="historical" className="mt-0">
          <AuctionFilters
            search={search}
            category={category}
            categories={categories}
            onSearch={setSearch}
            onCategory={handleCategoryChange}
            total={histAuctions.length}
            shown={histPage_items.length}
          />

          {loading ? (
            <LoadingState />
          ) : error ? (
            <ErrorState onRetry={retryFetch} />
          ) : histAuctions.length === 0 ? (
            <div className="py-20 text-center text-sm text-muted-foreground">No completed auctions yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr>
                    <th className="w-14 pl-6 py-2" />
                    <TableHead>Item</TableHead>
                    <TableHead right>Final price</TableHead>
                    <TableHead>Winner</TableHead>
                    <TableHead>Ended</TableHead>
                    <TableHead>Outcome</TableHead>
                    <TableHead right>Actions</TableHead>
                  </tr>
                </thead>
                <tbody>
                  {histPage_items.map(a => (
                    <HistoricalAuctionRow key={a.id} auction={a} />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!loading && !error && histPageCount > 1 && (
            <Pagination page={histPage} total={histPageCount} onChange={setHistPage} count={histAuctions.length} pageSize={ITEMS_PER_PAGE} />
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}

// ── Pagination ──────────────────────────────────────────────────
function Pagination({ page, total, onChange, count, pageSize }: {
  page: number; total: number; onChange(p: number): void; count: number; pageSize: number
}) {
  const from = (page - 1) * pageSize + 1
  const to   = Math.min(page * pageSize, count)
  return (
    <div className="flex items-center justify-between px-6 py-3 border-t border-[var(--color-border-default)]">
      <p className="text-xs text-muted-foreground">Showing {from}–{to} of {count}</p>
      <div className="flex gap-1">
        <Button variant="outline" size="sm" className="h-7 w-7 p-0" disabled={page === 1} onClick={() => onChange(page - 1)}>‹</Button>
        {Array.from({ length: total }, (_, i) => i + 1).map(p => (
          <Button
            key={p}
            variant={p === page ? 'brand' : 'outline'}
            size="sm"
            className="h-7 w-7 p-0 text-xs"
            onClick={() => onChange(p)}
          >
            {p}
          </Button>
        ))}
        <Button variant="outline" size="sm" className="h-7 w-7 p-0" disabled={page === total} onClick={() => onChange(page + 1)}>›</Button>
      </div>
    </div>
  )
}
