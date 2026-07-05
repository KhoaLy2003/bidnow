'use client'

import { Search } from 'lucide-react'
import { Input }  from '@/components/ui/input'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import type { AuctionCategoryResponse } from '@/types/api/auction.api'

interface AuctionFiltersProps {
  search:     string
  category:   string
  categories: AuctionCategoryResponse[]
  onSearch(v: string): void
  onCategory(v: string): void
  total?:     number
  shown?:     number
}

export function AuctionFilters({
  search, category, categories,
  onSearch, onCategory,
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

        <Select value={category} onValueChange={(v) => { if (v !== null) onCategory(v) }}>
          <SelectTrigger className="h-8 w-40 text-sm">
            <SelectValue placeholder="Category">
              {category ? categories.find(c => c.id === category)?.name : 'All categories'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">All categories</SelectItem>
            {categories.map(c => (
              <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
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
