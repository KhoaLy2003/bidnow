export interface WalletResponse {
  totalBalance: number;
  availableBalance: number;
  lockedBalance: number;
  currency: string;
  status: string;
}

export interface DepositRequest {
  amount: number;
}

export interface DepositResponse {
  transactionId: string;
  newBalance: number;
  status: string;
}

export type WalletTransactionType =
  | 'DEPOSIT'
  | 'HOLD'
  | 'HOLD_CANCEL'
  | 'PAYMENT'
  | 'FORFEIT'
  | 'REFUND'
  | 'FEE'
  | 'WITHDRAWAL';

export interface TransactionResponse {
  id: string;
  type: WalletTransactionType;
  amount: number;
  availableBalanceBefore: number;
  availableBalanceAfter: number;
  description: string;
  status: string;
  createdAt: string;
}
