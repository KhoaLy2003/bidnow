# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Commands

```bash
npm run dev      # Start dev server (Next.js on default port 3000)
npm run build    # Production build
npm run lint     # ESLint check
npm test         # Vitest unit tests (pure modules: lib/, services/, store/, types/mappers/)
```

## Environment

Requires `.env.local` with:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws-notifications/websocket   # optional; derived from NEXT_PUBLIC_API_URL when unset
```

See `.env.example` for all configuration options.

## Architecture

### Route Groups & Layouts

| Route group | Guard | Layout shell |
|---|---|---|
| `app/(auth)/` | none | Centered card, logo only |
| `app/(dashboard)/` | `AuthGuard` | Header + Footer + BottomNav |
| `app/admin/` | `AdminGuard` | Header + sidebar nav + Footer |
| `app/auctions/` | none | Public |

`AuthGuard` and `AdminGuard` (in `components/shared/`) read from Zustand `authStore` and redirect to `/login` if unauthenticated/unauthorized.

### State Management

Four Zustand stores in `store/`:
- `authStore` — JWT tokens + user, persisted to `localStorage` via `zustand/middleware/persist` (key: `auth-storage`)
- `auctionStore` — live state (`LiveAuctionState`) of the auction detail page being viewed, updated only through the monotonic reducers in `lib/realtime/auction-live.ts`; reset on unmount
- `notificationStore` — notification list and unread count
- `walletStore` — balance and transaction history

### Real-time (WebSocket)

`hooks/useAuctionSocket.ts` connects with `@stomp/stompjs` over native WebSocket to `/ws-notifications/websocket` through the gateway (JWT as `access_token`, refreshed on every connect), subscribes to `/topic/auctions/{id}` (`BID_PLACED`, `AUCTION_EXTENDED`, `AUCTION_ENDED`, `AUCTION_CANCELLED`) and, when logged in, `/user/queue/notifications` (`OUTBID`). Messages are parsed/routed by `lib/realtime/dispatch.ts`; `AuctionLiveBridge` hydrates the store and resyncs (re-fetches the auction + first history page) on every (re)connect, including the first, to recover events missed before the subscription; components read through `useLiveAuction`. Clients are receive-only.

### API & Types

All API responses use the envelope in `types/api.ts`:
```ts
ApiResponse<T>  // single item: { timestamp, status, message, data: T }
PageResponse<T> // paginated: { data: T[], pagination: PaginationMeta }
```

Domain types live in `types/` (`auction.ts`, `user.ts`, `wallet.ts`, `notification.ts`, `auth.ts`, `admin.ts`).

### Design Token Usage

Two CSS layers in `app/globals.css`:
1. **shadcn semantic vars** — `--primary`, `--background`, `--foreground`, etc. Use these for Tailwind utility classes.
2. **BidNow extended vars** — `--color-auction-*`, `--color-wallet-*`, `--color-text-*`, `--color-bg-*`, etc. Reference as arbitrary values: `bg-[var(--color-auction-active-bg)]`.

`lib/design-tokens.ts` exports `AuctionStatus` enum, `getStatusTokens(status)` helper, plus typed `colors`, `durations`, and `easings` constants.

### Auction Status Logic

`lib/auction-utils.ts` computes `AuctionStatus` from a live `endsAt` date:
- `> 5 min` → `Active`
- `1–5 min` → `EndingSoon`
- `< 1 min` → `Critical`
- Terminal states from the server (`Closed`, `Won`, `Lost`, `Outbid`) are passed through unchanged.

`useCountdown(endsAt)` (in `hooks/`) ticks every second and returns `{ secondsLeft, timerState, isExpired }`.

### Money / Formatting

Amounts are **dollars** (`number`) end-to-end, exactly as the API sends them. Do arithmetic/comparison through `lib/money.ts` (integer cents internally) and display with `formatCurrency(dollars)` in `font-mono`.
