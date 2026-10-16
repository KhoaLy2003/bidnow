import { ArrowDown, Trophy } from 'lucide-react'
import { cn } from '@/lib/utils'

interface BidStatusPillProps {
  isWinning:  boolean
  className?: string
}

/** Winning (success tokens) or Outbid (the existing outbid auction tokens). */
export function BidStatusPill({ isWinning, className }: BidStatusPillProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium',
        isWinning
          ? 'border-[var(--color-success-border)] bg-[var(--color-success-subtle)] text-[var(--color-success-text)]'
          : 'border-[var(--color-auction-outbid-border)] bg-[var(--color-auction-outbid-bg)] text-[var(--color-auction-outbid-text)]',
        className,
      )}
    >
      {isWinning ? <Trophy className="size-3" /> : <ArrowDown className="size-3" />}
      {isWinning ? 'Winning' : 'Outbid'}
    </span>
  )
}
