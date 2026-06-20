# Shared Fetch Wrapper & Token Refresh Design

**Date:** 2026-06-20
**Issue:** [#93 — No shared fetch wrapper, 401 surfaces as raw error, refresh token never used](https://github.com/KhoaLy2003/bidnow/issues/93)
**Milestone:** v0.1-foundation

---

## Problem

Every service (`auction`, `user`, `media`, `admin`) attaches the `Authorization` header manually and handles errors independently with no 401-specific branch. When the access token expires, the next API call returns 401 and the raw error object surfaces directly in the UI — the user is not redirected to login, the refresh token is never used, and no retry is attempted. There is no single place to apply a fix.

---

## Goals

- One place that attaches `Authorization` headers
- One place that handles 401: attempt a single token refresh, retry, or force-logout
- Concurrent 401s during a refresh do not trigger multiple simultaneous refresh requests
- All services and their callers are free of auth token boilerplate

---

## Architecture

### 1. `authStore` — add `setTokens` action

Add a single new action to `store/authStore.ts`:

```ts
setTokens: (accessToken: string, refreshToken: string) =>
  set({ accessToken, refreshToken }),
```

Added to the `AuthState` interface and the store implementation. No other store changes — `logout()` already clears all auth state.

### 2. `lib/apiClient.ts` — new file

A single exported function `apiFetch(url: string, options?: RequestInit)` wraps `fetch` with three responsibilities:

1. **Token injection** — reads `accessToken` from `useAuthStore.getState()` and attaches `Authorization: Bearer` automatically. Sets `Content-Type: application/json` unless the body is `FormData`.
2. **401 handling** — on a 401 response, reads `refreshToken` from the store and calls `authService.refresh()`. On success, calls `setTokens()` with the new pair and retries the original request once. On failure (refresh also 401s or throws), calls `logout()` and redirects to `/login?reason=session_expired`.
3. **Concurrent refresh deduplication** — a module-level `let refreshPromise: Promise<void> | null = null` ensures all in-flight requests that hit 401 simultaneously await the same single refresh, not N parallel refreshes.

A private `doFetch(url, options)` helper builds the headers and executes the underlying `fetch`. The public `apiFetch` calls `doFetch` for the initial request and again for the retry so the retry re-reads the updated token from the store without duplicating header logic.

`auth.service.ts` stays on raw `fetch` — `apiFetch` imports `authService` for the refresh call, so `authService` must not import `apiFetch` (would be circular).

`window.location.href` is used for the forced logout redirect since `apiFetch` lives outside React and cannot use the Next.js router.

Base URL (`NEXT_PUBLIC_API_URL`) is owned by `apiFetch`. Services pass URL paths (e.g., `/api/v1/auctions`), not full URLs.

```ts
// Sketch — not final implementation code
const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
let refreshPromise: Promise<void> | null = null;

export async function apiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const response = await doFetch(url, options);
  if (response.status !== 401) return response;

  const { refreshToken } = useAuthStore.getState();
  if (!refreshToken) { forceLogout(); throw new Error("session_expired"); }

  if (!refreshPromise) {
    refreshPromise = authService.refresh(refreshToken)
      .then(({ data }) => useAuthStore.getState().setTokens(data.accessToken, data.refreshToken))
      .catch(() => { forceLogout(); throw new Error("session_expired"); })
      .finally(() => { refreshPromise = null; });
  }

  await refreshPromise;
  return doFetch(url, options);
}

function doFetch(url: string, options: RequestInit): Promise<Response> {
  const { accessToken } = useAuthStore.getState();
  const headers = new Headers(options.headers);
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (!headers.has("Content-Type") && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  return fetch(`${API_URL}${url}`, { ...options, headers });
}

function forceLogout(): void {
  useAuthStore.getState().logout();
  window.location.href = "/login?reason=session_expired";
}
```

### 3. Service layer migration

Four service files are migrated to `apiFetch`. Changes per file:

- Remove `const API_URL = ...` — base URL is now owned by `apiFetch`
- Remove `token: string` / `accessToken: string` parameter from every method
- Replace `fetch(${API_URL}...)` calls with `apiFetch(/api/v1/...)`
- Remove all manual `Authorization` header construction

**Special case — `mediaService.uploadToS3`:** This method uploads directly to an S3 presigned URL (a third-party host). It must stay on raw `fetch` and must not send an `Authorization` header.

**`adminService.ts`:** The existing `authHeaders()` and `parseResponse()` helpers are removed — `apiFetch` takes over token injection, and `parseResponse` is rewritten to accept the `Response` from `apiFetch` rather than building headers.

Resulting signatures after migration:

```ts
// auction.service.ts
createAuction(data: CreateAuctionRequest): Promise<ApiResponse<AuctionResponse>>
getMyAuctions(params): Promise<ApiResponse<PageResponse<AuctionSummaryResponse>>>
updateAuction(id, data): Promise<ApiResponse<AuctionResponse>>
deleteAuction(id): Promise<void>

// user.service.ts
getMyProfile(): Promise<ApiResponse<UserProfileResponse>>
updateMyProfile(data): Promise<ApiResponse<UserProfileResponse>>

// media.service.ts
uploadFile(file, entityType?, entityId?): Promise<MediaUploadResponse>
getPresignedUrl(fileName, contentType): Promise<PresignedUrlResponse>
getDownloadUrl(s3Key): Promise<string>
uploadToS3(uploadUrl, file): Promise<void>   // stays on raw fetch

// adminService.ts — all methods drop accessToken as first param
getUsers(page?, size?, sortBy?, direction?): Promise<PageResponse<AdminUserResponse>>
// ... (same pattern for all 10 admin methods)
```

### 4. Call site migration (components & hooks)

13 files currently pass `accessToken` into service calls. Each one:

- Removes the `const { accessToken } = useAuthStore()` line (or removes `accessToken` from the destructure if other store state is still used)
- Drops the `accessToken` argument from every service call

No behavioral change in these files — purely boilerplate removal.

**Affected files:**
- `app/(dashboard)/seller/auctions/page.tsx`
- `app/(dashboard)/seller/auctions/[id]/manage/page.tsx`
- `app/(dashboard)/seller/auctions/new/page.tsx`
- `app/(dashboard)/profile/page.tsx`
- `app/admin/users/page.tsx`
- `app/admin/templates/page.tsx`
- `app/admin/email-logs/page.tsx`
- `app/admin/audit-logs/page.tsx`
- `app/page.tsx`
- `app/auctions/page.tsx`
- `app/auctions/[id]/page.tsx`
- `hooks/useProfile.ts`
- `hooks/useSecureImage.ts`

---

## Constraints

- `auth.service.ts` must stay on raw `fetch` — circular import prevention
- `mediaService.uploadToS3` must stay on raw `fetch` — third-party S3 URL, no auth header
- Public auction endpoints in `auction.service.ts` (e.g., `getBrowseAuctions`, `getAuctionById`) keep their `cache: 'no-store'` option — `apiFetch` passes `options` through so this works unchanged
- `window.location.href` used for redirect (not Next.js router) since `apiFetch` is outside React

---

## Acceptance Criteria (from issue #93)

- [ ] `lib/apiClient.ts` exists with `apiFetch(url, options?)` signature
- [ ] Access token attached automatically — no manual `Authorization` header in services
- [ ] A 401 triggers one refresh attempt before propagating the error
- [ ] If refresh also returns 401, user is force-logged out and redirected to `/login?reason=session_expired`
- [ ] All existing service calls (`auction`, `user`, `media`, `admin`) migrated to `apiFetch`
- [ ] Concurrent 401s during a refresh do not trigger multiple simultaneous refresh requests
