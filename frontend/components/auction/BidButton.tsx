'use client'

import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCurrency } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BidButtonProps {
  amount?:    number         // dollars — shows "Place bid · $X.XX" when provided
  isLoading?: boolean
  disabled?:  boolean
  onClick?:   () => void
  type?:      'button' | 'submit'
  className?: string
}

export function BidButton({
  amount, isLoading, disabled, onClick, type, className,
}: BidButtonProps) {
  const label = amount
    ? `Place bid · ${formatCurrency(amount)}`
    : 'Place bid'

  return (
    <Button
      variant="brand"
      type={type}
      className={cn('h-[54px] w-full rounded-xl text-base font-semibold', className)}
      disabled={disabled || isLoading}
      onClick={onClick}
    >
      {isLoading && <Loader2 className="size-4 animate-spin" />}
      {label}
    </Button>
  )
}
