export const ROUTES = {
  home:            '/',
  browse:          '/auctions',
  howItWorks:      '/how-it-works',
  myBids:          '/my-bids',
  myAuctions:      '/seller/auctions',
  sell:            '/seller/auctions/new',
  wallet:          '/wallet',
  purchaseHistory: '/purchases',
  profile:         '/profile',
  settings:        '/settings',
  notifications:   '/notifications',
  login:           '/login',
  register:        '/register',
} as const

export interface NavLinkItem {
  label: string
  href:  string
}

export const AUTHED_NAV_LINKS: readonly NavLinkItem[] = [
  { label: 'Browse',  href: ROUTES.browse },
  { label: 'My bids', href: ROUTES.myBids },
]

export const GUEST_NAV_LINKS: readonly NavLinkItem[] = [
  { label: 'Browse',       href: ROUTES.browse },
  { label: 'How it works', href: ROUTES.howItWorks },
]

/** A link is active on its own path and on any nested path ("/auctions/42" activates "/auctions"). */
export function isNavActive(pathname: string | null, href: string): boolean {
  if (!pathname) return false
  if (href === ROUTES.home) return pathname === ROUTES.home
  return pathname === href || pathname.startsWith(`${href}/`)
}
