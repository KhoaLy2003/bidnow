export type TransactionType =
  | 'deposit'
  | 'withdrawal'
  | 'bid_hold'
  | 'bid_release'
  | 'won_payment'
  | 'refund'
  | 'fee'
  | 'forfeit'

export interface Transaction {
  id:          string
  userId?:     string
  type:        TransactionType
  amount:      number        // dollars — positive = credit, negative = debit
  description: string
  createdAt:   Date
  auctionId?:  string
}

export interface WalletBalance {
  available: number          // dollars — spendable
  held:      number          // dollars — reserved for active bids
  total:     number          // dollars — available + held
}

/** A winner's unpaid payment hold. All amounts are dollars. */
export interface PendingPayment {
  auctionId:      string
  totalAmount:    number   // winning price
  depositApplied: number   // auction deposit counted toward the price
  remaining:      number   // totalAmount − depositApplied
  fundsHeld:      boolean  // remaining is already moved to locked in the wallet
  hoursLeft:      number   // server-computed, floored; 0 means under one hour
  expired:        boolean  // server-computed
}
