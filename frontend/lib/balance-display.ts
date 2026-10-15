import { formatCurrency } from '@/lib/format'

export const MASKED_BALANCE = '••••••'

/** Wallet amounts stay masked until the user explicitly reveals them. */
export function displayBalance(dollars: number, visible: boolean): string {
  return visible ? formatCurrency(dollars) : MASKED_BALANCE
}
