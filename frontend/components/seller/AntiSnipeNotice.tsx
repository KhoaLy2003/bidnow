import { Zap } from 'lucide-react'

export function AntiSnipeNotice() {
  return (
    <div className="flex items-start gap-2.5 rounded-md border border-[var(--color-auction-ending-border)] bg-[var(--color-auction-ending-bg)] p-3">
      <Zap className="mt-0.5 size-4 shrink-0 text-[var(--color-auction-ending-text)]" />
      <div className="flex flex-col gap-0.5">
        <p className="text-sm font-medium text-[var(--color-auction-ending-text)]">
          Anti-sniping protection enabled
        </p>
        <p className="text-sm text-[var(--color-auction-ending-text)]">
          Auction extends by 5 minutes if any bid is placed in the final 5 minutes.
        </p>
      </div>
    </div>
  )
}
