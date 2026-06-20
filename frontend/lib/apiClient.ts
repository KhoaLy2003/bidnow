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
  if (typeof window !== "undefined") {
    window.location.href = "/login?reason=session_expired";
  }
}
