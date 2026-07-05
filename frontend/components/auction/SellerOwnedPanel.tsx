'use client'

import Link     from 'next/link'
import { Tag }  from 'lucide-react'
import { Button } from '@/components/ui/button'

interface SellerOwnedPanelProps {
  auctionId: string
}

export function SellerOwnedPanel({ auctionId }: SellerOwnedPanelProps) {
  return (
    <div className="rounded-xl border bg-card overflow-hidden flex flex-col gap-4 px-[18px] py-[18px]">
      <div className="flex items-center gap-2">
        <Tag className="size-4 text-muted-foreground" />
        <span className="font-medium text-sm">Your auction</span>
      </div>
      <p className="text-xs text-muted-foreground">
        Sellers can&apos;t bid on their own auctions.
      </p>
      <Button
        variant="outline"
        size="lg"
        className="w-full"
        render={<Link href={`/seller/auctions/${auctionId}/manage`} />}
        nativeButton={false}
      >
        Manage auction →
      </Button>
    </div>
  )
}
