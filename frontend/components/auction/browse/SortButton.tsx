'use client'

import { ArrowUpDown, ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
} from '@/components/ui/dropdown-menu'
import { SORT_OPTIONS, SORT_LABELS, type SortOption } from '@/types/ui/browse.ui'

interface SortButtonProps {
  value:    SortOption
  onChange: (sort: SortOption) => void
}

export function SortButton({ value, onChange }: SortButtonProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="outline" className="h-10 shrink-0 gap-2 px-3.5" aria-label={`Sort: ${SORT_LABELS[value]}`} />
        }
      >
        <ArrowUpDown aria-hidden className="size-4 text-muted-foreground" />
        <span className="hidden font-normal text-muted-foreground sm:inline">Sort</span>
        <span>{SORT_LABELS[value]}</span>
        <ChevronDown aria-hidden className="size-4" />
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end">
        <DropdownMenuRadioGroup
          value={value}
          onValueChange={(v) => onChange(v as SortOption)}
        >
          {SORT_OPTIONS.map((opt) => (
            <DropdownMenuRadioItem key={opt} value={opt}>
              {SORT_LABELS[opt]}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
