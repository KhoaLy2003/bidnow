import { create } from 'zustand'

interface BalanceVisibilityState {
  visible: boolean
  toggle:  () => void
  hide:    () => void
}

/**
 * Whether wallet amounts are shown in the header. Deliberately not persisted:
 * the balance is hidden again on every page load and on sign out.
 */
export const useBalanceVisibilityStore = create<BalanceVisibilityState>((set) => ({
  visible: false,
  toggle:  () => set((s) => ({ visible: !s.visible })),
  hide:    () => set({ visible: false }),
}))
