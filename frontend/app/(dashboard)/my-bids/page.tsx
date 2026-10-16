import type { Metadata } from 'next'
import { MyBidsClient } from '@/components/bids/MyBidsClient'

export const metadata: Metadata = { title: 'My Bids' }

export default function MyBidsPage() {
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="font-display font-medium text-[length:var(--font-size-3xl)] tracking-tight">My Bids</h1>
        <p className="mt-2 text-sm text-muted-foreground">Active auctions you&apos;ve bid on</p>
      </div>
      <MyBidsClient />
    </div>
  )
}
