import { Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface AuctionSearchInputProps {
  defaultValue?: string
  preserveParams?: Record<string, string>
  placeholder?: string
  className?: string
}

export function AuctionSearchInput({
  defaultValue,
  preserveParams,
  placeholder = 'Search auctions…',
  className,
}: AuctionSearchInputProps) {
  return (
    <form action="/auctions" method="GET" className={cn('relative flex items-center', className)}>
      {preserveParams &&
        Object.entries(preserveParams).map(([key, val]) => (
          <input key={key} type="hidden" name={key} value={val} />
        ))}
      <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
      <Input
        name="q"
        type="search"
        defaultValue={defaultValue}
        placeholder={placeholder}
        className="pl-9 h-9"
      />
    </form>
  )
}
