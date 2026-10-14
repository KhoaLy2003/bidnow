const WS_PATH = '/ws-notifications/websocket' // raw-WebSocket transport of the SockJS endpoint
const DEFAULT_API_URL = 'http://localhost:8080'

export function resolveWsEndpoint(env: { wsUrl?: string; apiUrl?: string }): string {
  if (env.wsUrl) return env.wsUrl
  const api = (env.apiUrl || DEFAULT_API_URL).replace(/\/+$/, '')
  return api.replace(/^http/, 'ws') + WS_PATH
}

/** The gateway reads the JWT from `access_token` on WebSocket handshakes (browsers cannot set headers). */
export function withAccessToken(endpoint: string, token: string | null): string {
  if (!token) return endpoint
  const url = new URL(endpoint)
  url.searchParams.set('access_token', token)
  return url.toString()
}
