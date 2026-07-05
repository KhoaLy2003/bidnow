'use client'

import { useState } from 'react'
import { toast } from 'sonner'
import { CircleDollarSign } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { CurrencyInput } from '@/components/ui/currency-input'
import { TransactionRow } from './TransactionRow'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'
import { getErrorMessage } from '@/lib/utils'

export function WalletPanel() {
  const { available, held, transactions, isLoading, depositFunds } = useWallet()
  const [isDialogOpen, setIsDialogOpen] = useState(false)
  const [amount, setAmount] = useState(0)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleDeposit = async () => {
    if (amount <= 0) return
    setIsSubmitting(true)
    try {
      await depositFunds(amount)
      toast.success('Deposit successful')
      setIsDialogOpen(false)
      setAmount(0)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to deposit funds'))
    } finally {
      setIsSubmitting(false)
    }
  }

  const showSkeleton = isLoading && transactions.length === 0 && available === 0

  return (
    <>
      {/* Balance card */}
      <div className="mx-4 rounded-xl bg-[var(--color-wallet-bg)] p-4 flex flex-col gap-1">
        <p className="text-xs text-muted-foreground uppercase font-medium">
          Available balance
        </p>
        {showSkeleton ? (
          <Skeleton className="h-8 w-32" />
        ) : (
          <p className="font-mono font-medium text-[length:var(--font-size-price-md)] text-[var(--color-wallet-text)]">
            {formatCurrency(available)}
          </p>
        )}
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
          onClick={() => setIsDialogOpen(true)}
        >
          <CircleDollarSign className="size-4" />
          Add Funds
        </Button>
      </div>

      <Separator />

      {/* Transaction history */}
      <div className="px-4 flex flex-col gap-2 flex-1 min-h-0">
        <p className="text-sm font-medium">Recent transactions</p>
        {showSkeleton ? (
          <div className="flex flex-col gap-3 py-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : transactions.length === 0 ? (
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

      <Dialog
        open={isDialogOpen}
        onOpenChange={(open) => {
          if (!isSubmitting) setIsDialogOpen(open)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add funds</DialogTitle>
            <DialogDescription>
              Simulated deposit — no real payment is processed.
            </DialogDescription>
          </DialogHeader>

          <CurrencyInput value={amount} onChange={setAmount} placeholder="0.00" />

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsDialogOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              variant="brand"
              onClick={handleDeposit}
              disabled={isSubmitting || amount <= 0}
            >
              Deposit {amount > 0 ? formatCurrency(amount) : ''}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
