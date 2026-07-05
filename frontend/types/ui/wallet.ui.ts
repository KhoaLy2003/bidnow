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
