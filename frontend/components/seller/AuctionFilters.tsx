'use client'

import { Search } from 'lucide-react'
import { Input }  from '@/components/ui/input'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'

interface AuctionFiltersProps {
  tab:         'active' | 'historical'
  search:      string
  category:    string
  statusFilter: string
  onSearch(v: string): void
  onCategory(v: string): void
  onStatusFilter(v: string): void
  total?:      number
  shown?:      number
}

const CATEGORIES = [
  'All categories', 'Watches & Jewelry', 'Electronics', 'Art & Collectibles',
  'Home & Furniture', 'Fashion', 'Vehicles', 'Music & Instruments',
]

const ACTIVE_STATUSES   = ['All statuses', 'Active', 'Ending Soon', 'Critical', 'Draft']
const HIST_OUTCOMES     = ['All outcomes', 'Sold', 'No Sale', 'Cancelled']

export function AuctionFilters({
  tab, search, category, statusFilter,
  onSearch, onCategory, onStatusFilter,
  total, shown,
}: AuctionFiltersProps) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border-default)] px-6 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by title"
            value={search}
            onChange={e => onSearch(e.target.value)}
            className="h-8 w-52 pl-8 text-sm"
          />
        </div>

        <Select value={category} onValueChange={onCategory}>
          <SelectTrigger className="h-8 w-40 text-sm">
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            {CATEGORIES.map(c => (
              <SelectItem key={c} value={c}>{c}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={statusFilter} onValueChange={onStatusFilter}>
          <SelectTrigger className="h-8 w-36 text-sm">
            <SelectValue placeholder={tab === 'active' ? 'Status' : 'Outcome'} />
          </SelectTrigger>
          <SelectContent>
            {(tab === 'active' ? ACTIVE_STATUSES : HIST_OUTCOMES).map(s => (
              <SelectItem key={s} value={s}>{s}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-3">
        {total !== undefined && shown !== undefined && (
          <span className="text-xs text-muted-foreground">{shown} of {total}</span>
        )}
        <Button variant="ghost" size="sm" className="h-8 text-xs">
          ↓ Export
        </Button>
      </div>
    </div>
  )
}
