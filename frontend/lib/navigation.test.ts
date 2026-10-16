import { describe, expect, it } from 'vitest'
import { NAV_LINKS, ROUTES, isNavActive } from './navigation'

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
  it('lists Browse, How it works and FAQ, in that order', () => {
    expect(NAV_LINKS.map((l) => l.label)).toEqual(['Browse', 'How it works', 'FAQ'])
    expect(NAV_LINKS.map((l) => l.href)).toEqual([ROUTES.browse, ROUTES.howItWorks, ROUTES.faq])
  })

  it('has no standalone Live link', () => {
    expect(NAV_LINKS.map((l) => l.label)).not.toContain('Live')
  })

  it('leaves member-only pages to the account menu', () => {
    expect(NAV_LINKS.map((l) => l.href)).not.toContain(ROUTES.myBids)
  })
})
