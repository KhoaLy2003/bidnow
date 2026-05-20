'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Edit,
  Eye,
  Loader2,
  Mail,
  Plus,
  Search,
  Send,
  XCircle,
} from 'lucide-react'
import { toast } from 'sonner'

import { adminService } from '@/services/adminService'
import {
  type NotificationTemplateLanguage,
  type NotificationTemplateRequest,
  type NotificationTemplateResponse,
  type NotificationTemplateType,
  type TemplateFilters,
} from '@/types/admin'
import { useAuthStore } from '@/store/authStore'
import { formatDate } from '@/lib/utils'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'

const PAGE_SIZE = 10

const emptyTemplateForm: NotificationTemplateRequest = {
  name: '',
  type: 'EMAIL',
  language: 'EN',
  subject: '',
  bodyHtml: '',
  bodyText: '',
  variables: [],
  active: true,
}

interface ApiError {
  message?: string
}

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as ApiError).message
    if (message) return message
  }

  return fallback
}

function variablesToText(variables?: string[] | null) {
  return variables?.join(', ') ?? ''
}

function textToVariables(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export default function AdminTemplatesPage() {
  const { accessToken } = useAuthStore()
  const [templates, setTemplates] = useState<NotificationTemplateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [filters, setFilters] = useState<TemplateFilters>({
    type: 'ALL',
    language: 'ALL',
    active: 'ALL',
    search: '',
  })

  const [selectedTemplate, setSelectedTemplate] = useState<NotificationTemplateResponse | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [testOpen, setTestOpen] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<NotificationTemplateResponse | null>(null)
  const [formData, setFormData] = useState<NotificationTemplateRequest>(emptyTemplateForm)
  const [variablesText, setVariablesText] = useState('')
  const [saving, setSaving] = useState(false)
  const [testEmail, setTestEmail] = useState('')
  const [testVariables, setTestVariables] = useState('{}')
  const [testing, setTesting] = useState(false)
  const [sendType, setSendType] = useState<'TEST' | 'GROUP'>('TEST')
  const [sendToAllActive, setSendToAllActive] = useState(false)
  const [recipientEmailsStr, setRecipientEmailsStr] = useState('')

  const fetchTemplates = useCallback(async () => {
    if (!accessToken) return
    setLoading(true)

    try {
      const result = await adminService.getTemplates(accessToken, filters, page, PAGE_SIZE)
      setTemplates(result.data)
      setTotalPages(result.pagination.totalPages)
      setTotalElements(result.pagination.total)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to load templates'))
    } finally {
      setLoading(false)
    }
  }, [accessToken, filters, page])

  useEffect(() => {
    const task = window.setTimeout(fetchTemplates, 0)
    return () => window.clearTimeout(task)
  }, [fetchTemplates])

  const showingStart = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const showingEnd = Math.min((page + 1) * PAGE_SIZE, totalElements)

  const selectedVariables = useMemo(
    () => variablesToText(selectedTemplate?.variables),
    [selectedTemplate]
  )

  const openDetails = async (template: NotificationTemplateResponse) => {
    if (!accessToken) return
    setDetailsOpen(true)

    try {
      const details = await adminService.getTemplate(accessToken, template.id)
      setSelectedTemplate(details)
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to load template details'))
      setDetailsOpen(false)
    }
  }

  const openCreateForm = () => {
    setEditingTemplate(null)
    setFormData(emptyTemplateForm)
    setVariablesText('')
    setFormOpen(true)
  }

  const openEditForm = (template: NotificationTemplateResponse) => {
    setEditingTemplate(template)
    setFormData({
      name: template.name,
      type: template.type,
      language: template.language,
      subject: template.subject ?? '',
      bodyHtml: template.bodyHtml ?? '',
      bodyText: template.bodyText,
      variables: template.variables ?? [],
      active: template.active,
    })
    setVariablesText(variablesToText(template.variables))
    setFormOpen(true)
  }

  const handleSubmit = async () => {
    if (!accessToken) return
    setSaving(true)

    const request: NotificationTemplateRequest = {
      ...formData,
      variables: textToVariables(variablesText),
    }

    try {
      if (editingTemplate) {
        await adminService.updateTemplate(accessToken, editingTemplate.id, request)
        toast.success('Template updated')
      } else {
        await adminService.createTemplate(accessToken, request)
        toast.success('Template created')
      }
      setFormOpen(false)
      await fetchTemplates()
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, 'Failed to save template'))
    } finally {
      setSaving(false)
    }
  }

  const openTestDialog = (template: NotificationTemplateResponse) => {
    setSelectedTemplate(template)
    setTestEmail('')
    setTestVariables('{}')
    setSendType('TEST')
    setSendToAllActive(false)
    setRecipientEmailsStr('')
    setTestOpen(true)
  }

  const handleSendEmail = async () => {
    if (!accessToken || !selectedTemplate) return
    setTesting(true)

    try {
      const variables = JSON.parse(testVariables || '{}') as Record<string, unknown>
      
      if (sendType === 'TEST') {
        const message = await adminService.testTemplate(accessToken, selectedTemplate.id, {
          recipientEmail: testEmail,
          variables,
        })
        toast.success(message)
      } else {
        const recipientEmails = recipientEmailsStr
          ? recipientEmailsStr.split(',').map((e) => e.trim()).filter(Boolean)
          : undefined

        const message = await adminService.sendTemplateToGroup(accessToken, selectedTemplate.id, {
          sendToAllActive,
          recipientEmails,
          variables,
        })
        toast.success(message)
      }
      setTestOpen(false)
    } catch (error: unknown) {
      const actionName = sendType === 'TEST' ? 'send test email' : 'send group emails'
      toast.error(getErrorMessage(error, `Failed to ${actionName}`))
    } finally {
      setTesting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-2xl font-medium">Email Templates</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage existing multilingual email templates and send test deliveries.
          </p>
        </div>
        <Button onClick={openCreateForm}>
          <Plus className="size-4" />
          Create Template
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Templates</CardTitle>
          <CardDescription>{totalElements} templates available.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-2 lg:grid-cols-[minmax(220px,1fr)_150px_150px_150px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={filters.search ?? ''}
                onChange={(event) => {
                  setPage(0)
                  setFilters((current) => ({ ...current, search: event.target.value }))
                }}
                placeholder="Search name or subject"
                className="pl-9"
              />
            </div>

            <Select
              value={filters.type ?? 'ALL'}
              onValueChange={(value) => {
                setPage(0)
                setFilters((current) => ({ ...current, type: value as NotificationTemplateType | 'ALL' }))
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue>{(value: string | null) => value === 'ALL' || !value ? 'All types' : value}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All types</SelectItem>
                <SelectItem value="EMAIL">Email</SelectItem>
              </SelectContent>
            </Select>

            <Select
              value={filters.language ?? 'ALL'}
              onValueChange={(value) => {
                setPage(0)
                setFilters((current) => ({ ...current, language: value as NotificationTemplateLanguage | 'ALL' }))
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue>{(value: string | null) => value === 'ALL' || !value ? 'All languages' : value}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All languages</SelectItem>
                <SelectItem value="EN">EN</SelectItem>
                <SelectItem value="VI">VI</SelectItem>
              </SelectContent>
            </Select>

            <Select
              value={String(filters.active ?? 'ALL')}
              onValueChange={(value) => {
                setPage(0)
                setFilters((current) => ({
                  ...current,
                  active: value === 'ALL' ? 'ALL' : value === 'true',
                }))
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue>{(value: string | null) => value === 'ALL' || !value ? 'All states' : value === 'true' ? 'Active' : 'Inactive'}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All states</SelectItem>
                <SelectItem value="true">Active</SelectItem>
                <SelectItem value="false">Inactive</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Language</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Updated</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    <TableCell colSpan={6}>
                      <Skeleton className="h-8 w-full" />
                    </TableCell>
                  </TableRow>
                ))
              ) : templates.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="h-32 text-center text-muted-foreground">
                    No templates found.
                  </TableCell>
                </TableRow>
              ) : (
                templates.map((template) => (
                  <TableRow
                    key={template.id}
                    role="button"
                    tabIndex={0}
                    className="cursor-pointer"
                    onClick={() => openDetails(template)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        openDetails(template)
                      }
                    }}
                  >
                    <TableCell className="font-medium">{template.name}</TableCell>
                    <TableCell className="max-w-[280px] truncate text-muted-foreground">
                      {template.subject || 'No subject'}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{template.language}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={template.active ? 'won' : 'outline'}>
                        {template.active ? <CheckCircle2 className="size-3" /> : <XCircle className="size-3" />}
                        {template.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {template.updatedAt ? formatDate(template.updatedAt) : 'N/A'}
                    </TableCell>
                    <TableCell className="text-right" onClick={(event) => event.stopPropagation()}>
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openDetails(template)}>
                          <Eye className="size-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => openEditForm(template)}>
                          <Edit className="size-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => openTestDialog(template)}>
                          <Send className="size-4" />
                        </Button>
                      </div>
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

      <Dialog open={detailsOpen} onOpenChange={setDetailsOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{selectedTemplate?.name ?? 'Template details'}</DialogTitle>
            <DialogDescription>{selectedTemplate?.subject ?? 'Review the stored email template.'}</DialogDescription>
          </DialogHeader>
          {selectedTemplate && (
            <ScrollArea className="max-h-[70vh] pr-3">
              <div className="space-y-4">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">{selectedTemplate.type}</Badge>
                  <Badge variant="secondary">{selectedTemplate.language}</Badge>
                  <Badge variant={selectedTemplate.active ? 'won' : 'outline'}>{selectedTemplate.active ? 'Active' : 'Inactive'}</Badge>
                </div>
                <TemplateBlock label="Variables" value={selectedVariables || 'No variables'} />
                <TemplateBlock label="Text body" value={selectedTemplate.bodyText} multiline />
                {selectedTemplate.bodyHtml && (
                  <div className="space-y-2">
                    <p className="text-sm font-medium text-muted-foreground">HTML body</p>
                    <Tabs defaultValue="review" className="w-full">
                      <TabsList className="grid w-full grid-cols-2">
                        <TabsTrigger value="review">Review</TabsTrigger>
                        <TabsTrigger value="raw">Raw</TabsTrigger>
                      </TabsList>
                      <TabsContent value="review" className="mt-2 rounded-lg border bg-white p-4">
                        <div
                          className="prose prose-sm max-w-none"
                          dangerouslySetInnerHTML={{ __html: selectedTemplate.bodyHtml }}
                        />
                      </TabsContent>
                      <TabsContent value="raw" className="mt-2">
                        <div className="rounded-lg border bg-muted p-4 font-mono text-xs whitespace-pre-wrap">
                          {selectedTemplate.bodyHtml}
                        </div>
                      </TabsContent>
                    </Tabs>
                  </div>
                )}
              </div>
            </ScrollArea>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDetailsOpen(false)}>Close</Button>
            {selectedTemplate && <Button onClick={() => openEditForm(selectedTemplate)}>Edit</Button>}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editingTemplate ? 'Edit template' : 'Create template'}</DialogTitle>
            <DialogDescription>Update the existing template fields exposed by the backend API.</DialogDescription>
          </DialogHeader>
          <ScrollArea className="max-h-[70vh] pr-3">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="template-name">Name</Label>
                <Input id="template-name" value={formData.name} onChange={(event) => setFormData((current) => ({ ...current, name: event.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>Type</Label>
                <Select value={formData.type} onValueChange={(value) => setFormData((current) => ({ ...current, type: value as NotificationTemplateType }))}>
                  <SelectTrigger className="w-full">
                    <SelectValue>{(value: string | null) => value ?? 'EMAIL'}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EMAIL">EMAIL</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Language</Label>
                <Select value={formData.language} onValueChange={(value) => setFormData((current) => ({ ...current, language: value as NotificationTemplateLanguage }))}>
                  <SelectTrigger className="w-full">
                    <SelectValue>{(value: string | null) => value ?? 'EN'}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EN">EN</SelectItem>
                    <SelectItem value="VI">VI</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="template-subject">Subject</Label>
                <Input id="template-subject" value={formData.subject ?? ''} onChange={(event) => setFormData((current) => ({ ...current, subject: event.target.value }))} />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="template-vars">Variables</Label>
                <Input id="template-vars" value={variablesText} onChange={(event) => setVariablesText(event.target.value)} placeholder="userName, auctionTitle, actionUrl" />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="template-text">Text body</Label>
                <Textarea id="template-text" value={formData.bodyText} onChange={(event) => setFormData((current) => ({ ...current, bodyText: event.target.value }))} className="min-h-32 font-mono" />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="template-html">HTML body</Label>
                <Textarea id="template-html" value={formData.bodyHtml ?? ''} onChange={(event) => setFormData((current) => ({ ...current, bodyHtml: event.target.value }))} className="min-h-40 font-mono" />
              </div>
              <div className="flex items-center gap-3 sm:col-span-2">
                <Switch checked={formData.active} onCheckedChange={(checked) => setFormData((current) => ({ ...current, active: checked }))} />
                <Label>Active</Label>
              </div>
            </div>
          </ScrollArea>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFormOpen(false)} disabled={saving}>Cancel</Button>
            <Button onClick={handleSubmit} disabled={saving || !formData.name || !formData.bodyText}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={testOpen} onOpenChange={setTestOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Send email template</DialogTitle>
            <DialogDescription>{selectedTemplate?.name}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Tabs value={sendType} onValueChange={(val) => setSendType(val as 'TEST' | 'GROUP')} className="w-full">
              <TabsList className="grid w-full grid-cols-2 mb-2">
                <TabsTrigger value="TEST">Single Test Email</TabsTrigger>
                <TabsTrigger value="GROUP">Group Delivery</TabsTrigger>
              </TabsList>
              
              <TabsContent value="TEST" className="space-y-4 mt-2">
                <div className="space-y-2">
                  <Label htmlFor="test-email">Recipient Email Address</Label>
                  <Input
                    id="test-email"
                    type="email"
                    value={testEmail}
                    onChange={(event) => setTestEmail(event.target.value)}
                    placeholder="recipient@example.com"
                  />
                </div>
              </TabsContent>

              <TabsContent value="GROUP" className="space-y-4 mt-2">
                <div className="flex items-center justify-between rounded-lg border p-4 bg-muted/40">
                  <div className="space-y-0.5">
                    <Label className="text-sm font-medium">Send to All Active Users</Label>
                    <p className="text-xs text-muted-foreground">
                      Resolves active accounts via identity service.
                    </p>
                  </div>
                  <Switch
                    checked={sendToAllActive}
                    onCheckedChange={setSendToAllActive}
                  />
                </div>

                {!sendToAllActive && (
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="group-emails">Recipient Email Addresses</Label>
                      <Textarea
                        id="group-emails"
                        value={recipientEmailsStr}
                        onChange={(event) => setRecipientEmailsStr(event.target.value)}
                        placeholder="user1@example.com, user2@example.com"
                        className="min-h-16 text-sm"
                      />
                      <p className="text-[11px] text-muted-foreground">
                        Comma-separated list of target email addresses.
                      </p>
                    </div>
                  </div>
                )}
              </TabsContent>
            </Tabs>

            <div className="space-y-2">
              <Label htmlFor="test-vars">Variables (JSON)</Label>
              <Textarea
                id="test-vars"
                value={testVariables}
                onChange={(event) => setTestVariables(event.target.value)}
                className="min-h-24 font-mono text-xs"
              />
              <p className="text-[11px] text-muted-foreground">
                Input template variables as a valid JSON object.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTestOpen(false)} disabled={testing}>Cancel</Button>
            <Button
              onClick={handleSendEmail}
              disabled={
                testing ||
                (sendType === 'TEST'
                  ? !testEmail
                  : !sendToAllActive && !recipientEmailsStr)
              }
            >
              {testing ? <Loader2 className="size-4 animate-spin" /> : <Mail className="size-4" />}
              Send
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function TemplateBlock({ label, value, multiline = false }: { label: string; value: string; multiline?: boolean }) {
  return (
    <div className="rounded-lg border p-4">
      <p className="mb-2 text-sm font-medium text-muted-foreground">{label}</p>
      <p className={multiline ? 'whitespace-pre-wrap font-mono text-sm leading-relaxed' : 'text-sm'}>{value}</p>
    </div>
  )
}
