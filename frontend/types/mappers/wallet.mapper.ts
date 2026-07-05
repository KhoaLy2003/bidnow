import type { WalletResponse, TransactionResponse, WalletTransactionType } from '@/types/api/wallet.api'
import type { WalletBalance, Transaction, TransactionType } from '@/types/ui/wallet.ui'

const TYPE_MAP: Record<WalletTransactionType, TransactionType> = {
  DEPOSIT:     'deposit',
  WITHDRAWAL:  'withdrawal',
  HOLD:        'bid_hold',
  HOLD_CANCEL: 'bid_release',
  PAYMENT:     'won_payment',
  REFUND:      'refund',
  FEE:         'fee',
  FORFEIT:     'forfeit',
}

export function mapWalletResponse(dto: WalletResponse): WalletBalance {
  return {
    available: dto.availableBalance,
    held:      dto.lockedBalance,
    total:     dto.totalBalance,
  }
}

export function mapTransactionResponse(dto: TransactionResponse): Transaction {
  const signedAmount = dto.availableBalanceAfter >= dto.availableBalanceBefore
    ? dto.amount
    : -dto.amount

  return {
    id:          dto.id,
    type:        TYPE_MAP[dto.type],
    amount:      signedAmount,
    description: dto.description,
    createdAt:   new Date(dto.createdAt),
  }
}
