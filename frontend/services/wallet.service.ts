import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type { WalletResponse, DepositResponse, TransactionResponse } from '@/types/api/wallet.api'

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
}
