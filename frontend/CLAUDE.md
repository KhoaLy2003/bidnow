# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Commands

```bash
npm run dev      # Start dev server (Next.js on default port 3000)
npm run build    # Production build
npm run lint     # ESLint check
```

No test suite is configured. There is no test runner command.

## Environment

Requires `.env.local` with:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_SOCKET_URL=<websocket server url>
```

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
- `auctionStore` — live bid state for the active auction detail page; reset on unmount
- `notificationStore` — notification list and unread count
- `walletStore` — balance and transaction history

### Real-time (WebSocket)

`hooks/useAuctionSocket.ts` connects to `NEXT_PUBLIC_SOCKET_URL` using socket.io-client. It joins an auction room on mount (`auction:join`) and listens for three events:
- `bid:new` → calls `setBid` + `addBidToHistory` on `auctionStore`
- `auction:status` → calls `setStatus`
- `auction:end` → sets final bid + marks status `Closed`

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

Amounts are stored and transmitted in **cents** (integers). Always use `formatCurrency(cents)` from `lib/format.ts` for display. Render prices, balances, and timers in `font-mono` (Geist Mono) to prevent layout shift on digit change.
