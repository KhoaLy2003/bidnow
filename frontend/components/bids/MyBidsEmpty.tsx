import Link from 'next/link'
import { Gavel } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { MyBidsTab } from '@/types/ui/bid.ui'

const COPY: Record<MyBidsTab, { title: string; body: string }> = {
  all:     { title: "You're not bidding on anything yet", body: "Auctions you place a bid on will show up here while they're live." },
  winning: { title: "You're not winning anything right now", body: 'Bid again on an auction to take the lead.' },
  outbid:  { title: 'Nothing to catch up on', body: "You're winning everything you've bid on." },
}

interface MyBidsEmptyProps {
  tab: MyBidsTab
}

export function MyBidsEmpty({ tab }: MyBidsEmptyProps) {
  const { title, body } = COPY[tab]

  return (
    <div className="flex flex-col items-center rounded-xl border px-6 py-14 text-center">
      <Gavel className="size-10 text-muted-foreground" strokeWidth={1.5} aria-hidden />
      <h2 className="mt-4 text-lg font-medium">{title}</h2>
      <p className="mt-1.5 max-w-sm text-sm text-muted-foreground">{body}</p>
      {tab === 'all' && (
        <Link href="/auctions" className={cn(buttonVariants({ variant: 'brand', size: 'xl' }), 'mt-6')}>
          Browse auctions
        </Link>
      )}
    </div>
  )
}
