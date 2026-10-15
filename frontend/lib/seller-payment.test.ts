import { describe, expect, it } from 'vitest'
import { deriveSellerPaymentState, formatPaymentTimeLeft } from './seller-payment'
import type { PaymentStatusLookup } from '@/types/ui/wallet.ui'

const NOW = Date.parse('2026-10-15T10:00:00Z')
const at = (offsetMs: number) => new Date(NOW + offsetMs)
const MIN = 60_000
const HOUR = 60 * MIN
const DAY = 24 * HOUR

describe('formatPaymentTimeLeft', () => {
  it('is null without a deadline', () => {
    expect(formatPaymentTimeLeft(undefined, NOW)).toBeNull()
  })

  it('is null once the deadline has passed or is exactly now', () => {
    expect(formatPaymentTimeLeft(at(-1), NOW)).toBeNull()
    expect(formatPaymentTimeLeft(at(0), NOW)).toBeNull()
  })

  it('shows days and hours from a day up', () => {
    expect(formatPaymentTimeLeft(at(DAY), NOW)).toBe('1d 0h')
    expect(formatPaymentTimeLeft(at(DAY + 4 * HOUR + 59 * MIN), NOW)).toBe('1d 4h')
    expect(formatPaymentTimeLeft(at(2 * DAY - 1), NOW)).toBe('1d 23h')
  })

  it('shows hours and minutes from an hour up', () => {
    expect(formatPaymentTimeLeft(at(HOUR), NOW)).toBe('1h 0m')
    expect(formatPaymentTimeLeft(at(5 * HOUR + 12 * MIN + 30_000), NOW)).toBe('5h 12m')
  })

  it('shows minutes under an hour and <1m under a minute', () => {
    expect(formatPaymentTimeLeft(at(42 * MIN), NOW)).toBe('42m')
    expect(formatPaymentTimeLeft(at(59 * MIN + 59_000), NOW)).toBe('59m')
    expect(formatPaymentTimeLeft(at(59_000), NOW)).toBe('<1m')
  })
})

describe('deriveSellerPaymentState', () => {
  const found = (status: 'PENDING_PAYMENT' | 'COMPLETED' | 'CANCELLED' | 'FORFEITED'): PaymentStatusLookup =>
    ({ kind: 'found', status })

  it('maps each wallet hold status', () => {
    expect(deriveSellerPaymentState({ lookup: found('COMPLETED'), now: NOW })).toBe('paid')
    expect(deriveSellerPaymentState({ lookup: found('FORFEITED'), now: NOW })).toBe('forfeited')
    expect(deriveSellerPaymentState({ lookup: found('CANCELLED'), now: NOW })).toBe('cancelled')
  })

  it('is awaiting while the hold is pending and the deadline is ahead or unknown', () => {
    expect(deriveSellerPaymentState({ lookup: found('PENDING_PAYMENT'), paymentDeadline: at(HOUR), now: NOW })).toBe('awaiting')
    expect(deriveSellerPaymentState({ lookup: found('PENDING_PAYMENT'), now: NOW })).toBe('awaiting')
  })

  it('is overdue while the hold is pending but the deadline has passed (forfeit not yet processed)', () => {
    expect(deriveSellerPaymentState({ lookup: found('PENDING_PAYMENT'), paymentDeadline: at(-MIN), now: NOW })).toBe('overdue')
  })

  it('trusts the wallet status over the auction copy', () => {
    expect(deriveSellerPaymentState({ lookup: found('FORFEITED'), winnerPaidAt: at(-HOUR), now: NOW })).toBe('forfeited')
  })

  it('falls back to winnerPaidAt when the lookup has no hold or failed', () => {
    expect(deriveSellerPaymentState({ lookup: { kind: 'not-found' }, winnerPaidAt: at(-HOUR), now: NOW })).toBe('paid')
    expect(deriveSellerPaymentState({ lookup: { kind: 'failed' }, winnerPaidAt: at(-HOUR), now: NOW })).toBe('paid')
  })

  it('reports loading, error and none otherwise', () => {
    expect(deriveSellerPaymentState({ lookup: { kind: 'idle' }, now: NOW })).toBe('loading')
    expect(deriveSellerPaymentState({ lookup: { kind: 'loading' }, now: NOW })).toBe('loading')
    expect(deriveSellerPaymentState({ lookup: { kind: 'failed' }, now: NOW })).toBe('error')
    expect(deriveSellerPaymentState({ lookup: { kind: 'not-found' }, now: NOW })).toBe('none')
  })
})
