'use client'

import Link from 'next/link'
import { CircleDollarSign } from 'lucide-react'
import { WalletPanel } from '@/components/wallet/WalletPanel'
import { ROUTES } from '@/lib/navigation'

export default function WalletPage() {
  return (
    <div className="flex flex-col gap-6 max-w-lg">
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <CircleDollarSign className="size-5 text-[var(--color-text-brand)]" />
          <h1 className="font-display font-medium text-[length:var(--font-size-2xl)]">Wallet</h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Auctions you won and still need to pay for are in{' '}
          <Link href={ROUTES.purchaseHistory} className="text-[var(--color-text-brand)] underline-offset-2 hover:underline">
            Purchase history
          </Link>
          .
        </p>
      </div>
      <div className="rounded-xl border bg-card flex flex-col gap-4 py-4">
        <WalletPanel />
      </div>
    </div>
  )
}
