import { ArrowUp, ArrowDown, RefreshCw, CircleDollarSign } from 'lucide-react'
import { formatCurrency, formatRelativeTime } from '@/lib/format'
import type { Transaction } from '@/types/wallet'
import { cn } from '@/lib/utils'

interface TransactionRowProps {
  transaction: Transaction
}

const TYPE_CONFIG: Record<
  Transaction['type'],
  { label: string; Icon: React.ComponentType<{ className?: string }>; positive: boolean }
> = {
  deposit:      { label: 'Deposit',        Icon: ArrowDown,       positive: true  },
  withdrawal:   { label: 'Withdrawal',     Icon: ArrowUp,         positive: false },
  bid_hold:     { label: 'Bid Hold',       Icon: CircleDollarSign, positive: false },
  bid_release:  { label: 'Bid Released',   Icon: RefreshCw,       positive: true  },
  won_payment:  { label: 'Won — Payment',  Icon: ArrowUp,         positive: false },
  refund:       { label: 'Refund',         Icon: ArrowDown,       positive: true  },
}

export function TransactionRow({ transaction }: TransactionRowProps) {
  const config = TYPE_CONFIG[transaction.type]
  const { Icon, positive, label } = config

  return (
    <div className="flex items-center gap-3 py-3">
      <div
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full',
          positive
            ? 'bg-[var(--color-success-subtle)] text-[var(--color-success-default)]'
            : 'bg-[var(--color-danger-subtle)] text-[var(--color-danger-default)]',
        )}
      >
        <Icon className="size-4" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium truncate">{transaction.description || label}</p>
        <p className="text-xs text-muted-foreground">{formatRelativeTime(transaction.createdAt)}</p>
      </div>

      <span
        className={cn(
          'font-mono font-medium text-sm shrink-0',
          positive
            ? 'text-[var(--color-wallet-positive)]'
            : 'text-[var(--color-wallet-negative)]',
        )}
      >
        {positive ? '+' : ''}
        {formatCurrency(Math.abs(transaction.amount))}
      </span>
    </div>
  )
}
