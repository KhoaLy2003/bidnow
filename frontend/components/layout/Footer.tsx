import Link from 'next/link'
import { Gavel } from 'lucide-react'
import { Separator } from '@/components/ui/separator'

const LINKS = {
  Platform: [
    { label: 'Browse Auctions', href: '/auctions' },
    { label: 'Sell an Item',    href: '/sell' },
    { label: 'How It Works',   href: '/how-it-works' },
  ],
  Account: [
    { label: 'My Bids',   href: '/my-bids' },
    { label: 'Wallet',    href: '/wallet' },
    { label: 'Profile',   href: '/profile' },
  ],
  Support: [
    { label: 'Help Center', href: '/help' },
    { label: 'Contact',     href: '/contact' },
    { label: 'Privacy',     href: '/privacy' },
    { label: 'Terms',       href: '/terms' },
  ],
}

export function Footer() {
  return (
    <footer className="border-t bg-[var(--color-bg-elevated)]">
      <div className="mx-auto max-w-[var(--container-xl)] px-4 py-12">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          {/* Brand */}
          <div className="col-span-2 md:col-span-1 flex flex-col gap-3">
            <Link href="/" className="flex items-center gap-2">
              <Gavel className="size-5 text-[var(--color-text-brand)]" />
              <span className="text-base font-medium">
                Bid<span className="text-[var(--color-text-brand)] font-medium">Now</span>
              </span>
            </Link>
            <p className="text-sm text-muted-foreground max-w-xs">
              The fast, fair auction platform for buying and selling anything.
            </p>
          </div>

          {/* Link columns */}
          {Object.entries(LINKS).map(([section, links]) => (
            <div key={section} className="flex flex-col gap-3">
              <p className="text-xs font-medium uppercase text-muted-foreground">
                {section}
              </p>
              <ul className="flex flex-col gap-2">
                {links.map(({ label, href }) => (
                  <li key={href}>
                    <Link
                      href={href}
                      className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                    >
                      {label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <Separator className="my-8" />

        <p className="text-xs text-muted-foreground text-center">
          © {new Date().getFullYear()} BidNow. All rights reserved.
        </p>
      </div>
    </footer>
  )
}
