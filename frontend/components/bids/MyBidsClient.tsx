'use client'

import { useState } from 'react'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { MyBidsEmpty } from '@/components/bids/MyBidsEmpty'
import { MyBidsList } from '@/components/bids/MyBidsList'
import { MyBidsSkeleton } from '@/components/bids/MyBidsSkeleton'
import { useMyActiveBids } from '@/hooks/useMyActiveBids'
import { countByTab, filterByTab } from '@/lib/my-bids'
import type { MyBidsTab } from '@/types/ui/bid.ui'

const TABS: { value: MyBidsTab; label: string }[] = [
  { value: 'all',     label: 'All' },
  { value: 'winning', label: 'Winning' },
  { value: 'outbid',  label: 'Outbid' },
]

export function MyBidsClient() {
  const { bids, error, reload } = useMyActiveBids()
  const [tab, setTab] = useState<MyBidsTab>('all')

  const counts = countByTab(bids ?? [])
  const visible = bids ? filterByTab(bids, tab) : []

  return (
    <div className="flex flex-col gap-5">
      {error && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] px-4 py-2 text-sm text-[var(--color-warning-text)]"
        >
          <span>
            {bids ? "Couldn't refresh your bids. Showing the last loaded list." : "Couldn't load your bids."}
          </span>
          <Button variant="ghost" size="xl" onClick={reload} className="font-semibold underline">
            Retry
          </Button>
        </div>
      )}

      <div className="flex items-end justify-between gap-3 border-b">
        <Tabs value={tab} onValueChange={(value) => setTab(value as MyBidsTab)}>
          <TabsList variant="line" className="h-auto gap-6 p-0">
            {TABS.map(({ value, label }) => (
              <TabsTrigger key={value} value={value} className="flex-none px-0 pb-3.5 text-sm">
                {label}
                {bids && <span className="font-mono text-xs text-muted-foreground">{counts[value]}</span>}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <span className="hidden pb-3.5 text-xs text-muted-foreground sm:block">Sorted by ending soonest</span>
      </div>

      {bids === null ? (
        error ? null : <MyBidsSkeleton />
      ) : visible.length === 0 ? (
        <MyBidsEmpty tab={tab} />
      ) : (
        <MyBidsList bids={visible} />
      )}
    </div>
  )
}
