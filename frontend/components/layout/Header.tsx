'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { Gavel, User, LogOut, Settings, ListOrdered } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent,
  DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel,
} from '@/components/ui/dropdown-menu'
import { SearchBar }         from '@/components/shared/SearchBar'
import { UserAvatar }        from '@/components/shared/UserAvatar'
import { WalletBadge }       from '@/components/wallet/WalletBadge'
import { NotificationBell }  from '@/components/notification/NotificationBell'
import { cn } from '@/lib/utils'

const NAV_LINKS = [
  { label: 'Browse',   href: '/auctions' },
  { label: 'My Bids',  href: '/my-bids' },
  { label: 'Sell',     href: '/sell' },
] as const

// Placeholder — replace with real auth context
const MOCK_USER = { name: 'Hiep Nguyen', avatarUrl: undefined as string | undefined }

export function Header() {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <header
      className={cn(
        'sticky top-0 z-[var(--z-index-sticky)] h-16 bg-background border-b border-border transition-[backdrop-filter,box-shadow] duration-150',
        scrolled && 'backdrop-blur-sm shadow-sm',
      )}
    >
      <div className="mx-auto flex h-full max-w-[var(--container-xl)] items-center gap-4 px-4">
        {/* Wordmark */}
        <Link href="/" className="flex shrink-0 items-center gap-2">
          <Gavel className="size-5 text-[var(--color-text-brand)]" />
          <span className="hidden font-semibold sm:inline-block">
            Bid<span className="text-[var(--color-text-brand)] font-bold">Now</span>
          </span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden md:flex items-center gap-1">
          {NAV_LINKS.map(({ label, href }) => (
            <Button key={href} variant="ghost" size="sm" render={<Link href={href} />} nativeButton={false}>
              {label}
            </Button>
          ))}
        </nav>

        {/* Search */}
        <SearchBar className="hidden sm:flex flex-1 max-w-[400px] mx-auto" />

        {/* Right controls */}
        <div className="ml-auto flex items-center gap-1 shrink-0">
          <WalletBadge />
          <NotificationBell />

          {/* User menu */}
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button variant="ghost" size="icon" className="rounded-full" />
              }
            >
              <UserAvatar name={MOCK_USER.name} avatarUrl={MOCK_USER.avatarUrl} size="sm" />
              <span className="sr-only">Account menu</span>
            </DropdownMenuTrigger>
            <DropdownMenuContent side="bottom" align="end" sideOffset={8}>
              <DropdownMenuLabel className="font-normal">
                <p className="font-medium text-sm">{MOCK_USER.name}</p>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem render={<Link href="/profile" className="flex items-center gap-2" />}>
                <User className="size-4" /> Profile
              </DropdownMenuItem>
              <DropdownMenuItem render={<Link href="/my-bids" className="flex items-center gap-2" />}>
                <ListOrdered className="size-4" /> My Bids
              </DropdownMenuItem>
              <DropdownMenuItem render={<Link href="/settings" className="flex items-center gap-2" />}>
                <Settings className="size-4" /> Settings
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" className="gap-2">
                <LogOut className="size-4" /> Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* Sell CTA — desktop */}
          <Button variant="brand" size="sm" className="hidden md:inline-flex ml-1" render={<Link href="/sell" />} nativeButton={false}>
            Sell
          </Button>
        </div>
      </div>
    </header>
  )
}
