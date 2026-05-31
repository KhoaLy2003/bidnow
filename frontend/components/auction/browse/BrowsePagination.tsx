import Link from 'next/link'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { DEFAULT_SORT, SORT_OPTIONS, type SortOption } from '@/types/ui/browse.ui'
import { parseBrowseFilters, buildBrowseUrl } from '@/lib/browse-utils'

interface BrowsePaginationProps {
  currentPage:  number                              // 0-based
  totalPages:   number
  searchParams: Record<string, string | undefined>  // active URL params (not pending)
}

const NAV_CLASSES =
  'flex items-center justify-center size-8 rounded border border-border text-sm transition-colors duration-[var(--duration-tesla)] hover:border-foreground hover:text-foreground'

const DISABLED_NAV_CLASSES =
  'flex items-center justify-center size-8 rounded border border-border text-sm opacity-40 pointer-events-none'

const PAGE_CLASSES =
  'flex items-center justify-center size-8 rounded border border-border text-sm font-mono text-muted-foreground transition-colors duration-[var(--duration-tesla)] hover:border-foreground hover:text-foreground'

const ACTIVE_PAGE_CLASSES =
  'flex items-center justify-center size-8 rounded text-sm font-mono font-medium bg-foreground text-background pointer-events-none'

export function BrowsePagination({
  currentPage,
  totalPages,
  searchParams,
}: BrowsePaginationProps) {
  if (totalPages <= 1) return null

  const filters = parseBrowseFilters(searchParams)
  const sort    = SORT_OPTIONS.includes(searchParams.sort as SortOption)
    ? (searchParams.sort as SortOption)
    : DEFAULT_SORT

  function pageUrl(page: number) {
    return buildBrowseUrl(filters, sort, page)
  }

  function getPageNumbers(): (number | '...')[] {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
    if (currentPage < 4) {
      return [0, 1, 2, 3, '...', totalPages - 2, totalPages - 1]
    }
    if (currentPage > totalPages - 5) {
      return [0, 1, '...', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1]
    }
    return [0, '...', currentPage - 1, currentPage, currentPage + 1, '...', totalPages - 1]
  }

  const pages = getPageNumbers()

  return (
    <div className="flex flex-col items-center gap-2 mt-8">
      <div className="flex items-center gap-1">
        {currentPage > 0 ? (
          <Link href={pageUrl(currentPage - 1)} className={NAV_CLASSES} aria-label="Previous page">
            <ChevronLeft className="size-4" />
          </Link>
        ) : (
          <span className={DISABLED_NAV_CLASSES} aria-disabled>
            <ChevronLeft className="size-4" />
          </span>
        )}

        {pages.map((p, i) =>
          p === '...' ? (
            <span key={`ellipsis-${i}`} className="flex items-center justify-center size-8 text-sm text-muted-foreground">
              …
            </span>
          ) : (
            <Link
              key={p}
              href={pageUrl(p)}
              aria-current={p === currentPage ? 'page' : undefined}
              className={p === currentPage ? ACTIVE_PAGE_CLASSES : PAGE_CLASSES}
            >
              {p + 1}
            </Link>
          ),
        )}

        {currentPage < totalPages - 1 ? (
          <Link href={pageUrl(currentPage + 1)} className={NAV_CLASSES} aria-label="Next page">
            <ChevronRight className="size-4" />
          </Link>
        ) : (
          <span className={DISABLED_NAV_CLASSES} aria-disabled>
            <ChevronRight className="size-4" />
          </span>
        )}
      </div>

      <p className="text-xs text-muted-foreground font-mono">
        Page {currentPage + 1} of {totalPages}
      </p>
    </div>
  )
}
