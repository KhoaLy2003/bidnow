import { describe, expect, it } from 'vitest'
import { AUTHED_NAV_LINKS, GUEST_NAV_LINKS, ROUTES, isNavActive } from './navigation'

describe('isNavActive', () => {
  it('matches the exact path', () => {
    expect(isNavActive('/auctions', '/auctions')).toBe(true)
  })

  it('matches nested paths on a segment boundary', () => {
    expect(isNavActive('/auctions/42', '/auctions')).toBe(true)
  })

  it('does not match a sibling that only shares a prefix', () => {
    expect(isNavActive('/auctions-archive', '/auctions')).toBe(false)
  })

  it('only matches home exactly', () => {
    expect(isNavActive('/', '/')).toBe(true)
    expect(isNavActive('/auctions', '/')).toBe(false)
  })

  it('is inactive when the pathname is unknown', () => {
    expect(isNavActive(null, '/auctions')).toBe(false)
  })
})

describe('nav links', () => {
  it('has no standalone Live link', () => {
    const labels = [...AUTHED_NAV_LINKS, ...GUEST_NAV_LINKS].map((l) => l.label)
    expect(labels).not.toContain('Live')
  })

  it('shows My bids only to signed-in users', () => {
    expect(AUTHED_NAV_LINKS.map((l) => l.href)).toContain(ROUTES.myBids)
    expect(GUEST_NAV_LINKS.map((l) => l.href)).not.toContain(ROUTES.myBids)
  })
})
