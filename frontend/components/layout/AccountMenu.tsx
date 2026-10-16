'use client'

import type { ComponentType } from 'react'
import Link from 'next/link'
import { useTheme } from 'next-themes'
import {
  ChevronDown, Eye, EyeOff, ListOrdered, LogOut, Monitor, Moon,
  Package, Receipt, Settings, Sun, User, Wallet,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuGroup,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/shared/UserAvatar'
import { useWallet } from '@/hooks/useWallet'
import { useHasMounted } from '@/hooks/useHasMounted'
import { useBalanceVisibilityStore } from '@/store/balanceVisibilityStore'
import { displayBalance } from '@/lib/balance-display'
import { ROUTES } from '@/lib/navigation'
import { cn } from '@/lib/utils'

interface MenuLink {
  label: string
  href:  string
  Icon:  ComponentType<{ className?: string }>
  hint?: string
}

// Grouped by intent: what I buy and sell, then account housekeeping.
const MENU_GROUPS: readonly (readonly MenuLink[])[] = [
  [
    { label: 'My bids',          href: ROUTES.myBids,          Icon: ListOrdered },
    { label: 'Purchase history', href: ROUTES.purchaseHistory, Icon: Receipt },
    { label: 'My auctions',      href: ROUTES.myAuctions,      Icon: Package },
  ],
  [
    { label: 'Wallet',   href: ROUTES.wallet,   Icon: Wallet, hint: 'Transactions' },
    { label: 'Profile',  href: ROUTES.profile,  Icon: User },
    { label: 'Settings', href: ROUTES.settings, Icon: Settings },
  ],
]

const THEMES = [
  { value: 'light',  label: 'Light theme',  Icon: Sun },
  { value: 'dark',   label: 'Dark theme',   Icon: Moon },
  { value: 'system', label: 'System theme', Icon: Monitor },
] as const

const ITEM_CLASS = 'h-10 gap-3 rounded-[6px] px-2.5 text-sm'

function BalanceCard() {
  const { available, isLoading, transactions } = useWallet()
  const visible = useBalanceVisibilityStore((s) => s.visible)
  const toggle  = useBalanceVisibilityStore((s) => s.toggle)
  const showSkeleton = isLoading && transactions.length === 0 && available === 0

  return (
    <div className="mx-3 mb-3 flex items-center justify-between gap-3 rounded-lg bg-muted p-3.5">
      <div className="min-w-0">
        <p className="text-[10px] font-semibold tracking-[0.08em] text-muted-foreground uppercase">Available</p>
        {showSkeleton ? (
          <Skeleton className="mt-1 h-7 w-28" />
        ) : (
          <p className="mt-0.5 font-mono text-[26px] leading-tight font-semibold tracking-[-0.02em]">
            {displayBalance(available, visible)}
          </p>
        )}
      </div>
      <Button
        type="button"
        variant="ghost"
        className="size-9 rounded-[6px] text-muted-foreground"
        onClick={toggle}
        aria-label={visible ? 'Hide balance' : 'Show balance'}
        aria-pressed={visible}
      >
        {visible ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
      </Button>
    </div>
  )
}

function ThemeSwitch() {
  const { theme, setTheme } = useTheme()
  const mounted = useHasMounted()

  return (
    <div className="flex items-center justify-between border-t px-4 py-2.5">
      <span className="text-[13px] text-muted-foreground">Theme</span>
      <div className="flex rounded-[6px] border bg-muted p-0.5">
        {THEMES.map(({ value, label, Icon }) => {
          const selected = mounted && theme === value
          return (
            <button
              key={value}
              type="button"
              aria-label={label}
              aria-pressed={selected}
              onClick={() => setTheme(value)}
              className={cn(
                'flex h-7 w-[34px] items-center justify-center rounded-[4px] text-muted-foreground transition-colors',
                selected && 'bg-background text-foreground shadow-sm',
              )}
            >
              <Icon className="size-4" aria-hidden />
            </button>
          )
        })}
      </div>
    </div>
  )
}

interface AccountMenuProps {
  name:       string
  email:      string
  avatarUrl?: string
  verified:   boolean
  onSignOut:  () => void
}

export function AccountMenu({ name, email, avatarUrl, verified, onSignOut }: AccountMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            className="h-11 gap-1.5 rounded-[6px] pr-2 pl-1"
            aria-label="Account menu"
          />
        }
      >
        <UserAvatar name={name} avatarUrl={avatarUrl} size="md" tone="brand" />
        <ChevronDown className="size-4 text-muted-foreground" aria-hidden />
      </DropdownMenuTrigger>

      <DropdownMenuContent
        side="bottom"
        align="end"
        sideOffset={8}
        className="w-[336px] max-w-[calc(100vw-1rem)] gap-0 overflow-hidden rounded-[10px] p-0 shadow-[0_18px_48px_rgb(20_23_31/0.16)]"
      >
        <div className="flex items-center gap-3 p-4">
          <UserAvatar name={name} avatarUrl={avatarUrl} size="menu" tone="brand" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{name}</p>
            <p className="mt-0.5 truncate text-xs text-muted-foreground">{email}</p>
          </div>
          {verified && (
            <span className="rounded-[4px] bg-[var(--color-success-subtle)] px-1.5 py-[3px] text-[10px] font-semibold tracking-[0.06em] text-[var(--color-success-text)] uppercase">
              Verified
            </span>
          )}
        </div>

        <BalanceCard />

        <div className="px-2 pb-2">
          {MENU_GROUPS.map((group, i) => (
            <div key={i}>
              {i > 0 && <div className="mx-2.5 my-1.5 h-px bg-border" role="separator" />}
              <DropdownMenuGroup>
                {group.map(({ label, href, Icon, hint }) => (
                  <DropdownMenuItem
                    key={href}
                    render={<Link href={href} className="flex items-center" />}
                    className={ITEM_CLASS}
                  >
                    <Icon className="size-[18px] text-muted-foreground" />
                    <span className="flex-1">{label}</span>
                    {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuGroup>
            </div>
          ))}
        </div>

        <ThemeSwitch />

        <DropdownMenuItem
          variant="destructive"
          className="h-[46px] cursor-pointer gap-3 rounded-none border-t px-[18px] text-sm font-medium"
          onClick={onSignOut}
        >
          <LogOut className="size-[18px]" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
