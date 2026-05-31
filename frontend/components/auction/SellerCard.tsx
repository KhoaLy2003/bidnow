import { UserAvatar } from '@/components/shared/UserAvatar'
import type { AuctionSeller } from '@/types/ui/auction.ui'

interface SellerCardProps {
  seller: AuctionSeller
}

export function SellerCard({ seller }: SellerCardProps) {
  const fullStars = Math.round(seller.rating)

  return (
    <section>
      <p className="text-[10.5px] font-mono uppercase tracking-widest text-muted-foreground mb-3">
        Seller
      </p>
      <div className="flex items-center justify-between gap-4 border border-[var(--color-border-default)] rounded-lg p-4">
        <div className="flex items-center gap-3">
          <UserAvatar name={seller.name} avatarUrl={seller.avatarUrl} size="lg" />
          <div className="flex flex-col gap-0.5">
            <span className="font-display font-medium text-base leading-tight">{seller.name}</span>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="text-amber-400 text-xs tracking-tight">
                {'★'.repeat(fullStars)}
                <span className="text-muted-foreground/40">{'★'.repeat(5 - fullStars)}</span>
              </span>
              <span className="font-mono">{seller.rating.toFixed(1)}</span>
              <span>·</span>
              <span><span className="font-mono">{seller.totalAuctions}</span> auctions</span>
            </div>
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
