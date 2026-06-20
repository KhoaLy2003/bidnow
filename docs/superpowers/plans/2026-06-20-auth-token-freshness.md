# Auth Token Freshness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store the JWT expiry timestamp on login, proactively refresh before it expires in API calls and on route guard mount, and show a dismissible banner on the login page when the user was force-logged out.

**Architecture:** `authStore` gains `accessTokenExpiresAt`; `apiFetch` checks it before every authenticated request and refreshes proactively if near-expiry; `AuthGuard` and `AdminGuard` do the same check on mount before rendering protected content; the login page reads `?reason=session_expired` from the URL and renders a dismissible info banner.

**Tech Stack:** Next.js 16.2 App Router, Zustand 5 with `persist` middleware, TypeScript strict mode, Tailwind CSS v4, Lucide React icons, shadcn/ui components.

## Global Constraints

- TypeScript strict mode — no `any` in new code. Existing `catch (err: any)` in login page is pre-existing, do not change it.
- No test suite — verification is `npm run lint` (from `frontend/`) and `npx tsc --noEmit` (from `frontend/`) plus manual browser steps.
- No new dependencies — all needed APIs (Zustand, React hooks, Next.js router) already exist.
- Grace window is exactly `60_000` ms (60 seconds) throughout — do not use a different value.
- `authService` stays on raw `fetch` — never import `apiFetch` into `auth.service.ts` (circular import).
- No background refresh timer — out of scope.
- Spec: `docs/superpowers/specs/2026-06-20-auth-token-freshness-design.md`

---

## File Map

| File | Change |
|---|---|
| `frontend/store/authStore.ts` | Add `accessTokenExpiresAt` field; update `setAuth`, `setTokens`, `logout` |
| `frontend/lib/apiClient.ts` | Add proactive pre-request check; update `setTokens` call to pass `expiresIn` |
| `frontend/components/shared/AuthGuard.tsx` | Add `isValidating` state; mount-time token check + silent refresh |
| `frontend/components/shared/AdminGuard.tsx` | Same as AuthGuard, plus existing role check |
| `frontend/app/(auth)/login/page.tsx` | Split into `LoginForm` + `LoginPage` wrapper; add session-expired banner |

---

## Task 1: authStore — add `accessTokenExpiresAt`

**Files:**
- Modify: `frontend/store/authStore.ts`

**Interfaces:**
- Produces:
  - `accessTokenExpiresAt: number | null` on `AuthState` (persisted)
  - `setAuth(response: LoginResponse): void` — now also sets `accessTokenExpiresAt`
  - `setTokens(accessToken: string, refreshToken: string, expiresIn: number): void` — signature change, third param required
  - `logout(): void` — now also clears `accessTokenExpiresAt`

- [ ] **Step 1: Replace `frontend/store/authStore.ts` with the updated version**

Write the complete file:

```ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { User, LoginResponse } from '@/types/api/auth.api';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  accessTokenExpiresAt: number | null;
  setAuth: (response: LoginResponse) => void;
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      accessTokenExpiresAt: null,
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
      setTokens: (accessToken: string, refreshToken: string, expiresIn: number) =>
        set({ accessToken, refreshToken, accessTokenExpiresAt: Date.now() + expiresIn * 1000 }),
      logout: () =>
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          accessTokenExpiresAt: null,
        }),
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

- [ ] **Step 2: Verify lint and types pass**

```bash
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
```

Expected: zero errors. If lint reports `setTokens` call-site errors in `apiClient.ts`, that is expected — Task 2 fixes them.

- [ ] **Step 3: Verify in browser**

Start the dev server (`npm run dev` from `frontend/`). Log in. Open DevTools → Application → Local Storage → `auth-storage`. Confirm the persisted state contains `accessTokenExpiresAt` as a Unix timestamp (number ~13 digits), not `null`.

- [ ] **Step 4: Commit**

```bash
git add frontend/store/authStore.ts
git commit -m "feat(auth): store accessTokenExpiresAt and update setTokens signature"
```

---

## Task 2: apiClient — proactive pre-request refresh

**Files:**
- Modify: `frontend/lib/apiClient.ts`

**Interfaces:**
- Consumes from Task 1:
  - `accessTokenExpiresAt: number | null` from `useAuthStore.getState()`
  - `setTokens(accessToken: string, refreshToken: string, expiresIn: number): void`

- [ ] **Step 1: Replace `frontend/lib/apiClient.ts` with the updated version**

Two changes from the current file:
1. Add proactive check at the top of `apiFetch` (inside a block scope to avoid variable name collision with the existing 401 path).
2. Update the existing `setTokens` call in the 401 path to pass `data.expiresIn`.

Write the complete file:

```ts
import { useAuthStore } from "@/store/authStore";
import { authService } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

let refreshPromise: Promise<void> | null = null;

export async function apiFetch(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  // Proactive refresh: if token is expired or within 60 s of expiry, refresh before sending.
  // Block-scoped to avoid naming conflicts with the reactive 401 path below.
  {
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
  }

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
        useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.expiresIn);
      })
      .catch(() => {
        forceLogout();
        throw new Error("session_expired");
      })
      .finally(() => { refreshPromise = null; });
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
  if (typeof window !== "undefined") {
    window.location.href = "/login?reason=session_expired";
  }
}
```

- [ ] **Step 2: Verify lint and types pass**

```bash
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
```

Expected: zero errors. The `setTokens` call-site error from Task 1's lint run should now be gone.

- [ ] **Step 3: Verify proactive refresh in browser**

1. Log in. Open DevTools → Application → Local Storage → `auth-storage`.
2. Edit `accessTokenExpiresAt` to `Date.now() + 30000` (30 seconds from now) to simulate a near-expiry token.
3. Make any API call from the UI (e.g., navigate to the seller dashboard which calls `getMyAuctions`).
4. Open DevTools → Network. Confirm a `POST /api/v1/auth/refresh` request fires **before** the API request. Confirm `accessTokenExpiresAt` in Local Storage is updated to a new future timestamp after the refresh.

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/apiClient.ts
git commit -m "feat(auth): proactively refresh access token before expiry in apiFetch"
```

---

## Task 3: AuthGuard and AdminGuard — mount-time token validation

**Files:**
- Modify: `frontend/components/shared/AuthGuard.tsx`
- Modify: `frontend/components/shared/AdminGuard.tsx`

**Interfaces:**
- Consumes from Task 1:
  - `accessTokenExpiresAt: number | null` from `useAuthStore()`
  - `refreshToken: string | null` from `useAuthStore()`
  - `setTokens(accessToken: string, refreshToken: string, expiresIn: number): void`
  - `logout(): void`
- Consumes: `authService.refresh(refreshToken: string): Promise<ApiResponse<LoginResponse>>` from `@/services/auth.service`

- [ ] **Step 1: Replace `frontend/components/shared/AuthGuard.tsx`**

```tsx
'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'
import { Loader2 } from 'lucide-react'

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const { isAuthenticated, accessTokenExpiresAt, refreshToken, setTokens, logout } = useAuthStore()
  const [isMounted, setIsMounted] = useState(false)
  const [isValidating, setIsValidating] = useState(false)

  useEffect(() => {
    setIsMounted(true)
  }, [])

  useEffect(() => {
    if (!isMounted) return

    if (!isAuthenticated) {
      router.push('/login')
      return
    }

    const nearExpiry =
      accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - 60_000

    if (nearExpiry) {
      if (!refreshToken) {
        logout()
        router.push('/login?reason=session_expired')
        return
      }
      setIsValidating(true)
      authService
        .refresh(refreshToken)
        .then(({ data }) => setTokens(data.accessToken, data.refreshToken, data.expiresIn))
        .catch(() => {
          logout()
          router.push('/login?reason=session_expired')
        })
        .finally(() => setIsValidating(false))
    }
  }, [isMounted, isAuthenticated, accessTokenExpiresAt, refreshToken, router, setTokens, logout])

  if (!isMounted || !isAuthenticated || isValidating) {
    return (
      <div className="flex h-[80vh] w-full items-center justify-center">
        <Loader2 className="size-8 animate-spin text-[var(--color-text-brand)]" />
      </div>
    )
  }

  return <>{children}</>
}
```

- [ ] **Step 2: Replace `frontend/components/shared/AdminGuard.tsx`**

Identical to AuthGuard, with two additions: role check redirects to `/` for non-admins, and the `user` field is read from the store and included in both the effect deps and the render guard condition.

```tsx
'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'
import { Loader2 } from 'lucide-react'

export function AdminGuard({ children }: { readonly children: React.ReactNode }) {
  const router = useRouter()
  const { isAuthenticated, user, accessTokenExpiresAt, refreshToken, setTokens, logout } = useAuthStore()
  const [isMounted, setIsMounted] = useState(false)
  const [isValidating, setIsValidating] = useState(false)

  useEffect(() => {
    setIsMounted(true)
  }, [])

  useEffect(() => {
    if (!isMounted) return

    if (!isAuthenticated) {
      router.push('/login')
      return
    }

    if (user?.role !== 'ADMIN') {
      router.push('/')
      return
    }

    const nearExpiry =
      accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - 60_000

    if (nearExpiry) {
      if (!refreshToken) {
        logout()
        router.push('/login?reason=session_expired')
        return
      }
      setIsValidating(true)
      authService
        .refresh(refreshToken)
        .then(({ data }) => setTokens(data.accessToken, data.refreshToken, data.expiresIn))
        .catch(() => {
          logout()
          router.push('/login?reason=session_expired')
        })
        .finally(() => setIsValidating(false))
    }
  }, [isMounted, isAuthenticated, user, accessTokenExpiresAt, refreshToken, router, setTokens, logout])

  if (!isMounted || !isAuthenticated || user?.role !== 'ADMIN' || isValidating) {
    return (
      <div className="flex h-[80vh] w-full items-center justify-center">
        <Loader2 className="size-8 animate-spin text-[var(--color-text-brand)]" />
      </div>
    )
  }

  return <>{children}</>
}
```

- [ ] **Step 3: Verify lint and types pass**

```bash
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 4: Verify guard behavior in browser — expired token**

1. Log in as a regular user. Navigate to `/seller/auctions` (AuthGuard-protected).
2. In DevTools → Application → Local Storage → `auth-storage`, set `accessTokenExpiresAt` to `1` (epoch 1 ms, definitively expired) and set `refreshToken` to an invalid string like `"bad-token"`.
3. Hard-refresh the page (`Ctrl+Shift+R`).
4. Expected: spinner appears briefly, then the page redirects to `/login?reason=session_expired`.

- [ ] **Step 5: Verify guard behavior in browser — valid token**

1. Log in fresh. Navigate to `/seller/auctions`.
2. Expected: page loads normally without redirect. The spinner shows only briefly during the hydration check.

- [ ] **Step 6: Verify AdminGuard — expired token**

1. Log in as an admin user. Navigate to `/admin/users`.
2. Set `accessTokenExpiresAt` to `1` and `refreshToken` to `"bad-token"` in LocalStorage.
3. Hard-refresh. Expected: redirect to `/login?reason=session_expired`.

- [ ] **Step 7: Commit**

```bash
git add frontend/components/shared/AuthGuard.tsx frontend/components/shared/AdminGuard.tsx
git commit -m "fix(auth): validate token freshness on guard mount and silently refresh if near-expiry"
```

---

## Task 4: Login page — session-expired banner

**Files:**
- Modify: `frontend/app/(auth)/login/page.tsx`

**Interfaces:**
- Consumes: `?reason=session_expired` query param from URL (set by `forceLogout()` in `apiClient.ts` and by guard redirects in Task 3)
- Color tokens from `app/globals.css`:
  - `--color-info-subtle` — background
  - `--color-info-border` — border
  - `--color-info-text` — text

- [ ] **Step 1: Replace `frontend/app/(auth)/login/page.tsx` with the updated version**

Changes from the current file:
- `useSearchParams()` added (requires Suspense boundary).
- Component logic extracted into `LoginForm`; `LoginPage` default export becomes a thin Suspense wrapper.
- `showExpiredBanner` state initialized from the `reason` param.
- `useEffect` cleans up the URL param on mount so refreshing the page doesn't re-show the banner.
- Banner rendered above the error block using info design tokens.
- Both `<Input>` elements get `onChange={() => setShowExpiredBanner(false)}` to dismiss the banner on typing.
- Unused `import type { Metadata } from 'next'` removed (was in the original but unused).
- `X` icon imported from `lucide-react`.

```tsx
'use client'

import { Suspense, useState, useEffect, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Loader2, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { authService } from '@/services/auth.service'
import { useAuthStore } from '@/store/authStore'

function LoginForm() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setAuth } = useAuthStore()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showExpiredBanner, setShowExpiredBanner] = useState(
    searchParams.get('reason') === 'session_expired'
  )

  useEffect(() => {
    if (searchParams.get('reason') === 'session_expired') {
      window.history.replaceState(null, '', '/login')
    }
    // Intentionally runs once on mount — we only need to clean the URL param once
    // after capturing it into state. Re-running on searchParams changes would cause
    // a loop if the router reflects the replaceState update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsLoading(true)
    setError(null)

    const formData = new FormData(e.currentTarget)
    const email = formData.get('email') as string
    const password = formData.get('password') as string

    try {
      const result = await authService.login(email, password)
      setAuth(result.data)

      if (result.data.role === 'ADMIN') {
        router.push('/admin/users')
      } else {
        router.push('/')
      }
    } catch (err: any) {
      setError(err.message || 'Invalid email or password')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 flex flex-col gap-6">
        <div className="text-center">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Welcome back</h1>
          <p className="mt-1 text-sm text-muted-foreground">Sign in to your account</p>
        </div>

        {showExpiredBanner && (
          <div className="flex items-start gap-3 rounded-lg border border-[var(--color-info-border)] bg-[var(--color-info-subtle)] px-3 py-2.5 text-sm text-[var(--color-info-text)]">
            <span className="flex-1">Your session has expired. Please sign in again.</span>
            <button onClick={() => setShowExpiredBanner(false)} aria-label="Dismiss">
              <X className="size-4 shrink-0" />
            </button>
          </div>
        )}

        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-lg border border-destructive/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              placeholder="you@example.com"
              required
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link href="/forgot-password" className="text-xs text-[var(--color-text-link)] hover:underline">
                Forgot password?
              </Link>
            </div>
            <Input
              id="password"
              name="password"
              type="password"
              placeholder="••••••••"
              required
              onChange={() => setShowExpiredBanner(false)}
            />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading}>
            {isLoading && <Loader2 className="size-4 animate-spin mr-2" />}
            Sign in
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{' '}
          <Link href="/register" className="text-[var(--color-text-link)] hover:underline font-medium">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  )
}
```

- [ ] **Step 2: Verify lint and types pass**

```bash
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
```

Expected: zero errors. (The `eslint-disable-next-line` comment suppresses the intentional exhaustive-deps deviation.)

- [ ] **Step 3: Verify banner in browser — session_expired reason**

1. Navigate directly to `http://localhost:3000/login?reason=session_expired`.
2. Expected: blue info banner appears reading "Your session has expired. Please sign in again."
3. Check the URL bar — it should immediately change to `/login` (no `?reason=...`).
4. Click the X button — banner dismisses.

- [ ] **Step 4: Verify banner dismissed on typing**

1. Navigate to `/login?reason=session_expired`. Banner appears.
2. Start typing in the email field. Banner should disappear.

- [ ] **Step 5: Verify banner absent on normal visit**

1. Navigate to `/login` (no query param). No banner should appear.

- [ ] **Step 6: Verify full end-to-end flow**

1. Log in as any user.
2. In Local Storage, set `accessTokenExpiresAt` to `1` and `refreshToken` to `"bad-token"`.
3. Navigate to a guard-protected route (e.g., `/seller/auctions` for a regular user).
4. Expected flow: spinner → redirect to `/login?reason=session_expired` → banner appears → URL cleans to `/login`.

- [ ] **Step 7: Commit**

```bash
git add "frontend/app/(auth)/login/page.tsx"
git commit -m "feat(auth): show session-expired banner on login page after force-logout redirect"
```
