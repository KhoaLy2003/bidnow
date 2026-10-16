export const ROUTES = {
  home:            '/',
  browse:          '/auctions',
  howItWorks:      '/how-it-works',
  faq:             '/faq',
  about:           '/about',
  myBids:         '/my-bids',
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

/** The header's left-hand links, identical for guests, members and admins. */
export const NAV_LINKS: readonly NavLinkItem[] = [
  { label: 'Browse',       href: ROUTES.browse },
  { label: 'How it works', href: ROUTES.howItWorks },
  { label: 'FAQ',          href: ROUTES.faq },
  { label: 'About',        href: ROUTES.about },
]

/** A link is active on its own path and on any nested path ("/auctions/42" activates "/auctions"). */
export function isNavActive(pathname: string | null, href: string): boolean {
  if (!pathname) return false
  if (href === ROUTES.home) return pathname === ROUTES.home
  return pathname === href || pathname.startsWith(`${href}/`)
}
