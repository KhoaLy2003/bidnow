import { BadgeCheck } from 'lucide-react'
import { UserAvatar } from '@/components/shared/UserAvatar'
import type { AuctionDetailSeller } from '@/types/ui/auction.ui'

interface SellerCardProps {
  readonly seller: AuctionDetailSeller | null
}

export function SellerCard({ seller }: SellerCardProps) {
  if (!seller) return null

  return (
    <section id="seller">
      <h2 className="font-display text-2xl font-semibold tracking-tight mb-3.5">Seller</h2>
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border p-5">
        <div className="flex items-center gap-4">
          <UserAvatar name={seller.name} avatarUrl={seller.avatarUrl} size="lg" />
          <div className="flex flex-col gap-1">
            <span className="font-display font-semibold text-lg leading-tight">{seller.name}</span>
            <span className="flex items-center gap-1 text-[13px] font-medium text-[var(--color-success-text)]">
              <BadgeCheck className="size-3.5" aria-hidden />
              Verified Seller
            </span>
          </div>
        </div>
        <button
          type="button"
          className="h-10 rounded-md border border-[var(--color-border-default)] px-[18px] text-[13px] font-medium transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)] hover:bg-muted"
        >
          View profile
        </button>
      </div>
    </section>
  )
}
