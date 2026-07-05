import { apiFetch } from "@/lib/apiClient";
import { type ApiResponse, type PageResponse } from "@/types/api/common.api";
import {
  AuditLogFilters,
  AuditLogResponse,
  type AdminUserProfileResponse,
  type AdminUserResponse,
  type AdminUserSortField,
  type AdminUserStatus,
  type EmailLogFilters,
  type EmailLogResponse,
  type EmailTestRequest,
  type NotificationTemplateRequest,
  type NotificationTemplateResponse,
  type SendTemplateEmailRequest,
  type SortDirection,
  type TemplateFilters,
} from "@/types/api/admin.api";
import {
  type AdminAuctionDetailResponse,
  type AdminAuctionFilters,
  type AdminAuctionSummaryResponse,
  type AuctionResponse,
} from "@/types/api/auction.api";

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json();
    throw error;
  }
  const result: ApiResponse<T> = await response.json();
  return result.data;
}

export const adminService = {
  async getUsers(
    page = 0,
    size = 10,
    sortBy: AdminUserSortField = "createdAt",
    direction: SortDirection = "desc"
  ): Promise<PageResponse<AdminUserResponse>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      direction,
    });
    const response = await apiFetch(`/api/v1/admin/users?${params}`);
    return parseResponse<PageResponse<AdminUserResponse>>(response);
  },

  async updateUserStatus(
    userId: string,
    status: AdminUserStatus,
    reason?: string
  ): Promise<AdminUserResponse> {
    const response = await apiFetch(`/api/v1/admin/users/${userId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status, reason }),
    });
    return parseResponse<AdminUserResponse>(response);
  },

  async getUserProfile(userId: string): Promise<AdminUserProfileResponse> {
    const response = await apiFetch(`/api/v1/admin/profiles/${userId}`);
    return parseResponse<AdminUserProfileResponse>(response);
  },

  async getTemplates(
    filters: TemplateFilters = {},
    page = 0,
    size = 10,
    sortBy = "createdAt",
    sortDir: SortDirection = "desc"
  ): Promise<PageResponse<NotificationTemplateResponse>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDir,
    });
    if (filters.type && filters.type !== "ALL") params.append("types", filters.type);
    if (filters.language && filters.language !== "ALL") params.append("languages", filters.language);
    if (filters.active !== undefined && filters.active !== "ALL") params.append("active", String(filters.active));
    if (filters.search?.trim()) params.append("search", filters.search.trim());
    const response = await apiFetch(`/api/v1/admin/templates?${params}`);
    return parseResponse<PageResponse<NotificationTemplateResponse>>(response);
  },

  async getTemplate(templateId: string): Promise<NotificationTemplateResponse> {
    const response = await apiFetch(`/api/v1/admin/templates/${templateId}`);
    return parseResponse<NotificationTemplateResponse>(response);
  },

  async createTemplate(
    request: NotificationTemplateRequest
  ): Promise<NotificationTemplateResponse> {
    const response = await apiFetch('/api/v1/admin/templates', {
      method: 'POST',
      body: JSON.stringify(request),
    });
    return parseResponse<NotificationTemplateResponse>(response);
  },

  async updateTemplate(
    templateId: string,
    request: NotificationTemplateRequest
  ): Promise<NotificationTemplateResponse> {
    const response = await apiFetch(`/api/v1/admin/templates/${templateId}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    });
    return parseResponse<NotificationTemplateResponse>(response);
  },

  async testTemplate(
    templateId: string,
    request: EmailTestRequest
  ): Promise<string> {
    const response = await apiFetch(`/api/v1/admin/templates/${templateId}/test`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
    return parseResponse<string>(response);
  },

  async sendTemplateToGroup(
    templateId: string,
    request: SendTemplateEmailRequest
  ): Promise<string> {
    const response = await apiFetch(`/api/v1/admin/templates/${templateId}/send`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
    return parseResponse<string>(response);
  },

  async getEmailLogs(
    filters: EmailLogFilters = {},
    page = 0,
    size = 10,
    sortBy = "createdAt",
    sortDir: SortDirection = "desc"
  ): Promise<PageResponse<EmailLogResponse>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDir,
    });
    if (filters.recipientEmail?.trim()) params.append("recipientEmail", filters.recipientEmail.trim());
    if (filters.templateName?.trim()) params.append("templateNames", filters.templateName.trim());
    if (filters.status && filters.status !== "ALL") params.append("statuses", filters.status);
    if (filters.search?.trim()) params.append("search", filters.search.trim());
    const response = await apiFetch(`/api/v1/admin/email-logs?${params}`);
    return parseResponse<PageResponse<EmailLogResponse>>(response);
  },

  async getAuditLogs(
    filters: AuditLogFilters = {},
    page = 0,
    size = 20,
    sortBy = "createdAt",
    sortDir: SortDirection = "desc"
  ): Promise<PageResponse<AuditLogResponse>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDir,
    });
    if (filters.actorEmail?.trim()) params.append("actorEmail", filters.actorEmail.trim());
    if (filters.action) params.append("action", filters.action);
    if (filters.fromDate) params.append("fromDate", filters.fromDate);
    if (filters.toDate) params.append("toDate", filters.toDate);
    const response = await apiFetch(`/api/v1/admin/audit-logs?${params}`);
    return parseResponse<PageResponse<AuditLogResponse>>(response);
  },

  async getAuctions(
    filters: AdminAuctionFilters = {},
    page = 0,
    size = 20,
    sortBy = "createdAt",
    sortDir: SortDirection = "desc"
  ): Promise<PageResponse<AdminAuctionSummaryResponse>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDir,
    });
    if (filters.status && filters.status !== "ALL") params.append("status", filters.status);
    if (filters.categoryId) params.append("categoryId", filters.categoryId);
    if (filters.sellerId) params.append("sellerId", filters.sellerId);
    if (filters.q?.trim()) params.append("q", filters.q.trim());
    const response = await apiFetch(`/api/v1/admin/auctions?${params}`);
    return parseResponse<PageResponse<AdminAuctionSummaryResponse>>(response);
  },

  async getAuctionDetail(auctionId: string): Promise<AdminAuctionDetailResponse> {
    const response = await apiFetch(`/api/v1/admin/auctions/${auctionId}`);
    return parseResponse<AdminAuctionDetailResponse>(response);
  },

  async rejectAuction(auctionId: string, reason: string): Promise<AuctionResponse> {
    const response = await apiFetch(`/api/v1/admin/auctions/${auctionId}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    return parseResponse<AuctionResponse>(response);
  },

  async cancelAuction(auctionId: string, reason: string): Promise<AuctionResponse> {
    const response = await apiFetch(`/api/v1/admin/auctions/${auctionId}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    return parseResponse<AuctionResponse>(response);
  },

  async forceCloseAuction(auctionId: string, reason?: string): Promise<AuctionResponse> {
    const response = await apiFetch(`/api/v1/admin/auctions/${auctionId}/force-close`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    return parseResponse<AuctionResponse>(response);
  },
};
