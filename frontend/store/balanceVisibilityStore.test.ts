import { beforeEach, describe, expect, it } from 'vitest'
import { useBalanceVisibilityStore } from './balanceVisibilityStore'
import { displayBalance, MASKED_BALANCE } from '@/lib/balance-display'

describe('balanceVisibilityStore', () => {
  beforeEach(() => useBalanceVisibilityStore.getState().hide())

  it('starts hidden', () => {
    expect(useBalanceVisibilityStore.getState().visible).toBe(false)
  })

  it('toggles visibility', () => {
    useBalanceVisibilityStore.getState().toggle()
    expect(useBalanceVisibilityStore.getState().visible).toBe(true)
    useBalanceVisibilityStore.getState().toggle()
    expect(useBalanceVisibilityStore.getState().visible).toBe(false)
  })

  it('hide() re-masks a revealed balance', () => {
    useBalanceVisibilityStore.getState().toggle()
    useBalanceVisibilityStore.getState().hide()
    expect(useBalanceVisibilityStore.getState().visible).toBe(false)
  })
})

describe('displayBalance', () => {
  it('masks the amount while hidden', () => {
    expect(displayBalance(1240.5, false)).toBe(MASKED_BALANCE)
  })

  it('formats the amount once revealed', () => {
    expect(displayBalance(1240.5, true)).toBe('$1,240.50')
  })
})
