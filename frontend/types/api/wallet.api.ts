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

export interface PendingPaymentResponse {
  auctionId: string;
  totalAmount: number;
  depositApplied: number;
  remaining: number;
  fundsHeld: boolean;
  /** Server LocalDateTime without a timezone: display-only, never parse in the browser. */
  deadline: string;
  hoursLeft: number;
  expired: boolean;
}

export interface ConfirmPaymentRequest {
  auctionId: string;
}

export interface ConfirmPaymentResponse {
  auctionId: string;
  amountPaid: number;
  depositApplied: number;
  remainingPaid: number;
  availableBalance: number;
  lockedBalance: number;
}

export type PaymentHoldStatus = 'PENDING_PAYMENT' | 'COMPLETED' | 'CANCELLED' | 'FORFEITED';

export interface PaymentStatusResponse {
  auctionId: string;
  status: PaymentHoldStatus;
  totalAmount: number;
}
