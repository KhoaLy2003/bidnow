'use client'

import { CircleDollarSign } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { ScrollArea } from '@/components/ui/scroll-area'
import { TransactionRow } from './TransactionRow'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'

export function WalletPanel() {
  const { available, held, transactions, deposit } = useWallet()

  return (
    <>
      {/* Balance card */}
      <div className="mx-4 rounded-xl bg-[var(--color-wallet-bg)] p-4 flex flex-col gap-1">
        <p className="text-xs text-muted-foreground uppercase font-medium">
          Available balance
        </p>
        <p className="font-mono font-medium text-[length:var(--font-size-price-md)] text-[var(--color-wallet-text)]">
          {formatCurrency(available)}
        </p>
        {held > 0 && (
          <p className="text-xs text-muted-foreground">
            {formatCurrency(held)} held for active bids
          </p>
        )}
      </div>

      <div className="px-4">
        <Button
          variant="brand"
          className="w-full"
          onClick={() => deposit(10_000)}   // placeholder +$100
        >
          <CircleDollarSign className="size-4" />
          Add Funds
        </Button>
      </div>

      <Separator />

      {/* Transaction history */}
      <div className="px-4 flex flex-col gap-2 flex-1 min-h-0">
        <p className="text-sm font-medium">Recent transactions</p>
        {transactions.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4">No transactions yet.</p>
        ) : (
          <ScrollArea className="flex-1">
            {transactions.map((tx, i) => (
              <div key={tx.id}>
                {i > 0 && <Separator />}
                <TransactionRow transaction={tx} />
              </div>
            ))}
          </ScrollArea>
        )}
      </div>
    </>
  )
}
