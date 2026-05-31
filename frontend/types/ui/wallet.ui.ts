export type TransactionType =
  | 'deposit'
  | 'withdrawal'
  | 'bid_hold'
  | 'bid_release'
  | 'won_payment'
  | 'refund'

export interface Transaction {
  id:          string
  userId:      string
  type:        TransactionType
  amount:      number        // cents — positive = credit, negative = debit
  description: string
  createdAt:   Date
  auctionId?:  string
}

export interface WalletBalance {
  available: number          // cents — spendable
  held:      number          // cents — reserved for active bids
  total:     number          // cents — available + held
}
