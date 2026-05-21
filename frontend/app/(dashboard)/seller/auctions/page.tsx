'use client'

import { useState, useMemo } from 'react'
import Link                  from 'next/link'
import { Button }            from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ActiveAuctionRow, HistoricalAuctionRow } from '@/components/seller/SellerAuctionRow'
import { AuctionFilters }    from '@/components/seller/AuctionFilters'
import { EmptyAuctions }     from '@/components/seller/EmptyAuctions'
import { SellerAuctionStatus } from '@/types/seller'
import type { SellerAuction }  from '@/types/seller'

// ── Mock data ──────────────────────────────────────────────────
const now = Date.now()

const MOCK_ACTIVE: SellerAuction[] = [
  {
    id: 'au-29481', title: 'Hasselblad 500C/M Medium Format — body + 80mm', description: '',
    imageUrls: [], categoryId: 'electronics', categoryName: 'Electronics',
    sellerId: 'me', startingPrice: 120_000, currentBid: 142_000, bidIncrement: 5_000,
    depositAmount: 8_400, totalBids: 18, watchers: 47,
    startsAt: new Date(now - 2 * 86_400_000),
    endsAt:   new Date(now + 2 * 3_600_000 + 14 * 60_000),
    createdAt: new Date(now - 3 * 86_400_000),
    status: SellerAuctionStatus.Active,
  },
  {
    id: 'au-29482', title: '1968 Omega Speedmaster Pre-Moon (Cal. 321)', description: '',
    imageUrls: [], categoryId: 'watches', categoryName: 'Watches & Jewelry',
    sellerId: 'me', startingPrice: 250_000, currentBid: 420_000, bidIncrement: 5_000,
    depositAmount: 17_500, totalBids: 42, watchers: 124,
    startsAt: new Date(now - 4 * 86_400_000),
    endsAt:   new Date(now + 11 * 60_000 + 4_000),
    createdAt: new Date(now - 5 * 86_400_000),
    status: SellerAuctionStatus.EndingSoon,
  },
  {
    id: 'au-29483', title: 'Eames Lounge & Ottoman — rosewood, 1971', description: '',
    imageUrls: [], categoryId: 'furniture', categoryName: 'Home & Furniture',
    sellerId: 'me', startingPrice: 180_000, currentBid: 280_000, bidIncrement: 2_500,
    depositAmount: 12_600, totalBids: 7, watchers: 23,
    startsAt: new Date(now - 5 * 86_400_000),
    endsAt:   new Date(now + 48_000),
    createdAt: new Date(now - 6 * 86_400_000),
    status: SellerAuctionStatus.Critical,
  },
  {
    id: 'au-29484', title: 'Fender Telecaster Custom 1972 — natural finish', description: '',
    imageUrls: [], categoryId: 'music', categoryName: 'Music & Instruments',
    sellerId: 'me', startingPrice: 80_000, currentBid: 95_000, bidIncrement: 2_500,
    depositAmount: 5_600, totalBids: 23, watchers: 61,
    startsAt: new Date(now - 86_400_000),
    endsAt:   new Date(now + 30 * 3_600_000),
    createdAt: new Date(now - 2 * 86_400_000),
    status: SellerAuctionStatus.Active,
  },
  {
    id: 'au-29490', title: "Vintage Persian rug, 6'×9' — Tabriz", description: '',
    imageUrls: [], categoryId: 'furniture', categoryName: 'Home & Furniture',
    sellerId: 'me', startingPrice: 80_000, currentBid: 0, bidIncrement: 2_500,
    depositAmount: 8_000, totalBids: 0, watchers: 0,
    startsAt: new Date(now + 86_400_000),
    endsAt:   new Date(now + 6 * 86_400_000),
    createdAt: new Date(now - 86_400_000),
    status: SellerAuctionStatus.Draft,
  },
]

const MOCK_HISTORICAL: SellerAuction[] = [
  {
    id: 'au-10001', title: 'Leica M6 Classic — silver, original strap', description: '',
    imageUrls: [], categoryId: 'electronics', categoryName: 'Electronics',
    sellerId: 'me', startingPrice: 200_000, currentBid: 315_000, bidIncrement: 5_000,
    depositAmount: 14_000, totalBids: 22, watchers: 58,
    startsAt: new Date(now - 14 * 86_400_000),
    endsAt:   new Date(now - 7 * 86_400_000),
    createdAt: new Date(now - 15 * 86_400_000),
    status: SellerAuctionStatus.Won,
    winnerName: 'mauve_42',
  },
  {
    id: 'au-10002', title: 'Set of 4 Eames DSW chairs — walnut', description: '',
    imageUrls: [], categoryId: 'furniture', categoryName: 'Home & Furniture',
    sellerId: 'me', startingPrice: 50_000, currentBid: 68_000, bidIncrement: 1_000,
    depositAmount: 3_500, totalBids: 11, watchers: 29,
    startsAt: new Date(now - 20 * 86_400_000),
    endsAt:   new Date(now - 12 * 86_400_000),
    createdAt: new Date(now - 21 * 86_400_000),
    status: SellerAuctionStatus.Won,
    winnerName: 'dlrt',
  },
  {
    id: 'au-10003', title: 'Rolex Submariner 14060 — no date', description: '',
    imageUrls: [], categoryId: 'watches', categoryName: 'Watches & Jewelry',
    sellerId: 'me', startingPrice: 700_000, currentBid: 0, bidIncrement: 10_000,
    depositAmount: 49_000, totalBids: 0, watchers: 12,
    startsAt: new Date(now - 30 * 86_400_000),
    endsAt:   new Date(now - 23 * 86_400_000),
    createdAt: new Date(now - 31 * 86_400_000),
    status: SellerAuctionStatus.Failed,
  },
  {
    id: 'au-10004', title: 'Mid-century brass floor lamp', description: '',
    imageUrls: [], categoryId: 'furniture', categoryName: 'Home & Furniture',
    sellerId: 'me', startingPrice: 15_000, currentBid: 21_000, bidIncrement: 500,
    depositAmount: 1_050, totalBids: 6, watchers: 14,
    startsAt: new Date(now - 40 * 86_400_000),
    endsAt:   new Date(now - 33 * 86_400_000),
    createdAt: new Date(now - 41 * 86_400_000),
    status: SellerAuctionStatus.Won,
    winnerName: 'noor.k',
  },
  {
    id: 'au-10005', title: 'Polaroid SX-70 — alpha 1, with case', description: '',
    imageUrls: [], categoryId: 'electronics', categoryName: 'Electronics',
    sellerId: 'me', startingPrice: 30_000, currentBid: 0, bidIncrement: 500,
    depositAmount: 2_100, totalBids: 0, watchers: 8,
    startsAt: new Date(now - 50 * 86_400_000),
    endsAt:   new Date(now - 43 * 86_400_000),
    createdAt: new Date(now - 51 * 86_400_000),
    status: SellerAuctionStatus.Failed,
  },
]
// ──────────────────────────────────────────────────────────────

const ITEMS_PER_PAGE = 20

function TableHead({ children, right }: { children: React.ReactNode; right?: boolean }) {
  return (
    <th className={`border-b border-[var(--color-border-default)] py-2 text-[10px] font-medium uppercase tracking-wide text-muted-foreground ${right ? 'pr-4 text-right' : 'pr-4 text-left'}`}>
      {children}
    </th>
  )
}

export default function SellerAuctionsPage() {
  const [search,      setSearch]      = useState('')
  const [category,    setCategory]    = useState('All categories')
  const [statusFilter, setStatusFilter] = useState('All statuses')
  const [activePage,  setActivePage]  = useState(1)
  const [histPage,    setHistPage]    = useState(1)
  const [deletedIds,  setDeletedIds]  = useState<string[]>([])

  const activeAuctions = useMemo(() => {
    return MOCK_ACTIVE.filter(a => !deletedIds.includes(a.id)).filter(a => {
      if (search && !a.title.toLowerCase().includes(search.toLowerCase())) return false
      if (category !== 'All categories' && a.categoryName !== category) return false
      return true
    })
  }, [search, category, deletedIds])

  const histAuctions = useMemo(() => {
    return MOCK_HISTORICAL.filter(a => {
      if (search && !a.title.toLowerCase().includes(search.toLowerCase())) return false
      if (category !== 'All categories' && a.categoryName !== category) return false
      return true
    })
  }, [search, category])

  const activePage_items  = activeAuctions.slice((activePage - 1) * ITEMS_PER_PAGE, activePage * ITEMS_PER_PAGE)
  const histPage_items    = histAuctions.slice((histPage - 1)  * ITEMS_PER_PAGE, histPage  * ITEMS_PER_PAGE)
  const activePageCount   = Math.max(1, Math.ceil(activeAuctions.length / ITEMS_PER_PAGE))
  const histPageCount     = Math.max(1, Math.ceil(histAuctions.length  / ITEMS_PER_PAGE))

  return (
    <div className="flex flex-col gap-0 rounded-xl border border-[var(--color-border-default)] bg-background overflow-hidden">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4 px-6 py-5">
        <div className="flex flex-col gap-0.5">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">My auctions</h1>
          <p className="text-sm text-muted-foreground">
            {MOCK_ACTIVE.length + MOCK_HISTORICAL.length} total · {MOCK_ACTIVE.length} active · {MOCK_HISTORICAL.length} completed
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
              Active <span className="ml-1.5 font-mono text-xs text-muted-foreground">{MOCK_ACTIVE.length}</span>
            </TabsTrigger>
            <TabsTrigger
              value="historical"
              className="rounded-none border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted-foreground data-[state=active]:border-foreground data-[state=active]:text-foreground data-[state=active]:shadow-none bg-transparent"
            >
              Historical <span className="ml-1.5 font-mono text-xs text-muted-foreground">{MOCK_HISTORICAL.length}</span>
            </TabsTrigger>
          </TabsList>
        </div>

        {/* ── Active tab ── */}
        <TabsContent value="active" className="mt-0">
          <AuctionFilters
            tab="active"
            search={search}
            category={category}
            statusFilter={statusFilter}
            onSearch={setSearch}
            onCategory={setCategory}
            onStatusFilter={setStatusFilter}
            total={activeAuctions.length}
            shown={activePage_items.length}
          />

          {activeAuctions.length === 0 ? (
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

          {activePageCount > 1 && (
            <Pagination page={activePage} total={activePageCount} onChange={setActivePage} count={activeAuctions.length} pageSize={ITEMS_PER_PAGE} />
          )}
        </TabsContent>

        {/* ── Historical tab ── */}
        <TabsContent value="historical" className="mt-0">
          <AuctionFilters
            tab="historical"
            search={search}
            category={category}
            statusFilter={statusFilter}
            onSearch={setSearch}
            onCategory={setCategory}
            onStatusFilter={setStatusFilter}
            total={histAuctions.length}
            shown={histPage_items.length}
          />

          {histAuctions.length === 0 ? (
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

          {histPageCount > 1 && (
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
