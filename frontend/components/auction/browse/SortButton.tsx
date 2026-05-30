'use client'

import { ChevronDown } from 'lucide-react'
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
          <Button variant="outline" size="sm" className="gap-1.5 shrink-0" />
        }
      >
        <span className="hidden sm:inline">Sort:</span> {SORT_LABELS[value]}
        <ChevronDown className="size-3.5 text-muted-foreground" />
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
