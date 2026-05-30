import { BadgeCheck } from 'lucide-react'
import { UserAvatar } from '@/components/shared/UserAvatar'
import type { AuctionDetailSeller } from '@/types/ui/auction.ui'

interface SellerCardProps {
  seller: AuctionDetailSeller | null
}

export function SellerCard({ seller }: SellerCardProps) {
  if (!seller) return null

  return (
    <section>
      <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
        Seller
      </p>
      <div className="flex items-center justify-between gap-4 border border-[var(--color-border-default)] rounded-lg p-4">
        <div className="flex items-center gap-3">
          <UserAvatar name={seller.name} avatarUrl={seller.avatarUrl} size="lg" />
          <div className="flex flex-col gap-1">
            <span className="font-display font-medium text-base leading-tight">{seller.name}</span>
            <span className="flex items-center gap-1 text-[11px] text-[var(--color-success-text)]">
              <BadgeCheck className="size-3.5" />
              Verified Seller
            </span>
          </div>
        </div>
        <button
          type="button"
          className="text-xs border border-[var(--color-border-default)] rounded px-3 h-8 text-muted-foreground hover:text-foreground hover:border-foreground transition-colors duration-[var(--duration-tesla)] ease-[var(--ease-tesla)]"
        >
          View profile
        </button>
      </div>
    </section>
  )
}
