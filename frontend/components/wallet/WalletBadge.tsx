'use client'

import { Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetTrigger, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { WalletPanel } from './WalletPanel'
import { useWallet } from '@/hooks/useWallet'
import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'

export function WalletBadge() {
  const { available, isLow, isLoading, transactions } = useWallet()
  const showSkeleton = isLoading && transactions.length === 0 && available === 0

  if (showSkeleton) {
    return <Skeleton className="h-7 w-20 rounded-full" />
  }

  return (
    <Sheet>
      <SheetTrigger
        render={
          <Button
            variant="ghost"
            className={cn(
              'gap-1.5 rounded-full font-mono font-medium',
              isLow && 'text-[var(--color-warning-text)] bg-[var(--color-warning-subtle)] hover:bg-[var(--color-warning-subtle)]/80',
            )}
          />
        }
      >
        <Wallet className="size-4" />
        {formatCurrency(available)}
      </SheetTrigger>
      <SheetContent side="right" className="w-full sm:max-w-sm p-0 gap-4 flex flex-col py-4">
        <SheetHeader>
          <SheetTitle>Wallet</SheetTitle>
          <SheetDescription className="mt-4">Manage your auction balance</SheetDescription>
        </SheetHeader>
        <WalletPanel />
      </SheetContent>
    </Sheet>
  )
}
