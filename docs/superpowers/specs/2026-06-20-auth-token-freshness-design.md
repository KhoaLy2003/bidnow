# Auth Token Freshness Design

**Date:** 2026-06-20
**Issues:**
- [#94 — Store token expiry timestamp and proactively refresh before access token expires](https://github.com/KhoaLy2003/bidnow/issues/94)
- [#95 — AuthGuard trusts stale isAuthenticated flag and does not validate token freshness on mount](https://github.com/KhoaLy2003/bidnow/issues/95)
- [#96 — Show session expired notice when user is force-logged out due to token expiry](https://github.com/KhoaLy2003/bidnow/issues/96)
**Milestone:** v0.1-foundation

---

## Problem

Three gaps exist in the current auth flow:

1. `LoginResponse.expiresIn` is discarded on login — the frontend has no concept of when its access token will expire. The only expiry signal is a reactive 401, which itself was only just handled in issue #93.
2. `AuthGuard` and `AdminGuard` trust the persisted `isAuthenticated` boolean, which stays `true` across sessions regardless of token expiry. A returning user passes the guard, sees a protected page, and gets a raw 401 on the first API call.
3. When `apiFetch` or a guard force-logs the user out due to an expired token, the redirect to `/login` gives no explanation. Users may think they accidentally logged out or that there is a bug.

---

## Goals

- Frontend knows when the access token expires and acts proactively.
- Protected routes validate token freshness before rendering, not after the first API call fails.
- Force-logout redirects to a login page that explains what happened.

---

## Out of Scope

**Background refresh timer** — a `setTimeout` that silently refreshes at `expiresIn - 60` seconds after login. Excluded per YAGNI. The proactive pre-request check covers the primary use case without the complexity of managing timers across tab focus/blur, SSR, and unmount.

---

## Architecture

Four files change across the three issues, in dependency order:

```
store/authStore.ts          ← #94: adds accessTokenExpiresAt
lib/apiClient.ts            ← #94: proactive pre-request check; updates setTokens call
components/shared/AuthGuard.tsx   ← #95: mount-time token validation
components/shared/AdminGuard.tsx  ← #95: same fix
app/(auth)/login/page.tsx   ← #96: session-expired banner
```

---

## Design

### 1. `store/authStore.ts` (Issue #94)

Add `accessTokenExpiresAt: number | null` to `AuthState`. Persisted to `localStorage` inside the existing `persist` wrapper — no config change needed.

**Interface addition:**
```ts
accessTokenExpiresAt: number | null;
setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
```

**`setAuth` — compute expiry on login:**
```ts
setAuth: (response: LoginResponse) =>
  set({
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    accessTokenExpiresAt: Date.now() + response.expiresIn * 1000,
    user: {
      id: response.userId,
      email: response.email,
      role: response.role,
      accountStatus: 'ACTIVE',
      isEmailVerified: true,
      isActive: true,
    },
  }),
```

**`setTokens` — updated to accept and store `expiresIn`** (one call site: `apiClient.ts`):
```ts
setTokens: (accessToken, refreshToken, expiresIn) =>
  set({ accessToken, refreshToken, accessTokenExpiresAt: Date.now() + expiresIn * 1000 }),
```

**`logout` — clear expiry:**
```ts
logout: () =>
  set({
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    accessTokenExpiresAt: null,
  }),
```

`expiresIn` is already typed as `number` in `LoginResponse` (`types/api/auth.api.ts`) — no type changes needed there.

---

### 2. `lib/apiClient.ts` (Issue #94)

Two changes to the existing file.

**Update the reactive 401 refresh path** to pass `expiresIn` to `setTokens`:
```ts
.then(({ data }) => {
  useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.expiresIn);
})
```

**Add proactive pre-request check** at the top of `apiFetch`, before the `doFetch` call:
```ts
export async function apiFetch(url: string, options: RequestInit = {}): Promise<Response> {
  // Proactive refresh: fire before sending if token is expired or within 60 s of expiry
  const { accessToken, accessTokenExpiresAt, refreshToken } = useAuthStore.getState();
  if (accessToken && accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - 60_000) {
    if (!refreshToken) {
      forceLogout();
      throw new Error("session_expired");
    }
    if (!refreshPromise) {
      refreshPromise = authService
        .refresh(refreshToken)
        .then(({ data }) => {
          useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.expiresIn);
        })
        .catch(() => {
          forceLogout();
          throw new Error("session_expired");
        })
        .finally(() => { refreshPromise = null; });
    }
    await refreshPromise;
  }

  // Original flow
  const response = await doFetch(url, options);
  if (response.status !== 401) return response;
  // ... existing 401 handling unchanged ...
}
```

**Key properties:**
- Unauthenticated requests (no `accessToken`) skip the proactive check entirely.
- `accessTokenExpiresAt === null` (sessions persisted before this change) skip the check and fall back to reactive 401 handling.
- Reuses the existing `refreshPromise` deduplication — concurrent requests that all need a proactive refresh trigger only one server call.
- Proactive refresh failure → immediate `forceLogout()`, request is not sent.

---

### 3. `AuthGuard.tsx` and `AdminGuard.tsx` (Issue #95)

Both guards get the same treatment. `AuthGuard` is described in full; `AdminGuard` is identical except it also checks `user?.role !== 'ADMIN'` and redirects non-admins to `/`.

**New state:**
```ts
const [isValidating, setIsValidating] = useState(false);
```

**New store fields read:**
```ts
const { isAuthenticated, accessTokenExpiresAt, refreshToken, setTokens, logout } = useAuthStore();
```

**Mount effect:**
```ts
useEffect(() => {
  if (!isMounted) return;

  if (!isAuthenticated) {
    router.push('/login');
    return;
  }

  const nearExpiry =
    accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - 60_000;

  if (nearExpiry) {
    if (!refreshToken) {
      logout();
      router.push('/login?reason=session_expired');
      return;
    }
    setIsValidating(true);
    authService
      .refresh(refreshToken)
      .then(({ data }) => setTokens(data.accessToken, data.refreshToken, data.expiresIn))
      .catch(() => {
        logout();
        router.push('/login?reason=session_expired');
      })
      .finally(() => setIsValidating(false));
  }
}, [isMounted, isAuthenticated, accessTokenExpiresAt, refreshToken, router, setTokens, logout]);
```

**Spinner condition:**
```ts
if (!isMounted || !isAuthenticated || isValidating) {
  return <div className="flex h-[80vh] w-full items-center justify-center">
    <Loader2 className="size-8 animate-spin text-[var(--color-text-brand)]" />
  </div>;
}
```

**Key properties:**
- `accessTokenExpiresAt === null` → `nearExpiry` is false → guard passes through, reactive 401 handling catches stale tokens later.
- No flash of protected content — `isValidating` holds the spinner while refresh is in flight.
- No infinite redirect — `/login` is not wrapped in `AuthGuard`.
- Guards call `authService.refresh()` directly. No concurrent-refresh race is possible: the guard spinner blocks page render, so no API calls fire during validation.

---

### 4. `app/(auth)/login/page.tsx` (Issue #96)

**`useSearchParams()` requires a Suspense boundary** in Next.js App Router. The current `LoginPage` default export becomes a thin wrapper; all existing logic moves into an inner `LoginForm` component:

```tsx
export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
```

Inside `LoginForm`:

**Read reason param and initialize banner state:**
```ts
const searchParams = useSearchParams();
const [showExpiredBanner, setShowExpiredBanner] = useState(
  searchParams.get('reason') === 'session_expired'
);
```

**Clean up URL on mount** (prevent re-show on browser refresh):
```ts
useEffect(() => {
  if (showExpiredBanner) {
    window.history.replaceState(null, '', '/login');
  }
}, []);
```

**Dismiss banner on typing** — add `onChange` to both `<Input>` elements:
```tsx
onChange={() => setShowExpiredBanner(false)}
```

**Banner markup** — rendered above the existing error block:
```tsx
{showExpiredBanner && (
  <div className="flex items-start gap-3 rounded-lg border border-[var(--color-info-border)] bg-[var(--color-info-subtle)] px-3 py-2.5 text-sm text-[var(--color-info-text)]">
    <span className="flex-1">Your session has expired. Please sign in again.</span>
    <button onClick={() => setShowExpiredBanner(false)} aria-label="Dismiss">
      <X className="size-4 shrink-0" />
    </button>
  </div>
)}
```

**Key properties:**
- Banner absent on normal `/login` visit — `reason` param missing, state initializes to `false`.
- URL cleaned on mount via `replaceState` — no navigation triggered, browser history not polluted.
- Uses `--color-info-*` tokens from `globals.css` — no hardcoded colors.
- `X` icon from `lucide-react` (existing project dependency).

---

## Acceptance Criteria

### Issue #94
- [ ] `authStore` persists `accessTokenExpiresAt: number | null`
- [ ] `setAuth` populates `accessTokenExpiresAt` on login
- [ ] `setTokens` updates `accessTokenExpiresAt` on every refresh
- [ ] `logout` clears `accessTokenExpiresAt` to `null`
- [ ] `apiFetch` proactively refreshes when token is within 60 s of expiry
- [ ] Proactive refresh does not fire for unauthenticated requests (no `accessToken`)
- [ ] Proactive refresh does not fire when `accessTokenExpiresAt` is `null`
- [ ] Concurrent proactive refreshes are deduplicated via `refreshPromise`

### Issue #95
- [ ] `AuthGuard` attempts silent refresh when `accessTokenExpiresAt` is past or within 60 s
- [ ] Refresh success → protected content renders without redirect
- [ ] Refresh failure → redirect to `/login?reason=session_expired`
- [ ] Spinner shown during refresh (no flash of protected content)
- [ ] `AdminGuard` receives the same treatment
- [ ] Sessions without `accessTokenExpiresAt` (null) pass the guard unchanged

### Issue #96
- [ ] Login page renders dismissible info banner when `?reason=session_expired` is present
- [ ] Banner absent on normal `/login` visit
- [ ] Banner uses `--color-info-*` design tokens
- [ ] URL is cleaned via `replaceState` on mount
- [ ] Banner dismisses on close button click
- [ ] Banner dismisses when user starts typing in email or password
