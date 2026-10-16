'use client'

import { useCallback, useEffect, useMemo, useState, useTransition } from 'react'
import { useRouter } from 'next/navigation'
import { buildBrowseUrl, clearFilter, parseBrowseQuery } from '@/lib/browse-utils'
import type { ActiveFilterKey } from '@/lib/browse-utils'
import { DEFAULT_FILTERS, DEFAULT_SORT } from '@/types/ui/browse.ui'
import type { BrowseFilters, BrowseQuery, SortOption } from '@/types/ui/browse.ui'

/** Pause after the last keystroke or slider move before the URL is updated. */
export const BROWSE_DEBOUNCE_MS = 300

interface Draft {
  query: BrowseQuery
  /** How long to wait before committing this draft; 0 for discrete choices. */
  delay: number
}

export interface BrowseQueryApi {
  /** What the controls show: updates the instant the user acts, ahead of the URL. */
  query:      BrowseQuery
  /** Set one or more filters. Pass `debounced` for typing and dragging; clicks commit at once. */
  setFilters: (patch: Partial<BrowseFilters>, options?: { debounced?: boolean }) => void
  setSort:    (sort: SortOption) => void
  clearOne:   (key: ActiveFilterKey) => void
  clearAll:   () => void
  /** Commit a pending debounced change now (Enter in the search box). */
  flush:      () => void
  /** True from the first change until the new results have rendered. */
  isUpdating: boolean
}

/**
 * Keeps the browse controls and the URL in step. Controls read the draft, so they respond
 * immediately; the draft is pushed to the URL (`router.replace`, no history entry, no scroll
 * jump) after a debounce for text and sliders or at once for clicks. The server page then
 * re-renders with fresh results. Changes made elsewhere (back button, header search, a link)
 * replace the draft.
 *
 * @param searchParams the committed `/auctions` query string as the server received it
 */
export function useBrowseQuery(searchParams: Record<string, string | undefined>): BrowseQueryApi {
  const router = useRouter()
  const [isNavigating, startTransition] = useTransition()

  const committed    = useMemo(() => parseBrowseQuery(searchParams), [searchParams])
  const committedUrl = buildBrowseUrl(committed.filters, committed.sort)

  const [draft,      setDraft]      = useState<Draft>({ query: committed, delay: 0 })
  // Last URL this hook pushed. A committed URL that differs from it came from somewhere else.
  const [pushedUrl,  setPushedUrl]  = useState(committedUrl)
  const [prevUrl,    setPrevUrl]    = useState(committedUrl)

  if (committedUrl !== prevUrl) {
    // Adjust state during render: follow URL changes this hook did not make.
    setPrevUrl(committedUrl)
    if (committedUrl !== pushedUrl) {
      setPushedUrl(committedUrl)
      setDraft({ query: committed, delay: 0 })
    }
  }

  const draftUrl = buildBrowseUrl(draft.query.filters, draft.query.sort)
  const isDirty  = draftUrl !== committedUrl

  useEffect(() => {
    if (!isDirty) return
    const timer = setTimeout(() => {
      setPushedUrl(draftUrl)
      startTransition(() => router.replace(draftUrl, { scroll: false }))
    }, draft.delay)
    return () => clearTimeout(timer)
  }, [isDirty, draftUrl, draft.delay, router])

  const update = useCallback((patch: (q: BrowseQuery) => BrowseQuery, delay: number) => {
    setDraft((prev) => ({ query: patch(prev.query), delay }))
  }, [])

  const setFilters = useCallback<BrowseQueryApi['setFilters']>((patch, options) => {
    update((q) => ({ ...q, filters: { ...q.filters, ...patch } }), options?.debounced ? BROWSE_DEBOUNCE_MS : 0)
  }, [update])

  const setSort = useCallback((sort: SortOption) => {
    update((q) => ({ ...q, sort }), 0)
  }, [update])

  const clearOne = useCallback((key: ActiveFilterKey) => {
    update((q) => ({ ...q, filters: clearFilter(q.filters, key) }), 0)
  }, [update])

  const clearAll = useCallback(() => {
    update(() => ({ filters: DEFAULT_FILTERS, sort: DEFAULT_SORT }), 0)
  }, [update])

  const flush = useCallback(() => {
    update((q) => q, 0)
  }, [update])

  return {
    query:      draft.query,
    setFilters,
    setSort,
    clearOne,
    clearAll,
    flush,
    isUpdating: isDirty || isNavigating,
  }
}
