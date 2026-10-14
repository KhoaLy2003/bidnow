import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveImageUrl } from './image-utils'

describe('resolveImageUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it('returns undefined for empty values', () => {
    expect(resolveImageUrl(null)).toBeUndefined()
    expect(resolveImageUrl(undefined)).toBeUndefined()
    expect(resolveImageUrl('')).toBeUndefined()
  })

  it('passes full URLs through untouched', () => {
    expect(resolveImageUrl('https://cdn.example.com/uploads/a.jpg')).toBe('https://cdn.example.com/uploads/a.jpg')
  })

  it('prefixes bare keys with the CDN url', () => {
    vi.stubEnv('NEXT_PUBLIC_CDN_URL', 'https://d1234abcd.cloudfront.net')
    expect(resolveImageUrl('uploads/u/a.jpg')).toBe('https://d1234abcd.cloudfront.net/uploads/u/a.jpg')
  })

  it('tolerates a trailing slash on the CDN url', () => {
    vi.stubEnv('NEXT_PUBLIC_CDN_URL', 'https://d1234abcd.cloudfront.net/')
    expect(resolveImageUrl('uploads/u/a.jpg')).toBe('https://d1234abcd.cloudfront.net/uploads/u/a.jpg')
  })

  it('returns undefined and logs when the CDN url is missing', () => {
    vi.stubEnv('NEXT_PUBLIC_CDN_URL', '')
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(resolveImageUrl('uploads/u/a.jpg')).toBeUndefined()
    expect(error).toHaveBeenCalled()
  })
})
