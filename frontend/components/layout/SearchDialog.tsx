'use client'

import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Clock, Search } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { useCountdown } from '@/hooks/useCountdown'
import { useEndingSoon } from '@/hooks/useEndingSoon'
import { auctionService } from '@/services/auction.service'
import { formatCurrency } from '@/lib/format'
import { formatTimeLeft } from '@/lib/time-left'
import { ROUTES } from '@/lib/navigation'
import {
  RECENT_SEARCHES_KEY,
  addRecentSearch,
  parseRecentSearches,
} from '@/lib/recent-searches'
import { cn } from '@/lib/utils'
import type { AuctionBrowseItem } from '@/types/ui/auction-browse.ui'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 250

function readRecent(): string[] {
  try {
    return parseRecentSearches(window.localStorage.getItem(RECENT_SEARCHES_KEY))
  } catch {
    return []
  }
}

function writeRecent(list: string[]) {
  try {
    window.localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(list))
  } catch {
    // storage unavailable (private mode, quota) — recents are a convenience only
  }
}

function SectionLabel({ children }: { children: string }) {
  return (
    <h3 className="mx-2 mt-1 mb-1.5 text-[11px] font-semibold tracking-[0.08em] text-muted-foreground uppercase">
      {children}
    </h3>
  )
}

function Kbd({ children }: { children: string }) {
  return <kbd className="rounded-[4px] border bg-background px-[5px] py-px font-mono">{children}</kbd>
}

function TimeLeft({ endTime }: { endTime: Date }) {
  const { secondsLeft, timerState } = useCountdown(endTime)
  return (
    <span
      className={cn(
        'mt-0.5 block font-mono text-xs font-medium text-muted-foreground',
        timerState === 'warning'  && 'text-[var(--color-auction-ending-text)]',
        timerState === 'critical' && 'text-[var(--color-auction-critical-text)]',
      )}
    >
      {secondsLeft === null ? '' : formatTimeLeft(secondsLeft)}
    </span>
  )
}

function ResultRow({ auction, onSelect }: { auction: AuctionBrowseItem; onSelect: () => void }) {
  return (
    <Link
      href={`${ROUTES.browse}/${auction.id}`}
      onClick={onSelect}
      data-result
      className="flex items-center gap-3 rounded-lg p-2 outline-none transition-colors hover:bg-muted focus-visible:bg-muted"
    >
      <span
        className="flex size-11 shrink-0 items-center justify-center rounded-[6px] bg-brand-100 font-display text-[15px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
        aria-hidden
      >
        {auction.title.charAt(0).toUpperCase()}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{auction.title}</span>
        <span className="mt-0.5 block text-xs text-muted-foreground">
          {auction.categoryName} · {auction.totalBids} {auction.totalBids === 1 ? 'bid' : 'bids'}
        </span>
      </span>
      <span className="shrink-0 text-right">
        <span className="block font-mono text-sm font-semibold">{formatCurrency(auction.currentPrice)}</span>
        <TimeLeft endTime={auction.endTime} />
      </span>
    </Link>
  )
}

interface SearchDialogProps {
  open:         boolean
  onOpenChange: (open: boolean) => void
}

export function SearchDialog({ open, onOpenChange }: SearchDialogProps) {
  const router = useRouter()
  const endingSoon = useEndingSoon(3)
  const listRef  = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery]   = useState('')
  const [recent, setRecent] = useState<string[]>([])
  // Results remember which query they answer, so a stale response is never shown for newer input.
  const [found, setFound]   = useState<{ q: string; items: AuctionBrowseItem[] } | null>(null)

  const trimmed   = query.trim()
  const searching = trimmed.length >= MIN_QUERY_LENGTH
  const results   = searching && found?.q === trimmed ? found.items : null

  useEffect(() => {
    if (!open || !searching) return
    let cancelled = false
    const timer = setTimeout(() => {
      auctionService
        .getBrowseAuctions({ keyword: trimmed, size: 5 })
        .then((res) => { if (!cancelled) setFound({ q: trimmed, items: res.items }) })
        .catch(() => { if (!cancelled) setFound({ q: trimmed, items: [] }) })
    }, DEBOUNCE_MS)
    return () => { cancelled = true; clearTimeout(timer) }
  }, [open, searching, trimmed])

  function handleOpenChange(next: boolean) {
    if (next) setRecent(readRecent())
    else { setQuery(''); setFound(null) }
    onOpenChange(next)
  }

  function runSearch(term: string) {
    const clean = term.trim()
    if (!clean) return
    writeRecent(addRecentSearch(readRecent(), clean))
    handleOpenChange(false)
    router.push(`${ROUTES.browse}?q=${encodeURIComponent(clean)}`)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    runSearch(query)
  }

  /** ↑/↓ walk the result rows; ↑ from the first row returns to the input. */
  function handleKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
    const rows = Array.from(listRef.current?.querySelectorAll<HTMLElement>('[data-result]') ?? [])
    if (rows.length === 0) return
    e.preventDefault()
    const at = rows.indexOf(document.activeElement as HTMLElement)
    if (e.key === 'ArrowDown') rows[Math.min(at + 1, rows.length - 1)].focus()
    else if (at <= 0) inputRef.current?.focus()
    else rows[at - 1].focus()
  }

  const close = () => handleOpenChange(false)

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        onKeyDown={handleKeyDown}
        className="top-24 translate-y-0 gap-0 overflow-hidden rounded-[10px] p-0 shadow-[0_18px_48px_rgb(20_23_31/0.16)] sm:max-w-[640px]"
      >
        <DialogTitle className="sr-only">Search auctions</DialogTitle>

        <form onSubmit={handleSubmit} className="p-3">
          <div className="flex h-11 items-center gap-2.5 rounded-[6px] border border-[var(--color-text-brand)] bg-muted px-3">
            <Search className="size-[18px] shrink-0 text-muted-foreground" aria-hidden />
            <input
              ref={inputRef}
              autoFocus
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search lots, sellers, categories"
              aria-label="Search auctions"
              className="h-full w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
          </div>
        </form>

        <div ref={listRef} className="max-h-[60vh] overflow-y-auto">
          {searching ? (
            <div className="px-2 pb-2">
              {results === null ? (
                <div className="flex flex-col gap-2 p-2">
                  <Skeleton className="h-11" />
                  <Skeleton className="h-11" />
                </div>
              ) : results.length === 0 ? (
                <p className="p-4 text-center text-sm text-muted-foreground">
                  No auctions match “{trimmed}”.
                </p>
              ) : (
                <>
                  {results.map((a) => <ResultRow key={a.id} auction={a} onSelect={close} />)}
                  <button
                    type="button"
                    data-result
                    onClick={() => runSearch(trimmed)}
                    className="mt-1 w-full rounded-lg p-2 text-left text-sm text-[var(--color-text-brand)] outline-none hover:bg-muted focus-visible:bg-muted"
                  >
                    See all results for “{trimmed}”
                  </button>
                </>
              )}
            </div>
          ) : (
            <>
              {recent.length > 0 && (
                <section className="px-4 pt-1 pb-3">
                  <h3 className="mb-2.5 text-[11px] font-semibold tracking-[0.08em] text-muted-foreground uppercase">
                    Recent
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {recent.map((term) => (
                      <button
                        key={term}
                        type="button"
                        data-result
                        onClick={() => runSearch(term)}
                        className="flex h-8 items-center gap-1.5 rounded-full border bg-background px-3 text-[13px] outline-none transition-colors hover:bg-muted focus-visible:bg-muted"
                      >
                        <Clock className="size-[13px] text-muted-foreground" aria-hidden />
                        {term}
                      </button>
                    ))}
                  </div>
                </section>
              )}
              {endingSoon.length > 0 && (
                <section className="px-2 pb-2">
                  <SectionLabel>Ending soon</SectionLabel>
                  {endingSoon.map((a) => <ResultRow key={a.id} auction={a} onSelect={close} />)}
                </section>
              )}
            </>
          )}
        </div>

        <div className="flex items-center gap-4 border-t bg-muted px-4 py-2.5 text-xs text-muted-foreground">
          <span><Kbd>↑↓</Kbd> navigate</span>
          <span><Kbd>↵</Kbd> open</span>
          <span className="ml-auto"><Kbd>esc</Kbd> close</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}
