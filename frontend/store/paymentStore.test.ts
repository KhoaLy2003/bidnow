import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/wallet.service', () => ({
  walletService: { getPendingPayments: vi.fn(), getPaymentStatus: vi.fn() },
}))

import { walletService } from '@/services/wallet.service'
import type { PaymentHoldStatus, PaymentStatusResponse, PendingPaymentResponse } from '@/types/api/wallet.api'
import { usePaymentStore } from './paymentStore'

const service = vi.mocked(walletService)

function dto(auctionId: string, over: Partial<PendingPaymentResponse> = {}): PendingPaymentResponse {
  return {
    auctionId, totalAmount: 500, depositApplied: 100, remaining: 400, fundsHeld: false,
    deadline: '2026-10-17T10:00:00', hoursLeft: 47, expired: false, ...over,
  }
}

const store = () => usePaymentStore.getState()
const ids = () => store().holds.map((h) => h.auctionId)

describe('paymentStore', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    store().reset()
  })

  it('load fetches and maps the holds', async () => {
    service.getPendingPayments.mockResolvedValue([dto('a-1'), dto('a-2')])

    await store().load()

    expect(ids()).toEqual(['a-1', 'a-2'])
    expect(store().holds[0]).not.toHaveProperty('deadline')
    expect(store().hasFetched).toBe(true)
    expect(store().loadFailed).toBe(false)
    expect(store().isLoading).toBe(false)
  })

  it('a failed first load flags loadFailed and leaves hasFetched false', async () => {
    service.getPendingPayments.mockRejectedValue(new Error('down'))

    await store().load()

    expect(store().loadFailed).toBe(true)
    expect(store().hasFetched).toBe(false)
    expect(store().isLoading).toBe(false)
  })

  it('a failed refresh keeps the holds already loaded', async () => {
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1')])
    await store().load()
    service.getPendingPayments.mockRejectedValueOnce(new Error('down'))

    await store().load()

    expect(ids()).toEqual(['a-1'])
    expect(store().hasFetched).toBe(true)
  })

  it('a later success clears loadFailed', async () => {
    service.getPendingPayments.mockRejectedValueOnce(new Error('down'))
    await store().load()
    service.getPendingPayments.mockResolvedValueOnce([])

    await store().load()

    expect(store().loadFailed).toBe(false)
    expect(store().hasFetched).toBe(true)
  })

  it('ignores a superseded load', async () => {
    let resolveFirst!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveFirst = r }))
      .mockResolvedValueOnce([dto('new')])

    const first = store().load()
    await store().load()
    resolveFirst([dto('old')])
    await first

    expect(ids()).toEqual(['new'])
  })

  it('markPaid removes the hold and records the auction as paid', async () => {
    service.getPendingPayments.mockResolvedValue([dto('a-1'), dto('a-2')])
    await store().load()

    store().markPaid('a-1')

    expect(ids()).toEqual(['a-2'])
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('a stale response that still contains a paid hold does not resurrect it', async () => {
    let resolveSlow!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveSlow = r }))

    const slow = store().load()
    store().markPaid('a-1')
    resolveSlow([dto('a-1'), dto('a-2')])
    await slow

    expect(ids()).toEqual(['a-2'])
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('load keeps the paid ids across refetches', async () => {
    service.getPendingPayments.mockResolvedValue([])
    await store().load()
    store().markPaid('a-1')

    await store().load()

    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('refresh does nothing before the first load', async () => {
    await store().refresh()

    expect(service.getPendingPayments).not.toHaveBeenCalled()
  })

  it('refresh reloads once the store has been loaded', async () => {
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1')])
    await store().load()
    service.getPendingPayments.mockResolvedValueOnce([dto('a-1'), dto('a-2')])

    await store().refresh()

    expect(ids()).toEqual(['a-1', 'a-2'])
  })

  it('reset clears everything and ignores an in-flight load', async () => {
    let resolveSlow!: (v: PendingPaymentResponse[]) => void
    service.getPendingPayments
      .mockReturnValueOnce(new Promise<PendingPaymentResponse[]>((r) => { resolveSlow = r }))
    const slow = store().load()
    store().markPaid('a-9')

    store().reset()
    resolveSlow([dto('previous-user')])
    await slow

    expect(store().holds).toEqual([])
    expect(store().paidIds.size).toBe(0)
    expect(store().hasFetched).toBe(false)
    expect(store().isLoading).toBe(false)
  })
})

function statusDto(status: PaymentHoldStatus, auctionId = 'a-1'): PaymentStatusResponse {
  return { auctionId, status, totalAmount: 500 }
}

describe('paymentStore.loadStatus', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    store().reset()
  })

  it('records a found status', async () => {
    service.getPaymentStatus.mockResolvedValue(statusDto('FORFEITED'))

    await store().loadStatus('a-1')

    expect(service.getPaymentStatus).toHaveBeenCalledWith('a-1')
    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'FORFEITED' })
  })

  it('records not-found when there is no hold for the caller', async () => {
    service.getPaymentStatus.mockResolvedValue(null)

    await store().loadStatus('a-1')

    expect(store().statuses['a-1']).toEqual({ kind: 'not-found' })
  })

  it('records failed when the request throws', async () => {
    service.getPaymentStatus.mockRejectedValue(new Error('down'))

    await store().loadStatus('a-1')

    expect(store().statuses['a-1']).toEqual({ kind: 'failed' })
  })

  it('shows loading while the request is in flight', async () => {
    let resolve!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolve = r }))

    const pending = store().loadStatus('a-1')
    expect(store().statuses['a-1']).toEqual({ kind: 'loading' })
    resolve(statusDto('COMPLETED'))
    await pending

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
  })

  it('keeps a separate lookup per auction', async () => {
    service.getPaymentStatus
      .mockResolvedValueOnce(statusDto('COMPLETED', 'a-1'))
      .mockResolvedValueOnce(null)

    await store().loadStatus('a-1')
    await store().loadStatus('a-2')

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
    expect(store().statuses['a-2']).toEqual({ kind: 'not-found' })
  })

  it('ignores a superseded lookup for the same auction', async () => {
    let resolveFirst!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus
      .mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveFirst = r }))
      .mockResolvedValueOnce(statusDto('COMPLETED'))

    const first = store().loadStatus('a-1')
    await store().loadStatus('a-1')
    resolveFirst(null)
    await first

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
  })

  it('markPaid records COMPLETED and ignores a lookup that started before it', async () => {
    let resolveSlow!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveSlow = r }))

    const slow = store().loadStatus('a-1')
    store().markPaid('a-1')
    resolveSlow(null)
    await slow

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('reset clears the lookups and ignores a lookup still in flight', async () => {
    let resolveSlow!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveSlow = r }))
    const slow = store().loadStatus('a-1')

    store().reset()
    resolveSlow(statusDto('FORFEITED'))
    await slow

    expect(store().statuses).toEqual({})
  })
})
