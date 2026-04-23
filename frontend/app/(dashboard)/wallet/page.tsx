'use client'

import type { Metadata } from 'next'
import { CircleDollarSign } from 'lucide-react'
import { WalletPanel } from '@/components/wallet/WalletPanel'

export default function WalletPage() {
  return (
    <div className="flex flex-col gap-6 max-w-lg">
      <div className="flex items-center gap-2">
        <CircleDollarSign className="size-5 text-[var(--color-text-brand)]" />
        <h1 className="font-display font-bold text-[length:var(--font-size-2xl)]">Wallet</h1>
      </div>
      <div className="rounded-xl border bg-card flex flex-col gap-4 py-4">
        <WalletPanel />
      </div>
    </div>
  )
}
