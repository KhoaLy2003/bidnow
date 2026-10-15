import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type { ApiErrorResponse } from '@/types/api/bid.api'
import type {
  WalletResponse,
  DepositResponse,
  TransactionResponse,
  PendingPaymentResponse,
  ConfirmPaymentRequest,
  ConfirmPaymentResponse,
} from '@/types/api/wallet.api'

/** A non-2xx payment API response; `body` is the backend ErrorResponse when it was JSON. */
export class PaymentRequestError extends Error {
  constructor(readonly status: number, readonly body: ApiErrorResponse | null) {
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'PaymentRequestError'
  }
}

async function toPaymentRequestError(response: Response): Promise<PaymentRequestError> {
  let body: ApiErrorResponse | null = null
  try {
    body = (await response.json()) as ApiErrorResponse
  } catch {
    body = null
  }
  return new PaymentRequestError(response.status, body)
}

export const walletService = {
  async getWallet(): Promise<ApiResponse<WalletResponse>> {
    const response = await apiFetch('/api/v1/wallets')
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async deposit(amount: number): Promise<ApiResponse<DepositResponse>> {
    const response = await apiFetch('/api/v1/wallets/deposit', {
      method: 'POST',
      body: JSON.stringify({ amount }),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async getTransactions(
    params: { page?: number; size?: number } = {}
  ): Promise<ApiResponse<PageResponse<TransactionResponse>>> {
    const query = new URLSearchParams()
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 10))
    const response = await apiFetch(`/api/v1/wallets/transactions?${query}`)
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  /** The caller's unpaid winner holds (PENDING_PAYMENT only). */
  async getPendingPayments(): Promise<PendingPaymentResponse[]> {
    const response = await apiFetch('/api/v1/wallets/payments/pending')
    if (!response.ok) throw await toPaymentRequestError(response)
    const body: ApiResponse<PendingPaymentResponse[]> = await response.json()
    return body.data
  },

  /** Settles the caller's hold for an auction. Throws PaymentRequestError on any non-2xx. */
  async confirmPayment(auctionId: string): Promise<ConfirmPaymentResponse> {
    const request: ConfirmPaymentRequest = { auctionId }
    const response = await apiFetch('/api/v1/wallets/payments/confirm', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    if (!response.ok) throw await toPaymentRequestError(response)
    const body: ApiResponse<ConfirmPaymentResponse> = await response.json()
    return body.data
  },
}
