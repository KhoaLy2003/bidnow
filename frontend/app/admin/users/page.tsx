'use client'

import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  BadgeCheck,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Clock,
  ExternalLink,
  Filter,
  Gavel,
  History,
  Loader2,
  Mail,
  MoreHorizontal,
  ReceiptText,
  RotateCcwKey,
  ShieldAlert,
  ShieldCheck,
  User as UserIcon,
  UserCheck,
  UserX,
  X,
} from 'lucide-react'
import { toast } from 'sonner'

import { adminService } from '@/services/adminService'
import {
  type AdminUserProfileResponse,
  type AdminUserResponse,
  type AdminUserSortField,
  type AdminUserStatus,
  type SortDirection,
} from '@/types/admin'
import { useAuthStore } from '@/store/authStore'
import { cn, formatDate } from '@/lib/utils'
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

type StatusFilter = AdminUserStatus | 'ALL'
type IconComponent = React.ComponentType<{ className?: string }>

interface ApiError {
  message?: string
}

const PAGE_SIZE = 10

const statusOptions: Array<{ label: string; value: StatusFilter }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Pending verification', value: 'PENDING_VERIFICATION' },
  { label: 'Suspended', value: 'SUSPENDED' },
  { label: 'Banned', value: 'BANNED' },
]

const sortOptions: Array<{ label: string; value: AdminUserSortField }> = [
  { label: 'Created date', value: 'createdAt' },
  { label: 'Last login', value: 'lastLoginAt' },
  { label: 'Email', value: 'email' },
]

const profilePlaceholderPanels: Array<{
  icon: IconComponent
  title: string
  description: string
}> = [
  {
    icon: Gavel,
    title: 'Auction History',
    description: 'Seller and bidder history will be available here.',
  },
  {
    icon: ReceiptText,
    title: 'Wallet Transactions',
    description: 'Deposits, withdrawals, bids, and refunds will be available here.',
  },
  {
    icon: RotateCcwKey,
    title: 'Password Reset',
    description: 'Admin-triggered password reset email controls will be available here.',
  },
  {
    icon: History,
    title: 'Audit Trail',
    description: 'Moderation action history with admin ID, timestamp, and reason will be available here.',
  },
]

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as ApiError).message
    if (message) return message
  }

  return fallback
}

export default function UserManagementPage() {
  const { accessToken } = useAuthStore()
  const [users, setUsers] = useState<AdminUserResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [sortBy, setSortBy] = useState<AdminUserSortField>('createdAt')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

  const [isStatusModalOpen, setIsStatusModalOpen] = useState(false)
  const [selectedUser, setSelectedUser] = useState<AdminUserResponse | null>(null)
  const [newStatus, setNewStatus] = useState<AdminUserStatus>('ACTIVE')
  const [statusReason, setStatusReason] = useState('')
  const [updating, setUpdating] = useState(false)

  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [userDetails, setUserDetails] = useState<AdminUserProfileResponse | null>(null)
  const [profileUser, setProfileUser] = useState<AdminUserResponse | null>(null)

  const fetchUsers = useCallback(async () => {
    if (!accessToken) return
    setLoading(true)

    try {
      const result = await adminService.getUsers(accessToken, page, PAGE_SIZE, sortBy, sortDirection)
      setUsers(result.data)
      setTotalPages(result.pagination.totalPages)
      setTotalElements(result.pagination.total)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to fetch users'))
    } finally {
      setLoading(false)
    }
  }, [accessToken, page, sortBy, sortDirection])

  useEffect(() => {
    const task = window.setTimeout(() => {
      fetchUsers()
    }, 0)

    return () => window.clearTimeout(task)
  }, [fetchUsers])

  const filteredUsers = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()

    return users.filter((user) => {
      const matchesQuery =
        !normalizedQuery ||
        user.email.toLowerCase().includes(normalizedQuery) ||
        user.id.toLowerCase().includes(normalizedQuery)
      const matchesStatus = statusFilter === 'ALL' || user.status === statusFilter

      return matchesQuery && matchesStatus
    })
  }, [query, statusFilter, users])

  const handleUpdateStatus = async () => {
    if (!accessToken || !selectedUser) return

    setUpdating(true)
    try {
      await adminService.updateUserStatus(accessToken, selectedUser.id, newStatus, statusReason.trim() || undefined)
      toast.success(`User status updated to ${newStatus}`)
      setIsStatusModalOpen(false)
      setSelectedUser(null)
      setStatusReason('')
      await fetchUsers()
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to update status'))
    } finally {
      setUpdating(false)
    }
  }

  const handleViewProfile = async (user: AdminUserResponse) => {
    if (!accessToken) return

    setProfileUser(user)
    setUserDetails(null)
    setProfileLoading(true)
    setIsProfileModalOpen(true)

    try {
      const data = await adminService.getUserProfile(accessToken, user.id)
      setUserDetails(data)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to fetch user profile'))
      setIsProfileModalOpen(false)
    } finally {
      setProfileLoading(false)
    }
  }

  const openStatusModal = (user: AdminUserResponse, status: AdminUserStatus) => {
    setSelectedUser(user)
    setNewStatus(status)
    setStatusReason('')
    setIsStatusModalOpen(true)
  }

  const hasFilters = query.trim().length > 0 || statusFilter !== 'ALL'
  const showingStart = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const showingEnd = Math.min((page + 1) * PAGE_SIZE, totalElements)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">User Management</h1>
          <p className="text-sm text-muted-foreground">
            Monitor and moderate {totalElements} registered users on the platform.
          </p>
        </div>

        <div className="grid gap-2 sm:grid-cols-[minmax(220px,1fr)_170px_170px_auto] lg:w-[720px]">
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search email or ID"
              className="pl-9"
            />
          </div>

          <Select value={statusFilter} onValueChange={(value) => setStatusFilter((value ?? 'ALL') as StatusFilter)}>
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
              setPage(0)
              setSortBy((value ?? 'createdAt') as AdminUserSortField)
            }}
          >
            <SelectTrigger className="w-full">
              <SelectValue>{(value: AdminUserSortField | null) => sortOptions.find((option) => option.value === value)?.label ?? 'Created date'}</SelectValue>
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
          <p className="text-sm text-muted-foreground">
            Showing {filteredUsers.length} matching users on this page.
          </p>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setQuery('')
              setStatusFilter('ALL')
            }}
          >
            <X className="size-4" />
            Clear
          </Button>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Accounts</CardTitle>
          <CardDescription>
            Review account state, verification, login activity, and moderation actions.
          </CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40 hover:bg-muted/40">
                <TableHead className="px-4">User</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last login</TableHead>
                <TableHead>Joined</TableHead>
                <TableHead className="pr-4 text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    <TableCell className="px-4" colSpan={6}>
                      <div className="flex items-center gap-3 py-2">
                        <Skeleton className="size-10 rounded-full" />
                        <div className="space-y-2">
                          <Skeleton className="h-4 w-56" />
                          <Skeleton className="h-3 w-28" />
                        </div>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              ) : filteredUsers.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-32 text-center text-muted-foreground">
                    No users found.
                  </TableCell>
                </TableRow>
              ) : (
                filteredUsers.map((user) => (
                  <TableRow
                    key={user.id}
                    tabIndex={0}
                    role="button"
                    onClick={() => handleViewProfile(user)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        handleViewProfile(user)
                      }
                    }}
                    className="cursor-pointer"
                  >
                    <TableCell className="px-4">
                      <div className="flex items-center gap-3">
                        <div className="flex size-10 items-center justify-center rounded-full border bg-accent text-sm font-semibold text-accent-foreground">
                          {user.email.charAt(0).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="truncate font-medium text-foreground">{user.email}</span>
                            {user.isEmailVerified && <BadgeCheck className="size-4 shrink-0 text-[var(--color-success-default)]" />}
                          </div>
                          <p className="font-mono text-xs text-muted-foreground">ID: {user.id.slice(0, 8)}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={user.role === 'ADMIN' ? 'default' : 'secondary'}>{user.role}</Badge>
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={user.status} />
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Never'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(user.createdAt)}</TableCell>
                    <TableCell className="pr-4 text-right" onClick={(event) => event.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" />}>
                          <MoreHorizontal className="size-4" />
                          <span className="sr-only">Open user actions</span>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-48">
                          <DropdownMenuGroup>
                            <DropdownMenuLabel>Moderation</DropdownMenuLabel>
                            <DropdownMenuItem onClick={() => handleViewProfile(user)}>
                              <ExternalLink className="size-4" />
                              View profile
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                          <DropdownMenuSeparator />
                          <DropdownMenuGroup>
                            {user.status === 'ACTIVE' ? (
                              <DropdownMenuItem onClick={() => openStatusModal(user, 'SUSPENDED')}>
                                <ShieldAlert className="size-4" />
                                Suspend account
                              </DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem onClick={() => openStatusModal(user, 'ACTIVE')}>
                                <UserCheck className="size-4" />
                                Activate account
                              </DropdownMenuItem>
                            )}
                            {user.status !== 'BANNED' && (
                              <DropdownMenuItem variant="destructive" onClick={() => openStatusModal(user, 'BANNED')}>
                                <UserX className="size-4" />
                                Ban account
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuGroup>
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
            Showing <span className="font-medium text-foreground">{showingStart}</span>-
            <span className="font-medium text-foreground">{showingEnd}</span> of{' '}
            <span className="font-medium text-foreground">{totalElements}</span>
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
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
              onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
              disabled={totalPages === 0 || page >= totalPages - 1 || loading}
            >
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </CardFooter>
      </Card>

      <Dialog open={isProfileModalOpen} onOpenChange={setIsProfileModalOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>User profile</DialogTitle>
            <DialogDescription>Identity and profile details for the selected account.</DialogDescription>
          </DialogHeader>

          {profileLoading ? (
            <div className="space-y-4">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : (
            <ScrollArea className="max-h-[65vh] pr-3">
              <div className="space-y-6">
                <div className="flex items-start gap-4 rounded-lg border p-4">
                  <div className="flex size-14 shrink-0 items-center justify-center rounded-full bg-accent text-lg font-semibold text-accent-foreground">
                    {(profileUser?.email ?? '?').charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1 space-y-1">
                    <h3 className="truncate text-lg font-semibold">
                      {userDetails?.displayName || profileUser?.email?.split('@')[0] || 'Unknown user'}
                    </h3>
                    <p className="flex items-center gap-2 truncate text-sm text-muted-foreground">
                      <Mail className="size-4" />
                      {profileUser?.email}
                    </p>
                    <div className="flex flex-wrap gap-2 pt-2">
                      {profileUser && <StatusBadge status={profileUser.status} />}
                      {profileUser && <Badge variant={profileUser.role === 'ADMIN' ? 'default' : 'secondary'}>{profileUser.role}</Badge>}
                      <Badge variant={profileUser?.isEmailVerified ? 'won' : 'outline'}>
                        {profileUser?.isEmailVerified ? 'Verified' : 'Unverified'}
                      </Badge>
                    </div>
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <ProfileField icon={UserIcon} label="Display name" value={userDetails?.displayName} />
                  <ProfileField icon={Calendar} label="Member since" value={userDetails?.createdAt ? formatDate(userDetails.createdAt) : undefined} />
                  <ProfileField icon={ShieldCheck} label="Language" value={userDetails?.language} />
                  <ProfileField icon={Clock} label="Timezone" value={userDetails?.timezone} />
                  <ProfileField icon={Mail} label="Phone" value={userDetails?.phoneNumber} />
                  <ProfileField icon={BadgeCheck} label="Currency" value={userDetails?.currency} />
                </div>

                {(userDetails?.address || userDetails?.city || userDetails?.country || userDetails?.postalCode) && (
                  <div className="rounded-lg border p-4">
                    <p className="mb-2 text-sm font-medium">Address</p>
                    <p className="text-sm text-muted-foreground">
                      {[userDetails.address, userDetails.city, userDetails.country, userDetails.postalCode].filter(Boolean).join(', ')}
                    </p>
                  </div>
                )}

                {userDetails?.bio && (
                  <div className="rounded-lg border p-4">
                    <p className="mb-2 text-sm font-medium">Bio</p>
                    <p className="text-sm leading-relaxed text-muted-foreground">{userDetails.bio}</p>
                  </div>
                )}

                {profileUser?.statusReason && (
                  <div className="rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] p-4 text-[var(--color-warning-text)]">
                    <p className="mb-2 flex items-center gap-2 text-sm font-medium">
                      <AlertTriangle className="size-4" />
                      Moderation note
                    </p>
                    <p className="text-sm leading-relaxed">{profileUser.statusReason}</p>
                  </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                  {profilePlaceholderPanels.map((panel) => (
                    <PlaceholderPanel key={panel.title} {...panel} />
                  ))}
                </div>
              </div>
            </ScrollArea>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsProfileModalOpen(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isStatusModalOpen} onOpenChange={setIsStatusModalOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{newStatus === 'ACTIVE' ? 'Activate account' : 'Update account status'}</DialogTitle>
            <DialogDescription>
              This action updates account access and records a moderation reason.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-5">
            <div className="flex items-center gap-3 rounded-lg border p-3">
              <div className="flex size-10 items-center justify-center rounded-full bg-muted font-semibold">
                {selectedUser?.email.charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{selectedUser?.email}</p>
                <p className="font-mono text-xs text-muted-foreground">{selectedUser?.id}</p>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="status-reason">Reason</Label>
              <Textarea
                id="status-reason"
                value={statusReason}
                onChange={(event) => setStatusReason(event.target.value)}
                placeholder="Provide a clear reason for this moderation action."
                className="min-h-28 resize-none"
              />
            </div>

            {newStatus !== 'ACTIVE' && (
              <div className="flex gap-3 rounded-lg border border-[var(--color-warning-border)] bg-[var(--color-warning-subtle)] p-3 text-[var(--color-warning-text)]">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                <p className="text-sm">
                  Suspended and banned users cannot sign in or perform account actions.
                </p>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsStatusModalOpen(false)} disabled={updating}>
              Cancel
            </Button>
            <Button
              onClick={handleUpdateStatus}
              disabled={updating}
              variant={newStatus === 'ACTIVE' ? 'default' : 'destructive'}
            >
              {updating && <Loader2 className="size-4 animate-spin" />}
              Confirm {newStatus}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function StatusBadge({ status }: { status: AdminUserStatus }) {
  const configs: Record<
    AdminUserStatus,
    {
      label: string
      variant: React.ComponentProps<typeof Badge>['variant']
      icon: IconComponent
    }
  > = {
    ACTIVE: {
      label: 'Active',
      variant: 'won',
      icon: UserCheck,
    },
    PENDING_VERIFICATION: {
      label: 'Pending',
      variant: 'secondary',
      icon: Clock,
    },
    SUSPENDED: {
      label: 'Suspended',
      variant: 'ending-soon',
      icon: ShieldAlert,
    },
    BANNED: {
      label: 'Banned',
      variant: 'destructive',
      icon: UserX,
    },
  }

  const config = configs[status]
  const Icon = config.icon

  return (
    <Badge variant={config.variant} className="gap-1.5">
      <Icon className="size-3" />
      {config.label}
    </Badge>
  )
}

function ProfileField({
  icon: Icon,
  label,
  value,
}: {
  icon: IconComponent
  label: string
  value?: string | null
}) {
  return (
    <div className="rounded-lg border p-4">
      <p className="mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <Icon className="size-4" />
        {label}
      </p>
      <p className={cn('text-sm font-medium', !value && 'text-muted-foreground')}>{value || 'Not provided'}</p>
    </div>
  )
}

function PlaceholderPanel({
  icon: Icon,
  title,
  description,
}: {
  icon: IconComponent
  title: string
  description: string
}) {
  return (
    <div className="rounded-lg border border-dashed p-4">
      <div className="mb-3 flex size-9 items-center justify-center rounded-lg bg-muted text-muted-foreground">
        <Icon className="size-4" />
      </div>
      <p className="text-sm font-medium">{title}</p>
      <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{description}</p>
    </div>
  )
}
