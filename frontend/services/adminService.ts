import { type ApiResponse, type PageResponse } from "@/types/api";
import {
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
} from "@/types/admin";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

function authHeaders(accessToken: string): HeadersInit {
  return {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };
}

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
    accessToken: string,
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

    const response = await fetch(
      `${API_URL}/api/v1/admin/users?${params.toString()}`,
      {
        headers: authHeaders(accessToken),
      }
    );

    return parseResponse<PageResponse<AdminUserResponse>>(response);
  },

  async updateUserStatus(
    accessToken: string,
    userId: string,
    status: AdminUserStatus,
    reason?: string
  ): Promise<AdminUserResponse> {
    const response = await fetch(`${API_URL}/api/v1/admin/users/${userId}/status`, {
      method: "PUT",
      headers: authHeaders(accessToken),
      body: JSON.stringify({ status, reason }),
    });

    return parseResponse<AdminUserResponse>(response);
  },

  async getUserProfile(
    accessToken: string,
    userId: string
  ): Promise<AdminUserProfileResponse> {
    const response = await fetch(`${API_URL}/api/v1/admin/profiles/${userId}`, {
      headers: authHeaders(accessToken),
    });

    return parseResponse<AdminUserProfileResponse>(response);
  },

  async getTemplates(
    accessToken: string,
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

    if (filters.type && filters.type !== "ALL") {
      params.append("types", filters.type);
    }

    if (filters.language && filters.language !== "ALL") {
      params.append("languages", filters.language);
    }

    if (filters.active !== undefined && filters.active !== "ALL") {
      params.append("active", String(filters.active));
    }

    if (filters.search?.trim()) {
      params.append("search", filters.search.trim());
    }

    const response = await fetch(
      `${API_URL}/api/v1/admin/templates?${params.toString()}`,
      {
        headers: authHeaders(accessToken),
      }
    );

    return parseResponse<PageResponse<NotificationTemplateResponse>>(response);
  },

  async getTemplate(
    accessToken: string,
    templateId: string
  ): Promise<NotificationTemplateResponse> {
    const response = await fetch(`${API_URL}/api/v1/admin/templates/${templateId}`, {
      headers: authHeaders(accessToken),
    });

    return parseResponse<NotificationTemplateResponse>(response);
  },

  async createTemplate(
    accessToken: string,
    request: NotificationTemplateRequest
  ): Promise<NotificationTemplateResponse> {
    const response = await fetch(`${API_URL}/api/v1/admin/templates`, {
      method: "POST",
      headers: authHeaders(accessToken),
      body: JSON.stringify(request),
    });

    return parseResponse<NotificationTemplateResponse>(response);
  },

  async updateTemplate(
    accessToken: string,
    templateId: string,
    request: NotificationTemplateRequest
  ): Promise<NotificationTemplateResponse> {
    const response = await fetch(`${API_URL}/api/v1/admin/templates/${templateId}`, {
      method: "PUT",
      headers: authHeaders(accessToken),
      body: JSON.stringify(request),
    });

    return parseResponse<NotificationTemplateResponse>(response);
  },

  async testTemplate(
    accessToken: string,
    templateId: string,
    request: EmailTestRequest
  ): Promise<string> {
    const response = await fetch(`${API_URL}/api/v1/admin/templates/${templateId}/test`, {
      method: "POST",
      headers: authHeaders(accessToken),
      body: JSON.stringify(request),
    });

    return parseResponse<string>(response);
  },

  async sendTemplateToGroup(
    accessToken: string,
    templateId: string,
    request: SendTemplateEmailRequest
  ): Promise<string> {
    const response = await fetch(`${API_URL}/api/v1/admin/templates/${templateId}/send`, {
      method: "POST",
      headers: authHeaders(accessToken),
      body: JSON.stringify(request),
    });

    return parseResponse<string>(response);
  },

  async getEmailLogs(
    accessToken: string,
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

    if (filters.recipientEmail?.trim()) {
      params.append("recipientEmail", filters.recipientEmail.trim());
    }

    if (filters.templateName?.trim()) {
      params.append("templateNames", filters.templateName.trim());
    }

    if (filters.status && filters.status !== "ALL") {
      params.append("statuses", filters.status);
    }

    if (filters.search?.trim()) {
      params.append("search", filters.search.trim());
    }

    const response = await fetch(
      `${API_URL}/api/v1/admin/email-logs?${params.toString()}`,
      {
        headers: authHeaders(accessToken),
      }
    );

    return parseResponse<PageResponse<EmailLogResponse>>(response);
  },
};
