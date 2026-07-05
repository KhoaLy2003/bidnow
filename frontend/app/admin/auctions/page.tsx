'use client'

import React, { useCallback, useEffect, useState } from 'react'
import {
  AlertTriangle,
  Ban,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  Clock,
  FileText,
  Filter,
  Flag,
  Gavel,
  History,
  Loader2,
  Lock,
  MoreHorizontal,
  Radio,
  Search,
  ShieldX,
  XCircle,
} from 'lucide-react'
import { toast } from 'sonner'

import { adminService } from '@/services/adminService'
import {
  type AdminAuctionDetailResponse,
  type AdminAuctionStatus,
  type AdminAuctionSummaryResponse,
} from '@/types/api/auction.api'
import { type SortDirection } from '@/types/api/admin.api'
import { cn, formatDate, getErrorMessage, getPaginationRange } from '@/lib/utils'
import { formatCurrency } from '@/lib/format'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'

const PAGE_SIZE = 20

type StatusFilter = AdminAuctionStatus | 'ALL'
type ModerationAction = 'reject' | 'cancel' | 'force-close'

const statusOptions: Array<{ label: string; value: StatusFilter }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Scheduled', value: 'SCHEDULED' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Cancelled', value: 'CANCELLED' },
  { label: 'Rejected', value: 'REJECTED' },
]

const sortOptions: Array<{ label: string; value: string }> = [
  { label: 'Created date', value: 'createdAt' },
  { label: 'End time', value: 'endTime' },
  { label: 'Current price', value: 'currentPrice' },
]

const statusConfig: Record<
  AdminAuctionStatus,
  { label: string; variant: React.ComponentProps<typeof Badge>['variant']; icon: React.ComponentType<{ className?: string }>; pulse?: boolean }
> = {
  DRAFT: { label: 'Draft', variant: 'secondary', icon: FileText },
  SCHEDULED: { label: 'Scheduled', variant: 'scheduled', icon: CalendarClock },
  ACTIVE: { label: 'Active', variant: 'active', icon: Radio, pulse: true },
  COMPLETED: { label: 'Completed', variant: 'closed', icon: Lock },
  FAILED: { label: 'Failed', variant: 'closed', icon: XCircle },
  CANCELLED: { label: 'Cancelled', variant: 'destructive', icon: Ban },
  REJECTED: { label: 'Rejected', variant: 'destructive', icon: ShieldX },
}

const actionConfig: Record<
  ModerationAction,
  { title: string; description: string; reasonRequired: boolean; confirmLabel: string; variant: 'default' | 'destructive' }
> = {
  reject: {
    title: 'Reject auction',
    description: 'Blocks this auction before it goes live. The seller will be notified.',
    reasonRequired: true,
    confirmLabel: 'Reject auction',
    variant: 'destructive',
  },
  cancel: {
    title: 'Cancel auction',
    description: 'Terminates this active auction and refunds all locked deposits.',
    reasonRequired: true,
    confirmLabel: 'Cancel auction',
    variant: 'destructive',
  },
  'force-close': {
    title: 'Force-close auction',
    description: 'Ends this auction early, crowning the current highest bidder as winner.',
    reasonRequired: false,
    confirmLabel: 'Force-close auction',
    variant: 'destructive',
  },
}

export default function AdminAuctionsPage() {
  const [auctions, setAuctions] = useState<AdminAuctionSummaryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sortBy, setSortBy] = useState('createdAt')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detail, setDetail] = useState<AdminAuctionDetailResponse | null>(null)

  const [actionTarget, setActionTarget] = useState<AdminAuctionSummaryResponse | null>(null)
  const [actionType, setActionType] = useState<ModerationAction | null>(null)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const fetchAuctions = useCallback(() => {
    adminService.getAuctions(
      { status: statusFilter, q: query.trim() || undefined },
      page,
      PAGE_SIZE,
      sortBy,
      sortDirection
    )
      .then((result) => {
        setAuctions(result.data)
        setTotalPages(result.pagination.totalPages)
        setTotalElements(result.pagination.total)
      })
      .catch((error: unknown) => {
        toast.error(getErrorMessage(error, 'Failed to fetch auctions'))
      })
      .finally(() => {
        setLoading(false)
      })
  }, [page, query, statusFilter, sortBy, sortDirection])

  useEffect(() => {
    fetchAuctions()
  }, [fetchAuctions])

  const handleViewDetail = async (auction: AdminAuctionSummaryResponse) => {
    setDetail(null)
    setDetailLoading(true)
    setIsDetailModalOpen(true)
    try {
      const data = await adminService.getAuctionDetail(auction.id)
      setDetail(data)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to fetch auction detail'))
      setIsDetailModalOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const openActionDialog = (auction: AdminAuctionSummaryResponse, action: ModerationAction) => {
    setActionTarget(auction)
    setActionType(action)
    setReason('')
  }

  const closeActionDialog = () => {
    setActionTarget(null)
    setActionType(null)
    setReason('')
  }

  const handleConfirmAction = async () => {
    if (!actionTarget || !actionType) return
    const config = actionConfig[actionType]

    if (config.reasonRequired && !reason.trim()) {
      toast.error('A reason is required for this action')
      return
    }

    setSubmitting(true)
    try {
      if (actionType === 'reject') {
        await adminService.rejectAuction(actionTarget.id, reason.trim())
      } else if (actionType === 'cancel') {
        await adminService.cancelAuction(actionTarget.id, reason.trim())
      } else {
        await adminService.forceCloseAuction(actionTarget.id, reason.trim() || undefined)
      }
      toast.success(`Auction ${actionType === 'force-close' ? 'force-closed' : `${actionType}ed`} successfully`)
      closeActionDialog()
      setLoading(true)
      fetchAuctions()
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, `Failed to ${actionType} auction`))
    } finally {
      setSubmitting(false)
    }
  }

  const hasFilters = query.trim().length > 0 || statusFilter !== 'ALL'
  const { start: showingStart, end: showingEnd } = getPaginationRange(page, PAGE_SIZE, totalElements)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-medium text-foreground">Auction Moderation</h1>
          <p className="text-sm text-muted-foreground">
            Review, reject, cancel, or force-close any of {totalElements} auctions on the platform.
          </p>
        </div>

        <div className="grid gap-2 sm:grid-cols-[minmax(220px,1fr)_170px_170px_auto] lg:w-[720px]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => {
                setLoading(true)
                setPage(0)
                setQuery(event.target.value)
              }}
              placeholder="Search by title"
              className="pl-9"
            />
          </div>

          <Select
            value={statusFilter}
            onValueChange={(value) => {
              setLoading(true)
              setPage(0)
              setStatusFilter((value ?? 'ALL') as StatusFilter)
            }}
          >
            <SelectTrigger className="w-full">
              <Filter className="size-4 text-muted-foreground" />
              <SelectValue>{(value: StatusFilter | null) => statusOptions.find((option) => option.value === value)?.label ?? 'All statuses'}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select
            value={sortBy}
            onValueChange={(value) => {
              setLoading(true)
              setPage(0)
              setSortBy(value ?? 'createdAt')
            }}
          >
            <SelectTrigger className="w-full">
              <SelectValue>{(value: string | null) => sortOptions.find((option) => option.value === value)?.label ?? 'Created date'}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {sortOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button
            variant="outline"
            onClick={() => {
              setLoading(true)
              setPage(0)
              setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'))
            }}
            className="justify-center"
          >
            {sortDirection === 'asc' ? 'Asc' : 'Desc'}
          </Button>
        </div>
      </div>

      {hasFilters && (
        <div className="flex items-center justify-between rounded-lg border bg-muted/30 px-3 py-2">
          <p className="text-sm text-muted-foreground">Showing {auctions.length} matching auctions on this page.</p>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setLoading(true)
              setPage(0)
              setQuery('')
              setStatusFilter('ALL')
            }}
          >
            Clear
          </Button>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Auctions</CardTitle>
          <CardDescription>Browse every auction across all sellers and take moderation action.</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40 hover:bg-muted/40">
                <TableHead className="px-4">Auction</TableHead>
                <TableHead>Seller</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Price</TableHead>
                <TableHead>Bids</TableHead>
                <TableHead>Ends</TableHead>
                <TableHead className="pr-4 text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    <TableCell className="px-4" colSpan={7}>
                      <div className="flex items-center gap-3 py-2">
                        <Skeleton className="size-10 rounded-md" />
                        <div className="space-y-2">
                          <Skeleton className="h-4 w-56" />
                          <Skeleton className="h-3 w-28" />
                        </div>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              ) : auctions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="h-32 text-center text-muted-foreground">
                    No auctions found.
                  </TableCell>
                </TableRow>
              ) : (
                auctions.map((auction) => (
                  <TableRow
                    key={auction.id}
                    tabIndex={0}
                    role="button"
                    onClick={() => handleViewDetail(auction)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        handleViewDetail(auction)
                      }
                    }}
                    className="cursor-pointer"
                  >
                    <TableCell className="px-4">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-foreground">{auction.title}</p>
                        <p className="font-mono text-xs text-muted-foreground">{auction.category.name}</p>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {auction.sellerName || <span className="font-mono text-xs">{auction.sellerId.slice(0, 8)}</span>}
                    </TableCell>
                    <TableCell>
                      <AuctionStatusBadge status={auction.status} />
                    </TableCell>
                    <TableCell className="font-mono">{formatCurrency(auction.currentPrice)}</TableCell>
                    <TableCell className="text-muted-foreground">{auction.totalBids}</TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(auction.endTime)}</TableCell>
                    <TableCell className="pr-4 text-right" onClick={(event) => event.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" />}>
                          <MoreHorizontal className="size-4" />
                          <span className="sr-only">Open auction actions</span>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-52">
                          <DropdownMenuGroup>
                            <DropdownMenuLabel>Moderation</DropdownMenuLabel>
                            <DropdownMenuItem onClick={() => handleViewDetail(auction)}>
                              <History className="size-4" />
                              View detail &amp; history
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                          {(auction.status === 'SCHEDULED' || auction.status === 'ACTIVE') && (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuGroup>
                                {auction.status === 'SCHEDULED' && (
                                  <DropdownMenuItem variant="destructive" onClick={() => openActionDialog(auction, 'reject')}>
                                    <ShieldX className="size-4" />
                                    Reject
                                  </DropdownMenuItem>
                                )}
                                {auction.status === 'ACTIVE' && (
                                  <>
                                    <DropdownMenuItem onClick={() => openActionDialog(auction, 'force-close')}>
                                      <Flag className="size-4" />
                                      Force-close
                                    </DropdownMenuItem>
                                    <DropdownMenuItem variant="destructive" onClick={() => openActionDialog(auction, 'cancel')}>
                                      <Ban className="size-4" />
                                      Cancel
                                    </DropdownMenuItem>
                                  </>
                                )}
                              </DropdownMenuGroup>
                            </>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
        <CardFooter className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <p className="text-sm text-muted-foreground">
            Showing <span className="font-medium text-foreground">{showingStart}</span>{'-'}<span className="font-medium text-foreground">{showingEnd}</span> of{' '}
            <span className="font-medium text-foreground">{totalElements}</span>
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon"
              onClick={() => {
                setLoading(true)
                setPage((current) => Math.max(0, current - 1))
              }}
              disabled={page === 0 || loading}
            >
              <ChevronLeft className="size-4" />
            </Button>
            <span className="min-w-24 text-center text-sm text-muted-foreground">
              Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="icon"
              onClick={() => {
                setLoading(true)
                setPage((current) => Math.min(totalPages - 1, current + 1))
              }}
              disabled={totalPages === 0 || page >= totalPages - 1 || loading}
            >
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </CardFooter>
      </Card>

      {/* Detail dialog */}
      <Dialog open={isDetailModalOpen} onOpenChange={setIsDetailModalOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Auction detail</DialogTitle>
            <DialogDescription>Full auction record and status transition history.</DialogDescription>
          </DialogHeader>

          {detailLoading ? (
            <div className="space-y-4">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : detail ? (
            <ScrollArea className="max-h-[65vh] pr-3">
              <div className="space-y-6">
                <div className="rounded-lg border p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 space-y-1">
                      <h3 className="truncate text-lg font-medium">{detail.title}</h3>
                      <p className="text-sm text-muted-foreground">{detail.category.name}</p>
                    </div>
                    <AuctionStatusBadge status={detail.status} />
                  </div>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{detail.description}</p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <DetailField icon={Gavel} label="Seller" value={detail.sellerName || detail.sellerId} />
                  <DetailField icon={Clock} label="Total bids" value={String(detail.totalBids)} />
                  <DetailField icon={CalendarClock} label="Start time" value={formatDate(detail.startTime)} />
                  <DetailField icon={CalendarClock} label="End time" value={formatDate(detail.endTime)} />
                  <DetailField icon={FileText} label="Starting price" value={formatCurrency(detail.startingPrice)} />
                  <DetailField icon={FileText} label="Current price" value={formatCurrency(detail.currentPrice)} />
                </div>

                {detail.status === 'REJECTED' && detail.rejectionReason && (
                  <div className="rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] p-4 text-[var(--color-warning-text)]">
                    <p className="mb-2 flex items-center gap-2 text-sm font-medium">
                      <AlertTriangle className="size-4" />
                      Rejection reason
                    </p>
                    <p className="text-sm leading-relaxed">{detail.rejectionReason}</p>
                  </div>
                )}

                {detail.status === 'CANCELLED' && detail.cancellationReason && (
                  <div className="rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] p-4 text-[var(--color-warning-text)]">
                    <p className="mb-2 flex items-center gap-2 text-sm font-medium">
                      <AlertTriangle className="size-4" />
                      Cancellation reason
                    </p>
                    <p className="text-sm leading-relaxed">{detail.cancellationReason}</p>
                  </div>
                )}

                <div>
                  <p className="mb-3 flex items-center gap-2 text-sm font-medium">
                    <History className="size-4" />
                    Status history
                  </p>
                  <div className="space-y-3">
                    {detail.statusHistory.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No history recorded.</p>
                    ) : (
                      detail.statusHistory.map((entry, index) => (
                        <div key={index} className="flex gap-3 rounded-lg border p-3">
                          <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                            <History className="size-4" />
                          </div>
                          <div className="min-w-0 flex-1 space-y-1">
                            <p className="text-sm font-medium">
                              {entry.fromStatus ? `${entry.fromStatus} → ${entry.toStatus}` : entry.toStatus}
                            </p>
                            {entry.reason && <p className="text-sm text-muted-foreground">{entry.reason}</p>}
                            <p className="text-xs text-muted-foreground">{formatDate(entry.createdAt)}</p>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </ScrollArea>
          ) : null}

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDetailModalOpen(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Moderation action dialog (reject / cancel / force-close) */}
      <Dialog open={actionType !== null} onOpenChange={(open) => !open && closeActionDialog()}>
        <DialogContent className="sm:max-w-md">
          {actionType && (
            <>
              <DialogHeader>
                <DialogTitle>{actionConfig[actionType].title}</DialogTitle>
                <DialogDescription>{actionConfig[actionType].description}</DialogDescription>
              </DialogHeader>

              <div className="space-y-5">
                <div className="rounded-lg border p-3">
                  <p className="truncate text-sm font-medium">{actionTarget?.title}</p>
                  <p className="font-mono text-xs text-muted-foreground">{actionTarget?.id}</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="action-reason">
                    Reason {actionConfig[actionType].reasonRequired ? '' : '(optional)'}
                  </Label>
                  <Textarea
                    id="action-reason"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder="Provide a clear reason for this moderation action."
                    className="min-h-28 resize-none"
                  />
                </div>

                <div className="flex gap-3 rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] p-3 text-[var(--color-warning-text)]">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                  <p className="text-sm">This action is recorded in the auction&apos;s status history and cannot be undone.</p>
                </div>
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={closeActionDialog} disabled={submitting}>
                  Cancel
                </Button>
                <Button onClick={handleConfirmAction} disabled={submitting} variant={actionConfig[actionType].variant}>
                  {submitting && <Loader2 className="size-4 animate-spin" />}
                  {actionConfig[actionType].confirmLabel}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function AuctionStatusBadge({ status }: { status: AdminAuctionStatus }) {
  const { label, variant, icon: Icon, pulse } = statusConfig[status]
  return (
    <Badge variant={variant} className="gap-1.5">
      {pulse && <span className="inline-block size-1.5 rounded-full bg-current animate-pulse" />}
      <Icon className="size-3" />
      {label}
    </Badge>
  )
}

function DetailField({
  icon: Icon,
  label,
  value,
}: {
  readonly icon: React.ComponentType<{ className?: string }>
  readonly label: string
  readonly value?: string | null
}) {
  return (
    <div className="rounded-lg border p-4">
      <p className="mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <Icon className="size-4" />
        {label}
      </p>
      <p className={cn('text-sm font-medium', !value && 'text-muted-foreground')}>{value || 'N/A'}</p>
    </div>
  )
}
