import { describe, expect, it } from 'vitest'
import { mapPendingPaymentResponse } from './wallet.mapper'
import type { PendingPaymentResponse } from '@/types/api/wallet.api'

describe('mapPendingPaymentResponse', () => {
  it('maps the amounts and flags and drops the timezone-less deadline', () => {
    const dto: PendingPaymentResponse = {
      auctionId: 'a-1',
      totalAmount: 500,
      depositApplied: 50,
      remaining: 450,
      fundsHeld: true,
      deadline: '2026-10-17T10:00:00',
      hoursLeft: 47,
      expired: false,
    }

    expect(mapPendingPaymentResponse(dto)).toEqual({
      auctionId: 'a-1',
      totalAmount: 500,
      depositApplied: 50,
      remaining: 450,
      fundsHeld: true,
      hoursLeft: 47,
      expired: false,
    })
  })
})
