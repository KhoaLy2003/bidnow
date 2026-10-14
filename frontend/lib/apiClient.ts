import { useAuthStore } from "@/store/authStore";
import { authService } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const TOKEN_REFRESH_BUFFER_MS = 60_000;

let refreshPromise: Promise<void> | null = null;

/** Refreshes the access token if it is expired or within the refresh buffer. Throws `session_expired` after logging out. */
async function refreshIfNearExpiry(): Promise<void> {
  const { accessToken, accessTokenExpiresAt, refreshToken } = useAuthStore.getState();
  if (accessToken && accessTokenExpiresAt !== null && Date.now() >= accessTokenExpiresAt - TOKEN_REFRESH_BUFFER_MS) {
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

/** A usable access token for non-fetch transports (the STOMP handshake), or null when anonymous or logged out. */
export async function getFreshAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  try {
    await refreshIfNearExpiry();
  } catch {
    return null;
  }
  return useAuthStore.getState().accessToken;
}

export async function apiFetch(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  // Token refresh is browser-only — the auth store lives in localStorage.
  if (typeof window === "undefined") {
    return doFetch(url, options);
  }

  // Proactive refresh: if token is expired or within 60 s of expiry, refresh before sending.
  await refreshIfNearExpiry();

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
