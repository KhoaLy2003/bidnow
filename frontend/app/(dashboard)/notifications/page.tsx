'use client'

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { BellOff, CheckCheck, Mail, MailOpen, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { NotificationItem } from '@/components/notification/NotificationItem'
import { notificationService } from '@/services/notification.service'
import { useNotificationStore } from '@/store/notificationStore'
import { toNotification } from '@/types/mappers/notification.mapper'
import {
  NOTIFICATION_TYPE_FILTERS, PAGE_SIZE, parseNotificationFilters, toListParams, toNotificationSearchParams,
  type NotificationFilters,
} from '@/lib/notification-filters'
import type { Notification } from '@/types/ui/notification.ui'

const ALL_TYPES = 'all'

type Confirm = { kind: 'selected'; ids: string[] } | { kind: 'read' } | { kind: 'one'; id: string } | null

function NotificationsView() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const filters = useMemo(() => parseNotificationFilters(new URLSearchParams(searchParams.toString())), [searchParams])

  const [items, setItems] = useState<Notification[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [search, setSearch] = useState(filters.q)
  const [confirm, setConfirm] = useState<Confirm>(null)
  // Last non-null confirm, kept so the dialog text survives its close animation.
  const [shownConfirm, setShownConfirm] = useState<Confirm>(null)
  const openConfirm = (c: NonNullable<Confirm>) => { setShownConfirm(c); setConfirm(c) }
  // q the debounce last pushed to the URL; any other change of filters.q is external.
  const [pushedQ, setPushedQ] = useState(filters.q)
  const [prevQ, setPrevQ] = useState(filters.q)
  if (filters.q !== prevQ) {
    // Adjust state during render: follow external URL changes of q (back/forward, links).
    setPrevQ(filters.q)
    if (filters.q !== pushedQ) {
      setPushedQ(filters.q)
      setSearch(filters.q)
    }
  }

  const navigate = useCallback((next: NotificationFilters) => {
    const qs = toNotificationSearchParams(next).toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [pathname, router])

  // Skeleton shows whenever the loaded data does not belong to the current filters.
  const queryKey = useMemo(() => JSON.stringify(toListParams(filters, PAGE_SIZE)), [filters])
  const loading = loadedKey !== queryKey
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false
    let redirected = false
    notificationService.list(toListParams(filters, PAGE_SIZE))
      .then((res) => {
        if (cancelled) return
        const pages = Math.max(1, res.data.pagination.totalPages)
        if (res.data.data.length === 0 && filters.page > 1 && filters.page > pages) {
          // Past the last page: jump back instead of showing a false empty state.
          redirected = true
          navigate({ ...filters, page: pages })
          return
        }
        const next = res.data.data.map(toNotification)
        const ids = new Set(next.map((n) => n.id))
        setItems(next)
        setTotalPages(pages)
        setSelected((prev) => new Set([...prev].filter((id) => ids.has(id))))
      })
      .catch(() => {
        if (cancelled) return
        setItems([])
        setSelected(new Set())
        toast.error('Could not load notifications.')
      })
      .finally(() => {
        if (cancelled || redirected) return
        setLoadedKey(JSON.stringify(toListParams(filters, PAGE_SIZE)))
      })
    return () => { cancelled = true }
  }, [filters, navigate, reloadToken])

  // Debounced search → URL (resets to page 1)
  useEffect(() => {
    if (search === filters.q) return
    const timer = setTimeout(() => {
      setPushedQ(search)
      navigate({ ...filters, q: search, page: 1 })
    }, 300)
    return () => clearTimeout(timer)
  }, [search, filters, navigate])

  /** Reloads this page and the bell (list + count) after a change. */
  const afterChange = useCallback(async () => {
    const store = useNotificationStore.getState()
    setReloadToken((t) => t + 1)
    await Promise.all([store.refreshCount(), store.loadRecent()])
  }, [])

  const run = async (requests: Promise<unknown>[], failure: string) => {
    const results = await Promise.allSettled(requests)
    if (results.some((r) => r.status === 'rejected')) toast.error(failure)
    await afterChange()
  }

  const open = (n: Notification) => {
    if (!n.isRead) void run([notificationService.markRead(n.id)], 'Could not mark as read.')
    if (n.linkUrl) router.push(n.linkUrl)
  }

  const toggleRead = (n: Notification) =>
    run([n.isRead ? notificationService.markUnread(n.id) : notificationService.markRead(n.id)], 'Could not update.')

  const markSelectedRead = () =>
    run(items.filter((n) => selected.has(n.id) && !n.isRead).map((n) => notificationService.markRead(n.id)),
      'Some notifications could not be marked as read.')

  const markAllRead = async () => {
    if (!(await useNotificationStore.getState().markAllRead())) toast.error('Could not mark all as read.')
    await afterChange()
  }

  const confirmDelete = async () => {
    const c = confirm
    setConfirm(null)
    if (!c) return
    if (c.kind === 'read') await run([notificationService.removeRead()], 'Could not delete read notifications.')
    if (c.kind === 'one') await run([notificationService.remove(c.id)], 'Could not delete.')
    if (c.kind === 'selected') await run(c.ids.map((id) => notificationService.remove(id)), 'Some notifications could not be deleted.')
  }

  const allSelected = items.length > 0 && items.every((n) => selected.has(n.id))
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(items.map((n) => n.id)))
  const toggleOne = (id: string) => setSelected((prev) => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

  const filtered = filters.tab === 'unread' || filters.type !== null || filters.q !== ''

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-semibold">Notifications</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={markAllRead} className="gap-1.5">
            <CheckCheck className="size-4" aria-hidden /> Mark all as read
          </Button>
          <Button variant="outline" size="sm" onClick={() => openConfirm({ kind: 'read' })} className="gap-1.5">
            <Trash2 className="size-4" aria-hidden /> Delete read
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Tabs value={filters.tab} onValueChange={(tab) => navigate({ ...filters, tab: tab === 'unread' ? 'unread' : 'all', page: 1 })}>
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="unread">Unread</TabsTrigger>
          </TabsList>
        </Tabs>
        <Select
          value={filters.type ?? ALL_TYPES}
          onValueChange={(v) => navigate({ ...filters, type: !v || v === ALL_TYPES ? null : String(v), page: 1 })}
        >
          <SelectTrigger className="w-full sm:w-52" aria-label="Filter by type">
            <SelectValue>
              {NOTIFICATION_TYPE_FILTERS.find((t) => t.value === filters.type)?.label ?? 'All types'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_TYPES}>All types</SelectItem>
            {NOTIFICATION_TYPE_FILTERS.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search notifications"
          aria-label="Search notifications"
          maxLength={100}
          className="sm:max-w-xs"
        />
      </div>

      {items.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-lg border px-3 py-2">
          <Checkbox checked={allSelected} disabled={loading} onCheckedChange={toggleAll} aria-label="Select all on this page" />
          <span className="text-sm text-muted-foreground">{selected.size} selected</span>
          <Button variant="ghost" size="sm" disabled={loading || selected.size === 0} onClick={markSelectedRead}>Mark read</Button>
          <Button variant="ghost" size="sm" disabled={loading || selected.size === 0}
            onClick={() => openConfirm({ kind: 'selected', ids: [...selected] })}>Delete</Button>
        </div>
      )}

      {loading ? (
        <div className="flex flex-col gap-3" aria-busy>
          {Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-16 w-full" />)}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <BellOff className="size-8 text-muted-foreground" aria-hidden />
          <p className="font-medium">{filtered ? 'No notifications match these filters' : 'No notifications yet'}</p>
          <p className="text-sm text-muted-foreground">
            {filtered ? 'Try another tab, type or search.' : 'Bids, wins and payments will show up here.'}
          </p>
        </div>
      ) : (
        <ul className="divide-y rounded-lg border">
          {items.map((n) => (
            <li key={n.id}>
              <NotificationItem
                notification={n}
                onSelect={open}
                leading={
                  <Checkbox className="mt-1.5" checked={selected.has(n.id)} onCheckedChange={() => toggleOne(n.id)}
                    aria-label={`Select ${n.title}`} />
                }
                trailing={
                  <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" size="icon-sm" onClick={() => toggleRead(n)}
                      aria-label={n.isRead ? `Mark "${n.title}" as unread` : `Mark "${n.title}" as read`}>
                      {n.isRead ? <Mail className="size-4" /> : <MailOpen className="size-4" />}
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => openConfirm({ kind: 'one', id: n.id })}
                      aria-label={`Delete "${n.title}"`}>
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                }
              />
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <nav className="flex items-center justify-between" aria-label="Pagination">
          <Button variant="outline" size="sm" disabled={filters.page <= 1}
            onClick={() => navigate({ ...filters, page: filters.page - 1 })}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {filters.page} of {totalPages}</span>
          <Button variant="outline" size="sm" disabled={filters.page >= totalPages}
            onClick={() => navigate({ ...filters, page: filters.page + 1 })}>Next</Button>
        </nav>
      )}

      <Dialog open={confirm !== null} onOpenChange={(o) => { if (!o) setConfirm(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete notifications?</DialogTitle>
            <DialogDescription>
              {shownConfirm?.kind === 'read' && 'All read notifications will be deleted.'}
              {shownConfirm?.kind === 'one' && 'This notification will be deleted.'}
              {shownConfirm?.kind === 'selected' && `${shownConfirm.ids.length} selected notification(s) will be deleted.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>Cancel</DialogClose>
            <Button variant="destructive" onClick={confirmDelete}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default function NotificationsPage() {
  return (
    <Suspense fallback={null}>
      <NotificationsView />
    </Suspense>
  )
}
