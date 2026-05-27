import Link from 'next/link'
import { PackageOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function EmptyAuctions() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <div className="flex size-16 items-center justify-center rounded-xl border border-dashed border-[var(--color-border-strong)] bg-[var(--color-bg-elevated)] text-muted-foreground">
        <PackageOpen className="size-7" />
      </div>
      <div className="flex flex-col gap-1">
        <p className="font-display font-medium text-[length:var(--font-size-lg)]">
          No auctions yet
        </p>
        <p className="max-w-xs text-sm text-muted-foreground">
          You haven&apos;t listed anything. Start with one item — most sellers get their first bid within 6 hours.
        </p>
      </div>
      <Button variant="brand" size="lg" render={<Link href="/seller/auctions/new" />} nativeButton={false}>
        + Create your first auction
      </Button>
      <p className="text-xs text-muted-foreground">
        Or{' '}
        <a href="#" className="underline underline-offset-2 hover:text-foreground transition-colors duration-[var(--duration-tesla)]">
          read the seller guide ↗
        </a>
      </p>
    </div>
  )
}
