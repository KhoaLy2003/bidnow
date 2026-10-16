'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { isNavActive, type NavLinkItem } from '@/lib/navigation'
import { cn } from '@/lib/utils'

/** Primary navigation. The active page is marked by a bar sitting on the header's bottom border. */
export function NavLinks({ links }: { links: readonly NavLinkItem[] }) {
  const pathname = usePathname()

  return (
    <nav aria-label="Main" className="hidden gap-1 self-stretch md:flex">
      {links.map(({ label, href }) => {
        const active = isNavActive(pathname, href)
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'relative flex items-center whitespace-nowrap px-3.5 text-sm font-medium transition-colors',
              active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {label}
            {active && (
              <span
                className="absolute inset-x-3.5 -bottom-px h-0.5 rounded-t-[2px] bg-[var(--color-text-brand)]"
                aria-hidden
              />
            )}
          </Link>
        )
      })}
    </nav>
  )
}
