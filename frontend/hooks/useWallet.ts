'use client'

import { useWalletStore } from '@/store/walletStore'

export function useWallet() {
  return useWalletStore()
}
