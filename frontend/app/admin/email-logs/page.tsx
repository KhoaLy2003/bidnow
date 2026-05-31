'use client'

import type { ComponentProps, ComponentType } from 'react'
import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, Clock, Mail, RotateCcw, Search } from 'lucide-react'
import { toast } from 'sonner'

import { adminService } from '@/services/adminService'
import { type EmailDeliveryStatus, type EmailLogFilters, type EmailLogResponse } from '@/types/api/admin.api'
import { useAuthStore } from '@/store/authStore'
import { formatDate, getErrorMessage, DEFAULT_PAGE_SIZE, getPaginationRange } from '@/lib/utils'
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
import { Input } from '@/components/ui/input'
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

const PAGE_SIZE = DEFAULT_PAGE_SIZE
const SKELETON_ROW_KEYS = ['sk-0', 'sk-1', 'sk-2', 'sk-3', 'sk-4'] as const

export default function AdminEmailLogsPage() {
  const { accessToken } = useAuthStore()
  const [logs, setLogs] = useState<EmailLogResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [filters, setFilters] = useState<EmailLogFilters>({
    status: 'ALL',
    search: '',
  })

  const fetchLogs = useCallback(async () => {
    if (!accessToken) return
    setLoading(true)

    try {
      const result = await adminService.getEmailLogs(accessToken, filters, page, PAGE_SIZE)
      setLogs(result.data)
      setTotalPages(result.pagination.totalPages)
      setTotalElements(result.pagination.total)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to load email logs'))
    } finally {
      setLoading(false)
    }
  }, [accessToken, filters, page])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  const { start: showingStart, end: showingEnd } = getPaginationRange(page, PAGE_SIZE, totalElements)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-medium">Email Logs</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Monitor email delivery records produced by the media service.
        </p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Delivery Logs</CardTitle>
              <CardDescription>{totalElements} email delivery records.</CardDescription>
            </div>
            <Button variant="outline" onClick={fetchLogs} disabled={loading}>
              <RotateCcw className="size-4" />
              Refresh
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-2 px-4 lg:grid-cols-[1fr_200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={filters.search ?? ''}
                onChange={(event) => {
                  setPage(0)
                  setFilters((current) => ({ ...current, search: event.target.value }))
                }}
                placeholder="Search recipient, template or subject"
                className="pl-9"
              />
            </div>

            <Select
              value={filters.status ?? 'ALL'}
              onValueChange={(value) => {
                setPage(0)
                setFilters((current) => ({ ...current, status: value as EmailDeliveryStatus | 'ALL' }))
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue>{(value: string | null) => value === 'ALL' || !value ? 'All statuses' : value}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All statuses</SelectItem>
                <SelectItem value="SENT">Sent</SelectItem>
                <SelectItem value="FAILED">Failed</SelectItem>
                <SelectItem value="PENDING">Pending</SelectItem>
                <SelectItem value="RETRY">Retry</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4">Recipient</TableHead>
                <TableHead>Template</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Retries</TableHead>
                <TableHead>Sent</TableHead>
                <TableHead className="pr-4">Failure</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && SKELETON_ROW_KEYS.map((key) => (
                <TableRow key={key}>
                  <TableCell colSpan={7} className="px-4">
                    <Skeleton className="h-8 w-full" />
                  </TableCell>
                </TableRow>
              ))}
              {!loading && logs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="h-32 text-center text-muted-foreground">
                    No email logs found.
                  </TableCell>
                </TableRow>
              )}
              {!loading && logs.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="px-4">
                    <div className="flex items-center gap-2">
                      <Mail className="size-4 text-muted-foreground" />
                      <span className="font-medium">{log.recipientEmail}</span>
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{log.templateName}</TableCell>
                  <TableCell className="max-w-[260px] truncate text-muted-foreground">{log.subject}</TableCell>
                  <TableCell>
                    <EmailStatusBadge status={log.status} />
                  </TableCell>
                  <TableCell>{log.retryCount}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {log.sentAt ? formatDate(log.sentAt) : 'Not sent'}
                  </TableCell>
                  <TableCell className="max-w-[260px] truncate pr-4 text-muted-foreground">
                    {log.failureReason || '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
        <CardFooter className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <p className="text-sm text-muted-foreground">
            Showing <span className="font-medium text-foreground">{showingStart}</span>-
            <span className="font-medium text-foreground">{showingEnd}</span> of{' '}
            <span className="font-medium text-foreground">{totalElements}</span>
          </p>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" disabled={page === 0 || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}>
              <ChevronLeft className="size-4" />
            </Button>
            <span className="min-w-24 text-center text-sm text-muted-foreground">
              Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
            </span>
            <Button variant="outline" size="icon" disabled={totalPages === 0 || page >= totalPages - 1 || loading} onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}>
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}

function EmailStatusBadge({ status }: { status: EmailDeliveryStatus }) {
  const config: Record<EmailDeliveryStatus, { label: string; icon: ComponentType<{ className?: string }>; variant: ComponentProps<typeof Badge>['variant'] }> = {
    SENT: { label: 'Sent', icon: CheckCircle2, variant: 'won' },
    FAILED: { label: 'Failed', icon: AlertTriangle, variant: 'destructive' },
    PENDING: { label: 'Pending', icon: Clock, variant: 'secondary' },
    RETRY: { label: 'Retry', icon: RotateCcw, variant: 'ending-soon' },
  }

  const item = config[status] ?? config.PENDING
  const Icon = item.icon

  return (
    <Badge variant={item.variant} className="gap-1.5">
      <Icon className="size-3" />
      {item.label}
    </Badge>
  )
}
