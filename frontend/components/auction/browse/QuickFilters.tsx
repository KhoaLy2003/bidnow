'use client'

import { Checkbox } from '@/components/ui/checkbox'
import { Label }    from '@/components/ui/label'
import type { BrowseFilters } from '@/types/ui/browse.ui'

interface QuickFiltersProps {
  endingSoon: boolean
  buyNow:     boolean
  onChange:   (partial: Partial<Pick<BrowseFilters, 'endingSoon' | 'buyNow'>>) => void
}

export function QuickFilters({ endingSoon, buyNow, onChange }: QuickFiltersProps) {
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center gap-2.5">
        <Checkbox
          id="filter-ending-soon"
          checked={endingSoon}
          onCheckedChange={(checked) => onChange({ endingSoon: !!checked })}
        />
        <Label htmlFor="filter-ending-soon" className="cursor-pointer text-sm">
          Ending Soon
        </Label>
      </div>

      <div className="flex items-center gap-2.5">
        <Checkbox
          id="filter-buy-now"
          checked={buyNow}
          onCheckedChange={(checked) => onChange({ buyNow: !!checked })}
        />
        <Label htmlFor="filter-buy-now" className="cursor-pointer text-sm">
          Buy Now Available
        </Label>
      </div>
    </div>
  )
}
