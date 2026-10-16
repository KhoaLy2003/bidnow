import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { userService } from './user.service'

const mockFetch = vi.mocked(apiFetch)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('userService.getPublicProfile', () => {
  beforeEach(() => mockFetch.mockReset())

  it('fetches the public profile uncached and maps it', async () => {
    mockFetch.mockResolvedValue(json({
      status: 200,
      data: {
        userId: 'u-1', displayName: 'Linh Tran', avatarUrl: null, bio: null,
        city: 'Hanoi', country: 'Vietnam', memberSince: '2025-03-01T12:00:00',
      },
    }))

    const profile = await userService.getPublicProfile('u-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/users/u-1/public-profile', { cache: 'no-store' })
    expect(profile).toMatchObject({ id: 'u-1', name: 'Linh Tran', location: 'Hanoi, Vietnam' })
  })

  it('resolves null for an unknown or malformed id', async () => {
    mockFetch.mockResolvedValueOnce(json({ message: 'nope' }, 404))
    mockFetch.mockResolvedValueOnce(json({ message: 'bad' }, 400))

    expect(await userService.getPublicProfile('missing')).toBeNull()
    expect(await userService.getPublicProfile('not-a-uuid')).toBeNull()
  })

  it('throws on a server error', async () => {
    mockFetch.mockResolvedValue(json({ message: 'boom' }, 500))

    await expect(userService.getPublicProfile('u-1')).rejects.toThrow('Failed to load profile (500)')
  })
})
