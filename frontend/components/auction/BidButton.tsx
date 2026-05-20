'use client'

import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BidButtonProps {
  amount?:    number         // cents — shows "Confirm $X.XX" when provided
  isLoading?: boolean
  disabled?:  boolean
  onClick?:   () => void
  className?: string
}

export function BidButton({
  amount, isLoading, disabled, onClick, className,
}: BidButtonProps) {
  const label = amount
    ? `Confirm ${formatCurrency(amount)}`
    : 'Place Bid'

  return (
    <Button
      variant="brand"
      className={cn('h-12 w-full font-medium text-base', className)}
      disabled={disabled || isLoading}
      onClick={onClick}
    >
      {isLoading && <Loader2 className="size-4 animate-spin" />}
      {label}
    </Button>
  )
}
