import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  BulkUpdateDto, NotificationDto, NotificationListParams, UnreadCountDto,
} from '@/types/api/notification.api'

const BASE = '/api/v1/notifications'

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      throw { status: response.status }
    }
    throw body
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : null) as T
}

export const notificationService = {
  async list(params: NotificationListParams = {}): Promise<ApiResponse<PageResponse<NotificationDto>>> {
    const query = new URLSearchParams()
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    if (params.read !== undefined) query.set('read', String(params.read))
    params.types?.forEach((type) => query.append('types', type))
    const search = params.search?.trim()
    if (search) query.set('search', search)
    return parse(await apiFetch(`${BASE}?${query}`))
  },

  async unreadCount(): Promise<ApiResponse<UnreadCountDto>> {
    return parse(await apiFetch(`${BASE}/unread-count`))
  },

  async markRead(id: string): Promise<ApiResponse<NotificationDto>> {
    return parse(await apiFetch(`${BASE}/${id}/read`, { method: 'PUT' }))
  },

  async markUnread(id: string): Promise<ApiResponse<NotificationDto>> {
    return parse(await apiFetch(`${BASE}/${id}/unread`, { method: 'PUT' }))
  },

  async markAllRead(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/mark-all-read`, { method: 'PUT' }))
  },

  async remove(id: string): Promise<ApiResponse<string>> {
    return parse(await apiFetch(`${BASE}/${id}`, { method: 'DELETE' }))
  },

  async removeRead(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/delete-read`, { method: 'DELETE' }))
  },

  async removeAll(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/delete-all`, { method: 'DELETE' }))
  },
}
