import { type ApiResponse, type PageResponse } from "@/types/api";
import {
  type AdminUserProfileResponse,
  type AdminUserResponse,
  type AdminUserSortField,
  type AdminUserStatus,
  type SortDirection,
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
};
