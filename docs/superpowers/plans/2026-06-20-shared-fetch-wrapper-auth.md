# Shared Fetch Wrapper & Token Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a single `apiFetch` wrapper that auto-attaches JWT tokens, transparently refreshes on 401, and eliminates all manual token threading from services and components.

**Architecture:** A new `lib/apiClient.ts` reads from Zustand's `useAuthStore.getState()` to attach `Authorization` headers without requiring React context. On 401, it attempts one token refresh (serializing concurrent 401s behind a single in-flight promise) and retries the request once; if refresh also fails, it force-logs the user out and redirects to `/login?reason=session_expired`. All four service files drop their `token` parameter and switch from raw `fetch` to `apiFetch`; all 10 call-site files drop their `accessToken` pass-through.

**Tech Stack:** Next.js 16 (App Router), Zustand 5 (`persist` middleware), TypeScript strict mode, native `fetch`.

## Global Constraints

- `auth.service.ts` must remain on raw `fetch` — `apiFetch` imports it, circular import prevention.
- `mediaService.uploadToS3` must remain on raw `fetch` — it hits a third-party S3 URL with a presigned token, never our API.
- `Content-Type: application/json` is injected automatically by `apiFetch` unless the body is `FormData`.
- URL paths passed to `apiFetch` are root-relative (e.g. `/api/v1/auctions`). `apiFetch` prepends `NEXT_PUBLIC_API_URL`.
- No test suite exists (`npm run lint` is the only automated check). Run it only after Task 5 when all callers are migrated.
- Do not commit unless the user explicitly asks.

---

## File Map

| Status | File | Change |
|--------|------|--------|
| Create | `frontend/lib/apiClient.ts` | New shared fetch wrapper |
| Modify | `frontend/store/authStore.ts` | Add `setTokens` action |
| Modify | `frontend/services/auction.service.ts` | Remove `API_URL` + token params; use `apiFetch` |
| Modify | `frontend/services/user.service.ts` | Remove `API_URL` + token params; use `apiFetch` |
| Modify | `frontend/services/media.service.ts` | Remove `API_URL` + token params; use `apiFetch` (keep `uploadToS3` on raw `fetch`) |
| Modify | `frontend/services/adminService.ts` | Remove `API_URL`, `authHeaders`, token params; use `apiFetch` |
| Modify | `frontend/hooks/useProfile.ts` | Drop `accessToken` from store read and service calls |
| Modify | `frontend/hooks/useSecureImage.ts` | Drop `accessToken` from store read and service calls |
| Modify | `frontend/app/(dashboard)/seller/auctions/page.tsx` | Drop `accessToken` store read and service arg |
| Modify | `frontend/app/(dashboard)/seller/auctions/new/page.tsx` | Drop `accessToken` store read and service args |
| Modify | `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx` | Drop `accessToken` store reads and service args |
| Modify | `frontend/app/(dashboard)/profile/page.tsx` | Drop `accessToken` store read and service arg |
| Modify | `frontend/app/admin/users/page.tsx` | Drop `accessToken` store read and service args |
| Modify | `frontend/app/admin/templates/page.tsx` | Drop `accessToken` store read and service args |
| Modify | `frontend/app/admin/email-logs/page.tsx` | Drop `accessToken` store read and service args |
| Modify | `frontend/app/admin/audit-logs/page.tsx` | Drop `accessToken` store read and service args |

---

## Task 1: Foundation — `authStore.setTokens` + `lib/apiClient.ts`

**Files:**
- Modify: `frontend/store/authStore.ts`
- Create: `frontend/lib/apiClient.ts`

**Interfaces:**
- Produces: `apiFetch(url: string, options?: RequestInit): Promise<Response>` — consumed by all tasks 2–5
- Produces: `useAuthStore.getState().setTokens(accessToken: string, refreshToken: string): void`

- [ ] **Step 1: Add `setTokens` to `authStore`**

Open `frontend/store/authStore.ts`. Add `setTokens` to the interface and store:

```ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { User, LoginResponse } from '@/types/api/auth.api';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  setAuth: (response: LoginResponse) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      setAuth: (response: LoginResponse) =>
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          isAuthenticated: true,
          user: {
            id: response.userId,
            email: response.email,
            role: response.role,
            accountStatus: 'ACTIVE',
            isEmailVerified: true,
            isActive: true,
          },
        }),
      setTokens: (accessToken: string, refreshToken: string) =>
        set({ accessToken, refreshToken }),
      logout: () =>
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

- [ ] **Step 2: Create `lib/apiClient.ts`**

Create `frontend/lib/apiClient.ts`:

```ts
import { useAuthStore } from "@/store/authStore";
import { authService } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

let refreshPromise: Promise<void> | null = null;

export async function apiFetch(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  const response = await doFetch(url, options);

  if (response.status !== 401) return response;

  const { refreshToken } = useAuthStore.getState();
  if (!refreshToken) {
    forceLogout();
    throw new Error("session_expired");
  }

  if (!refreshPromise) {
    refreshPromise = authService
      .refresh(refreshToken)
      .then(({ data }) => {
        useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      })
      .catch(() => {
        forceLogout();
        throw new Error("session_expired");
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  await refreshPromise;
  return doFetch(url, options);
}

function doFetch(url: string, options: RequestInit): Promise<Response> {
  const { accessToken } = useAuthStore.getState();
  const headers = new Headers(options.headers);
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (
    !headers.has("Content-Type") &&
    !(options.body instanceof FormData)
  ) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(`${API_URL}${url}`, { ...options, headers });
}

function forceLogout(): void {
  useAuthStore.getState().logout();
  window.location.href = "/login?reason=session_expired";
}
```

- [ ] **Step 3: Verify lint is clean for these two files**

Run from the `frontend/` directory:
```
npm run lint
```
Expected: no errors for `authStore.ts` or `apiClient.ts`. (Call-site errors from later tasks do not exist yet — this is a baseline check.)

---

## Task 2: Migrate Service Files

**Files:**
- Modify: `frontend/services/auction.service.ts`
- Modify: `frontend/services/user.service.ts`
- Modify: `frontend/services/media.service.ts`
- Modify: `frontend/services/adminService.ts`

**Interfaces:**
- Consumes: `apiFetch` from `@/lib/apiClient`
- Produces updated signatures (token params removed):
  - `auctionService.createAuction(data)`, `getMyAuctions(params)`, `updateAuction(id, data)`, `deleteAuction(id)`
  - `userService.getMyProfile()`, `updateMyProfile(data)`
  - `mediaService.uploadFile(file, entityType?, entityId?)`, `getPresignedUrl(fileName, contentType)`, `getDownloadUrl(s3Key)`
  - `adminService.getUsers(page?, size?, sortBy?, direction?)` and all other admin methods minus the `accessToken` first arg

> **Note:** After this task, TypeScript will report errors in call-site files that still pass `accessToken`. This is expected and resolved in Tasks 3–5. Do not run lint until Task 5 is complete.

- [ ] **Step 1: Migrate `auction.service.ts`**

Replace the file contents of `frontend/services/auction.service.ts`:

```ts
import { mapAuctionDetailResponse, mapAuctionBrowseItem, mapCategoryCount } from '@/types/mappers/auction.mapper'
import { MOCK_BIDS } from '@/lib/mock-data'
import { apiFetch } from '@/lib/apiClient'
import type { BidHistoryItem, AuctionDetail } from '@/types/ui/auction.ui'
import type { AuctionBrowseItem, CategoryCount } from '@/types/ui/auction-browse.ui'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  CreateAuctionRequest,
  UpdateAuctionRequest,
  AuctionResponse,
  AuctionSummaryResponse,
  AuctionCategoryResponse,
  AuctionDetailResponse,
  AuctionBrowseItemResponse,
  CategoryCountResponse,
  BrowseAuctionParams,
} from '@/types/api/auction.api'

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export interface GetAuctionsParams {
  q?: string
  category?: string
  featured?: boolean
}

export const auctionService = {
  async getCategories(): Promise<ApiResponse<AuctionCategoryResponse[]>> {
    const response = await apiFetch('/api/v1/categories')
    if (!response.ok) {
      const errorText = await response.text()
      try {
        throw JSON.parse(errorText)
      } catch {
        throw new Error(errorText || `Failed with status ${response.status}`)
      }
    }
    return response.json()
  },

  async createAuction(data: CreateAuctionRequest): Promise<ApiResponse<AuctionResponse>> {
    const response = await apiFetch('/api/v1/auctions', {
      method: 'POST',
      body: JSON.stringify(data),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async getMyAuctions(
    params: { type?: string; categoryId?: string; page?: number; size?: number }
  ): Promise<ApiResponse<PageResponse<AuctionSummaryResponse>>> {
    const query = new URLSearchParams()
    if (params.type) query.append('type', params.type)
    if (params.categoryId) query.append('categoryId', params.categoryId)
    if (params.page !== undefined) query.append('page', params.page.toString())
    if (params.size !== undefined) query.append('size', params.size.toString())
    const url = `/api/v1/auctions/me${query.toString() ? '?' + query.toString() : ''}`
    const response = await apiFetch(url, { method: 'GET' })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async updateAuction(
    id: string,
    data: UpdateAuctionRequest
  ): Promise<ApiResponse<AuctionResponse>> {
    const response = await apiFetch(`/api/v1/auctions/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
    if (!response.ok) {
      const error = await response.json()
      throw error
    }
    return response.json()
  },

  async deleteAuction(id: string): Promise<void> {
    const response = await apiFetch(`/api/v1/auctions/${id}`, { method: 'DELETE' })
    if (!response.ok && response.status !== 204) {
      const error = await response.json()
      throw error
    }
  },

  async getBrowseAuctions(params: BrowseAuctionParams): Promise<{
    items: AuctionBrowseItem[]
    total: number
    totalPages: number
    page: number
  }> {
    const query = new URLSearchParams()
    if (params.keyword)                query.set('keyword', params.keyword)
    if (params.categorySlug)           query.set('categorySlug', params.categorySlug)
    if (params.minPrice !== undefined) query.set('minPrice', String(params.minPrice))
    if (params.maxPrice !== undefined) query.set('maxPrice', String(params.maxPrice))
    if (params.endingSoon)             query.set('endingSoon', 'true')
    if (params.buyNowAvailable)        query.set('buyNowAvailable', 'true')
    if (params.sortBy)                 query.set('sortBy', params.sortBy)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    const response = await apiFetch(`/api/v1/auctions/public?${query}`, { cache: 'no-store' })
    if (!response.ok) return { items: [], total: 0, totalPages: 0, page: 0 }
    const body: ApiResponse<PageResponse<AuctionBrowseItemResponse>> = await response.json()
    return {
      items:      body.data.data.map(mapAuctionBrowseItem),
      total:      body.data.pagination.total,
      totalPages: body.data.pagination.totalPages,
      page:       body.data.pagination.page,
    }
  },

  async getCategoryCounts(): Promise<CategoryCount[]> {
    const response = await apiFetch('/api/v1/auctions/public/category-counts', { cache: 'no-store' })
    if (!response.ok) return []
    const body: ApiResponse<CategoryCountResponse[]> = await response.json()
    return body.data.map(mapCategoryCount)
  },

  async getAuctionById(id: string): Promise<AuctionDetail | null> {
    const response = await apiFetch(`/api/v1/auctions/public/${id}`, { cache: 'no-store' })
    if (!response.ok) return null
    const body: ApiResponse<AuctionDetailResponse> = await response.json()
    return mapAuctionDetailResponse(body.data)
  },

  async getBidHistory(auctionId: string): Promise<BidHistoryItem[]> {
    await delay(200)
    return MOCK_BIDS.filter((b) => b.auctionId === auctionId).length > 0
      ? MOCK_BIDS.filter((b) => b.auctionId === auctionId)
      : MOCK_BIDS.map(b => ({ ...b, auctionId }))
  },
}
```

- [ ] **Step 2: Migrate `user.service.ts`**

Replace the contents of `frontend/services/user.service.ts`:

```ts
import { ApiResponse } from "@/types/api/auth.api";
import { UpdateUserProfileRequest, UserProfileResponse } from "@/types/api/user-profile.api";
import { apiFetch } from "@/lib/apiClient";

export const userService = {
  async getMyProfile(): Promise<ApiResponse<UserProfileResponse>> {
    const response = await apiFetch('/api/v1/users/me');
    if (!response.ok) {
      const error = await response.json();
      throw error;
    }
    return response.json();
  },

  async updateMyProfile(
    data: UpdateUserProfileRequest
  ): Promise<ApiResponse<UserProfileResponse>> {
    const response = await apiFetch('/api/v1/users/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
    if (!response.ok) {
      const error = await response.json();
      throw error;
    }
    return response.json();
  },
};
```

- [ ] **Step 3: Migrate `media.service.ts`**

Replace the contents of `frontend/services/media.service.ts`:

```ts
import { apiFetch } from "@/lib/apiClient";
import type { MediaUploadResponse, MediaEntityType, PresignedUrlResponse } from '@/types/api/media.api';

export const mediaService = {
  uploadFile: async (
    file: File,
    entityType?: MediaEntityType,
    entityId?: string,
  ): Promise<MediaUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    if (entityType) formData.append("entityType", entityType);
    if (entityId) formData.append("entityId", entityId);

    const response = await apiFetch('/api/v1/media/upload', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  getPresignedUrl: async (
    fileName: string,
    contentType: string,
  ): Promise<PresignedUrlResponse> => {
    const params = new URLSearchParams({ fileName, contentType });
    const response = await apiFetch(`/api/v1/media/presigned-url?${params}`, {
      method: 'GET',
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },

  uploadToS3: async (uploadUrl: string, file: File): Promise<void> => {
    const response = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Failed to upload to S3: ${response.statusText}`);
    }
  },

  getDownloadUrl: async (s3Key: string): Promise<string> => {
    const params = new URLSearchParams({ s3Key });
    const response = await apiFetch(`/api/v1/media/download?${params}`, {
      method: 'GET',
    });

    if (!response.ok) {
      const error = await response.json();
      throw error;
    }

    const json = await response.json();
    return json.data;
  },
};
```

- [ ] **Step 4: Migrate `adminService.ts`**

Replace the contents of `frontend/services/adminService.ts`:

```ts
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
};
```

---

## Task 3: Migrate Hooks

**Files:**
- Modify: `frontend/hooks/useProfile.ts`
- Modify: `frontend/hooks/useSecureImage.ts`

**Interfaces:**
- Consumes: `userService.getMyProfile()`, `userService.updateMyProfile(data)` (no token)
- Consumes: `mediaService.getDownloadUrl(s3Key)` (no token)

- [ ] **Step 1: Migrate `useProfile.ts`**

Replace the contents of `frontend/hooks/useProfile.ts`:

```ts
"use client";

import { useState, useEffect, useCallback } from "react";
import { useAuthStore } from "@/store/authStore";
import { userService } from "@/services/user.service";
import { UpdateUserProfileRequest, UserProfileResponse } from "@/types/api/user-profile.api";

interface UseProfileResult {
  profile: UserProfileResponse | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
  updateProfile: (data: UpdateUserProfileRequest) => Promise<UserProfileResponse>;
}

export function useProfile(): UseProfileResult {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async () => {
    if (!isAuthenticated) return;

    setIsLoading(true);
    setError(null);

    try {
      const res = await userService.getMyProfile();
      setProfile(res.data);
    } catch (err: unknown) {
      const message =
        err && typeof err === "object" && "message" in err
          ? String((err as { message: unknown }).message)
          : "Failed to load profile";
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  const updateProfile = useCallback(
    async (data: UpdateUserProfileRequest): Promise<UserProfileResponse> => {
      if (!isAuthenticated) {
        throw new Error("Not authenticated");
      }

      setIsLoading(true);
      setError(null);

      try {
        const res = await userService.updateMyProfile(data);
        setProfile(res.data);
        return res.data;
      } catch (err: unknown) {
        const message =
          err && typeof err === "object" && "message" in err
            ? String((err as { message: unknown }).message)
            : "Failed to update profile";
        setError(message);
        throw new Error(message);
      } finally {
        setIsLoading(false);
      }
    },
    [isAuthenticated]
  );

  return { profile, isLoading, error, refetch: fetchProfile, updateProfile };
}
```

- [ ] **Step 2: Migrate `useSecureImage.ts`**

Replace the contents of `frontend/hooks/useSecureImage.ts`:

```ts
import { useState, useEffect } from 'react'
import { mediaService } from '@/services/media.service'

export function useSecureImage(initialUrl?: string | null) {
  const [resolvedUrl, setResolvedUrl] = useState<string | undefined>(undefined)

  useEffect(() => {
    let isMounted = true

    const resolveImage = async () => {
      if (!initialUrl) {
        if (isMounted) setResolvedUrl(undefined)
        return
      }

      if (initialUrl.includes('/api/v1/media/download?s3Key=')) {
        try {
          const urlObj = new URL(initialUrl)
          const s3Key = urlObj.searchParams.get('s3Key')
          if (!s3Key) return
          const presignedUrl = await mediaService.getDownloadUrl(s3Key)
          if (isMounted) setResolvedUrl(presignedUrl)
        } catch (error) {
          console.error("Failed to load secure image", error)
        }
      } else if (initialUrl.startsWith('uploads/')) {
        try {
          const presignedUrl = await mediaService.getDownloadUrl(initialUrl)
          if (isMounted) setResolvedUrl(presignedUrl)
        } catch (error) {
          console.error("Failed to load secure image", error)
        }
      } else if (isMounted) {
        setResolvedUrl(initialUrl)
      }
    }

    resolveImage()

    return () => { isMounted = false }
  }, [initialUrl])

  return resolvedUrl
}
```

---

## Task 4: Migrate Seller Pages

**Files:**
- Modify: `frontend/app/(dashboard)/seller/auctions/page.tsx`
- Modify: `frontend/app/(dashboard)/seller/auctions/new/page.tsx`
- Modify: `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx`

**Interfaces:**
- Consumes: `auctionService.getMyAuctions(params)`, `createAuction(data)`, `updateAuction(id, data)`, `deleteAuction(id)` (no token)
- Consumes: `mediaService.getPresignedUrl(fileName, contentType)`, `uploadToS3(url, file)` (no token)

- [ ] **Step 1: Migrate `seller/auctions/page.tsx`**

Three changes to `frontend/app/(dashboard)/seller/auctions/page.tsx`:

1. Remove the `useAuthStore` import line entirely (nothing else from it is used):
```ts
// DELETE this line:
import { useAuthStore } from '@/store/authStore'
```

2. In `SellerAuctionsPage`, remove the `accessToken` destructure and its guard:
```ts
// DELETE these:
const { accessToken } = useAuthStore()
// ...
if (!accessToken) return
```

3. In `fetchAuctions`, remove `accessToken` from the service call and from `useCallback` deps:
```ts
// BEFORE:
const res = await auctionService.getMyAuctions({}, accessToken)
// ...
}, [accessToken])

// AFTER:
const res = await auctionService.getMyAuctions({})
// ...
}, [])
```

- [ ] **Step 2: Migrate `seller/auctions/new/page.tsx`**

Four changes to `frontend/app/(dashboard)/seller/auctions/new/page.tsx`:

1. Remove the `useAuthStore` import:
```ts
// DELETE:
import { useAuthStore } from '@/store/authStore'
```

2. In `CreateAuctionPage`, remove the `accessToken` destructure:
```ts
// DELETE:
const { accessToken } = useAuthStore()
```

3. In the categories `useEffect`, remove the guard and `accessToken` dep:
```ts
// BEFORE:
useEffect(() => {
  if (!accessToken) return;
  auctionService.getCategories()
    .then(res => setCategories(res.data))
    .catch(err => console.error("Failed to load categories:", err))
}, [accessToken])

// AFTER:
useEffect(() => {
  auctionService.getCategories()
    .then(res => setCategories(res.data))
    .catch(err => console.error("Failed to load categories:", err))
}, [])
```

4. In `handleSubmit`, remove the `!accessToken` guard and token args:
```ts
// BEFORE:
async function handleSubmit(asDraft: boolean) {
  if (!accessToken) {
    toast.error('You must be logged in to create an auction.')
    return;
  }
  // ...
  const presigned = await mediaService.getPresignedUrl(accessToken, file.name, file.type)
  // ...
  await auctionService.createAuction({ ... }, accessToken)

// AFTER:
async function handleSubmit(asDraft: boolean) {
  // (remove the !accessToken guard entirely)
  // ...
  const presigned = await mediaService.getPresignedUrl(file.name, file.type)
  // ...
  await auctionService.createAuction({ ... })
```

- [ ] **Step 3: Migrate `seller/auctions/[id]/manage/page.tsx`**

Six changes to `frontend/app/(dashboard)/seller/auctions/[id]/manage/page.tsx`:

1. In `DraftEditForm` (around line 107), remove `const { accessToken } = useAuthStore()`.

2. In `DraftEditForm`, in the categories `useEffect`, remove the `accessToken` guard and dep:
```ts
// BEFORE:
useEffect(() => {
  if (!accessToken) return
  auctionService.getCategories()
    .then(res => setCategories(res.data))
    .catch(err => console.error('Failed to load categories:', err))
}, [accessToken])

// AFTER:
useEffect(() => {
  auctionService.getCategories()
    .then(res => setCategories(res.data))
    .catch(err => console.error('Failed to load categories:', err))
}, [])
```

3. In `DraftEditForm`, in `submitForm`, remove the `!accessToken` guard and token args:
```ts
// BEFORE:
const submitForm = async (publish: boolean) => {
  if (!accessToken) {
    alert('You must be logged in to update an auction.')
    return
  }
  // ...
  const presigned = await mediaService.getPresignedUrl(accessToken, file.name, file.type)
  // ...
  await auctionService.updateAuction(auction.id, { ... }, accessToken)

// AFTER:
const submitForm = async (publish: boolean) => {
  // (remove !accessToken guard)
  // ...
  const presigned = await mediaService.getPresignedUrl(file.name, file.type)
  // ...
  await auctionService.updateAuction(auction.id, { ... })
```

4. In `ManageAuctionPage` (around line 280), remove `const { accessToken } = useAuthStore()`.

5. In `ManageAuctionPage.handleDelete`, remove the `accessToken` guard and token arg:
```ts
// BEFORE:
const handleDelete = async () => {
  if (!accessToken || !auction) return
  // ...
  await auctionService.deleteAuction(auction.id, accessToken)

// AFTER:
const handleDelete = async () => {
  if (!auction) return
  // ...
  await auctionService.deleteAuction(auction.id)
```

6. Remove the `useAuthStore` import if `accessToken` was the only thing used from it (verify no other store reads remain in the file before deleting the import).

---

## Task 5: Migrate Profile & Admin Pages — Final Lint Check

**Files:**
- Modify: `frontend/app/(dashboard)/profile/page.tsx`
- Modify: `frontend/app/admin/users/page.tsx`
- Modify: `frontend/app/admin/templates/page.tsx`
- Modify: `frontend/app/admin/email-logs/page.tsx`
- Modify: `frontend/app/admin/audit-logs/page.tsx`

**Interfaces:**
- Consumes: `mediaService.uploadFile(file, entityType?, entityId?)` (no token)
- Consumes: all `adminService` methods without `accessToken` first arg

- [ ] **Step 1: Migrate `profile/page.tsx`**

Two changes to `frontend/app/(dashboard)/profile/page.tsx`:

1. Remove the `accessToken` line (keep `authUser`):
```ts
// BEFORE:
const authUser = useAuthStore((s) => s.user);
const accessToken = useAuthStore((s) => s.accessToken);

// AFTER:
const authUser = useAuthStore((s) => s.user);
```

2. In `handleAvatarChange`, remove the `!accessToken` guard and the token arg:
```ts
// BEFORE:
const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
  if (!accessToken) return;
  // ...
  const uploadRes = await mediaService.uploadFile(
    accessToken,
    file,
    "USER_AVATAR",
    userId,
  );

// AFTER:
const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
  // (remove !accessToken guard)
  // ...
  const uploadRes = await mediaService.uploadFile(
    file,
    "USER_AVATAR",
    userId,
  );
```

- [ ] **Step 2: Migrate `admin/users/page.tsx`**

Pattern: remove `const { accessToken } = useAuthStore()`, remove `if (!accessToken ...)` guards, remove `accessToken` as first argument in every `adminService` call, remove `accessToken` from all `useCallback` / `useEffect` dep arrays.

Specific changes (line references may shift after earlier edits — find by content):

```ts
// DELETE:
const { accessToken } = useAuthStore()

// In loadUsers useCallback:
// BEFORE: if (!accessToken) return
// AFTER: (remove the guard)
// BEFORE: const result = await adminService.getUsers(accessToken, page, PAGE_SIZE, sortBy, sortDirection)
// AFTER:  const result = await adminService.getUsers(page, PAGE_SIZE, sortBy, sortDirection)
// BEFORE: }, [accessToken, page, sortBy, sortDirection])
// AFTER:  }, [page, sortBy, sortDirection])

// In handleStatusChange useCallback:
// BEFORE: if (!accessToken || !selectedUser) return
// AFTER:  if (!selectedUser) return
// BEFORE: await adminService.updateUserStatus(accessToken, selectedUser.id, newStatus, ...)
// AFTER:  await adminService.updateUserStatus(selectedUser.id, newStatus, ...)

// In handleViewProfile useCallback:
// BEFORE: if (!accessToken) return
// AFTER:  (remove guard)
// BEFORE: const data = await adminService.getUserProfile(accessToken, user.id)
// AFTER:  const data = await adminService.getUserProfile(user.id)
```

If `useAuthStore` is no longer referenced in this file after removing `accessToken`, also remove its import.

- [ ] **Step 3: Migrate `admin/templates/page.tsx`**

Same pattern as users page:

```ts
// DELETE:
const { accessToken } = useAuthStore()

// In loadTemplates useCallback — remove accessToken guard and first arg:
// BEFORE: if (!accessToken) return
// BEFORE: const result = await adminService.getTemplates(accessToken, filters, page, PAGE_SIZE)
// AFTER:  const result = await adminService.getTemplates(filters, page, PAGE_SIZE)
// BEFORE: }, [accessToken, filters, page])
// AFTER:  }, [filters, page])

// In handleViewTemplate:
// BEFORE: if (!accessToken) return
// BEFORE: const details = await adminService.getTemplate(accessToken, template.id)
// AFTER:  const details = await adminService.getTemplate(template.id)

// In handleSaveTemplate:
// BEFORE: if (!accessToken) return
// BEFORE: await adminService.updateTemplate(accessToken, editingTemplate.id, request)
// BEFORE: await adminService.createTemplate(accessToken, request)
// AFTER:  await adminService.updateTemplate(editingTemplate.id, request)
// AFTER:  await adminService.createTemplate(request)

// In handleTestSend:
// BEFORE: if (!accessToken || !selectedTemplate) return
// AFTER:  if (!selectedTemplate) return
// BEFORE: await adminService.testTemplate(accessToken, selectedTemplate.id, { ... })
// BEFORE: await adminService.sendTemplateToGroup(accessToken, selectedTemplate.id, { ... })
// AFTER:  await adminService.testTemplate(selectedTemplate.id, { ... })
// AFTER:  await adminService.sendTemplateToGroup(selectedTemplate.id, { ... })
```

Remove the `useAuthStore` import if no other store state is read in this file.

- [ ] **Step 4: Migrate `admin/email-logs/page.tsx`**

```ts
// DELETE:
const { accessToken } = useAuthStore()

// In the main data-loading useCallback:
// BEFORE: if (!accessToken) return
// BEFORE: const result = await adminService.getEmailLogs(accessToken, filters, page, PAGE_SIZE)
// AFTER:  const result = await adminService.getEmailLogs(filters, page, PAGE_SIZE)
// BEFORE: }, [accessToken, filters, page])
// AFTER:  }, [filters, page])
```

Remove the `useAuthStore` import if no longer used.

- [ ] **Step 5: Migrate `admin/audit-logs/page.tsx`**

```ts
// DELETE:
const { accessToken } = useAuthStore();

// In the audit logs loading useCallback/useEffect:
// BEFORE: if (!accessToken) return;
// BEFORE: (the object with accessToken key passed to adminService.getAuditLogs)
// AFTER:  await adminService.getAuditLogs(filters, page, size, sortBy, sortDir)
// BEFORE: }, [accessToken])
// AFTER:  }, [])  // or keep other actual deps
```

Remove the `useAuthStore` import if no longer used.

- [ ] **Step 6: Final lint check**

Run from `frontend/`:
```
npm run lint
```

Expected: zero TypeScript errors and zero lint warnings across all modified files. All `accessToken` references in service call arguments are gone. If any errors remain, check the file listed and remove the stale `accessToken` arg or import.

- [ ] **Step 7: Manual smoke test**

Start the dev server:
```
npm run dev
```

Verify these flows work without opening the network tab to add tokens manually:
1. Log in — the dashboard loads (confirms `setAuth` still works).
2. Navigate to seller auctions — list loads (confirms `getMyAuctions` works via `apiFetch`).
3. Create a new auction and upload an image — auction creates successfully (confirms `getPresignedUrl` + `createAuction` work).
4. Open browser dev tools → Application → Local Storage → `auth-storage`. Find the `accessToken`. Manually clear just the `accessToken` value (leave `refreshToken`). Navigate to any authenticated page. Observe: the page loads normally after a brief refresh-token exchange (confirms the 401 → refresh → retry flow).
5. Clear both `accessToken` and `refreshToken` from localStorage. Navigate to an authenticated page. Observe: redirect to `/login?reason=session_expired` (confirms the force-logout flow).
