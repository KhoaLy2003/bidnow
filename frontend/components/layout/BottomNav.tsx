'use client'

import React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, Search, Plus, ListOrdered, User } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const TABS: { label: string; href: string; Icon: React.FC<{ className?: string }>; accent?: boolean }[] = [
  { label: 'Home',    href: '/',        Icon: Home },
  { label: 'Browse',  href: '/auctions', Icon: Search },
  { label: 'Sell',    href: '/sell',     Icon: Plus,  accent: true },
  { label: 'My Bids', href: '/my-bids',  Icon: ListOrdered },
  { label: 'Account', href: '/profile',  Icon: User },
]

export function BottomNav() {
  const pathname = usePathname()

  return (
    <nav
      className="fixed bottom-0 inset-x-0 z-[var(--z-index-sticky)] flex h-14 items-center border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
      aria-label="Main navigation"
    >
      {TABS.map(({ label, href, Icon, accent }) => {
        const isActive = pathname === href || (href !== '/' && pathname.startsWith(href))

        if (accent) {
          return (
            <Link
              key={href}
              href={href}
              className="flex flex-1 flex-col items-center justify-center"
            >
              <Button
                variant="brand"
                size="icon"
                className="rounded-full -mt-4 [box-shadow:var(--shadow-brand)]"
                aria-label={label}
              >
                <Icon className="size-5" />
              </Button>
              <span className="mt-0.5 text-[10px] text-muted-foreground">{label}</span>
            </Link>
          )
        }

        return (
          <Link
            key={href}
            href={href}
            className={cn(
              'flex flex-1 flex-col items-center justify-center gap-0.5 py-1 text-[10px] transition-colors',
              isActive ? 'text-primary' : 'text-muted-foreground hover:text-foreground',
            )}
            aria-current={isActive ? 'page' : undefined}
          >
            <Icon className="size-5" />
            {label}
          </Link>
        )
      })}
    </nav>
  )
}
