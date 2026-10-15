'use client'

import React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, Search, Plus, ListOrdered, User } from 'lucide-react'
import { ROUTES, isNavActive } from '@/lib/navigation'
import { cn } from '@/lib/utils'

const TABS: { label: string; href: string; Icon: React.FC<{ className?: string }>; accent?: boolean }[] = [
  { label: 'Home',    href: ROUTES.home,    Icon: Home },
  { label: 'Browse',  href: ROUTES.browse,  Icon: Search },
  { label: 'Sell',    href: ROUTES.sell,    Icon: Plus, accent: true },
  { label: 'My bids', href: ROUTES.myBids,  Icon: ListOrdered },
  { label: 'Account', href: ROUTES.profile, Icon: User },
]

/** Floating dock for phones; the raised Sell button sits in the middle. */
export function BottomNav() {
  const pathname = usePathname()

  return (
    <nav
      className="fixed inset-x-3 bottom-[calc(0.875rem+env(safe-area-inset-bottom))] z-[var(--z-index-sticky)] flex h-16 items-center rounded-2xl border bg-background px-1.5 shadow-[0_10px_30px_rgb(20_23_31/0.14)] md:hidden"
      aria-label="Main navigation"
    >
      {TABS.map(({ label, href, Icon, accent }) => {
        const isActive = isNavActive(pathname, href)

        if (accent) {
          return (
            <Link
              key={href}
              href={href}
              aria-label="Sell an item"
              className="flex h-full flex-1 flex-col items-center justify-center gap-[3px] text-[11px] font-medium text-muted-foreground"
            >
              <span className="-mt-[26px] flex size-12 items-center justify-center rounded-full border-4 border-background bg-brand-600 text-white shadow-[0_6px_16px_rgb(79_70_229/0.4)]">
                <Icon className="size-[22px]" />
              </span>
              {label}
            </Link>
          )
        }

        return (
          <Link
            key={href}
            href={href}
            className={cn(
              'relative flex h-full flex-1 flex-col items-center justify-center gap-[3px] text-[11px] transition-colors',
              isActive
                ? 'font-semibold text-[var(--color-text-brand)]'
                : 'font-medium text-muted-foreground hover:text-foreground',
            )}
            aria-current={isActive ? 'page' : undefined}
          >
            {isActive && (
              <span
                className="absolute top-0 left-1/2 h-[3px] w-6 -translate-x-1/2 rounded-b-[3px] bg-[var(--color-text-brand)]"
                aria-hidden
              />
            )}
            <Icon className={cn('size-[22px]', isActive && '[stroke-width:2]')} />
            {label}
          </Link>
        )
      })}
    </nav>
  )
}
