import { describe, expect, it } from 'vitest'
import { mapPublicProfile } from './user-profile.mapper'
import type { PublicUserProfileResponse } from '@/types/api/user-profile.api'

function dto(overrides: Partial<PublicUserProfileResponse> = {}): PublicUserProfileResponse {
  return {
    userId: 'u-1',
    displayName: 'Linh Tran',
    avatarUrl: 'https://cdn.example.com/linh.jpg',
    bio: 'Vintage cameras',
    city: 'Hanoi',
    country: 'Vietnam',
    memberSince: '2025-03-01T12:00:00',
    ...overrides,
  }
}

describe('mapPublicProfile', () => {
  it('maps every public field', () => {
    expect(mapPublicProfile(dto())).toEqual({
      id: 'u-1',
      name: 'Linh Tran',
      avatarUrl: 'https://cdn.example.com/linh.jpg',
      bio: 'Vintage cameras',
      location: 'Hanoi, Vietnam',
      memberSince: new Date('2025-03-01T12:00:00'),
    })
  })

  it('joins whichever half of the location is set', () => {
    expect(mapPublicProfile(dto({ city: null })).location).toBe('Vietnam')
    expect(mapPublicProfile(dto({ country: '  ' })).location).toBe('Hanoi')
  })

  it('leaves empty optional fields undefined', () => {
    const profile = mapPublicProfile(dto({ avatarUrl: null, bio: ' ', city: null, country: null }))

    expect(profile.avatarUrl).toBeUndefined()
    expect(profile.bio).toBeUndefined()
    expect(profile.location).toBeUndefined()
  })

  it('falls back to a generic name when the display name is blank', () => {
    expect(mapPublicProfile(dto({ displayName: null })).name).toBe('BidNow member')
    expect(mapPublicProfile(dto({ displayName: '   ' })).name).toBe('BidNow member')
  })
})
