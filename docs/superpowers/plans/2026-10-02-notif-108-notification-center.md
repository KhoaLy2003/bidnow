# NOTIF-108: Frontend Notification Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- The header bell shows the live unread count, and its dropdown shows the 10 most recent notifications.
- `/notifications` lists every notification, with tabs, a type filter, search, pagination and bulk actions.
- New notifications arrive live with a toast for the important types.
- The badge resyncs when the tab regains focus.

**Architecture:**
- **Data.** `notificationStore` (Zustand) becomes server-backed:
  - It loads the recent list and the unread count over REST (`services/notification.service.ts`).
  - It applies `NOTIFICATION` pushes from `/user/queue/notifications`.
  - It performs optimistic mark-read, mark-all-read and delete, rolling back on error.
- **One live connection.** A single STOMP connection for the user queue is owned by `hooks/useUserNotifications`, mounted once at the root through `UserNotificationsBridge`. The auction page's socket stops subscribing to the user queue, so outbids are toasted once.
- **Pure logic.** DTO mapping, filter/URL handling and time formatting live in pure, unit-tested modules.
- **Backend fix.** One small backend change makes `createdAt` carry its UTC offset, so the browser computes correct "time ago" values whatever the server's time zone.

**Tech Stack:** Next.js 16.2 (App Router), React 19, TypeScript strict, Tailwind v4, Base UI / shadcn primitives in `components/ui/`, Zustand 5, `@stomp/stompjs` over native WebSocket, sonner, lucide-react, vitest (node environment, `**/*.test.ts`). Backend: Spring Boot 3.2.4 / Java 17 (one DTO change).

**Spec:** `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`, specifically **Story 8** and **Decisions 10 and 11** (no sounds, no desktop notifications). Source: `docs/epics/notification/issue-12.md` (#18) for the UI checklist. Design rules: `docs/design-system.md`, `frontend/AGENTS.md` and `frontend/CLAUDE.md`.

## Global Constraints

- **Next.js 16 is not the Next.js you know.** Before writing page or layout code, read the relevant guide in `frontend/node_modules/next/dist/docs/` (`frontend/AGENTS.md`). `useSearchParams` needs a `<Suspense>` boundary; follow `app/(auth)/login/page.tsx`.
- Use Base UI / shadcn primitives from `components/ui/` (Button, Popover, Tabs, Select, Checkbox, Dialog, Input, Skeleton, ScrollArea, Separator). Do not hand-write primitives. Icons come from `lucide-react` only.
- No raw hex colours. Use CSS variables from `app/globals.css`, e.g. `bg-[var(--color-danger-default)]`. Follow `docs/design-system.md` for spacing (4px base), radius, z-index and animation.
- TypeScript strict. No `any`. Robust types for API payloads (`types/api/`) and component props.
- REST calls go through `apiFetch` (`lib/apiClient.ts`). Services follow the existing pattern: on `!response.ok`, throw the parsed JSON body.
- WebSocket transport: STOMP over **native WebSocket** to `resolveWsEndpoint(...)` with `withAccessToken(endpoint, await getFreshAccessToken())` on every (re)connect, exactly as `hooks/useAuctionSocket.ts` does. No SockJS.
- The backend contract is `/api/v1/notifications`, used as follows:
  - `GET ?page&size&read&types&search` → `PageResponse<NotificationResponse>`
  - `GET /unread-count` → `{count}`
  - `PUT /{id}/read`, `PUT /{id}/unread`
  - `PUT /mark-all-read` → `{updated}`
  - `DELETE /{id}`, `DELETE /delete-read`, `DELETE /delete-all`
  - The push envelope is `{type:"NOTIFICATION", notification, unreadCount}`, where `unreadCount` is the server value (never compute +1).
- Toast only these server types: `BID_OUTBID`, `AUCTION_WON`, `PAYMENT_REQUIRED`, `PAYMENT_REMINDER` and `AUCTION_ENDING_SOON`.
- Out of scope (Decision 11): sound, desktop or browser notifications, and a per-user notification settings UI.
- Vitest runs pure modules only (`lib/`, `services/`, `store/`, `types/mappers/`). Components and hooks are verified by `npx tsc --noEmit`, `npm run lint`, `npm run build` and the manual check.
- **Agents do not commit.** Skip every commit step. The user commits.
- Commands: frontend from `frontend/` (`npm test`, `npx tsc --noEmit`, `npm run lint`, `npm run build`); backend from `backend/` (`mvn -q -pl media-service -am test`).
- Conventional commit for the story (the user runs it): `feat(notification): frontend notification center (NOTIF-108)`.

## Rulings (decisions this plan makes beyond the roadmap)

1. **No SockJS and no ref-counted STOMP singleton.**
   - The project already speaks STOMP over native WebSocket (`/ws-notifications/websocket`), so `sockjs-client` is not added.
   - The user queue has exactly one consumer: a root-mounted hook that owns one `Client`. A ref-counted client would be YAGNI.
   - The auction page keeps its own connection for `/topic/auctions/{id}`, but no longer subscribes to the user queue. An auction page therefore holds two sockets, which is acceptable. If needed later, it can be merged in a follow-up.
2. **Mounted at the root, not in the dashboard layout.** The header (and its bell) also appears on public pages such as `/auctions/[id]`, where outbids matter most. `UserNotificationsBridge` in `app/layout.tsx` keeps one connection across client-side navigation. It does nothing for anonymous users and for admins (the header hides the bell for `ADMIN`).
3. **`createdAt` carries an offset (backend).** `NotificationResponse.createdAt` was a zone-less `LocalDateTime` in the server's default zone (UTC in Docker, the host zone in local runs). A browser parses such a value as *its own* local time, so "time ago" would be off by the zone difference. The DTO now sends `OffsetDateTime` (`2026-10-01T10:00:00+07:00` / `…Z`), converted from the stored value using the server's default zone.
4. **Tab sync is a refresh on focus,** not localStorage events (roadmap). On window `focus` and on every STOMP (re)connect, the hook reloads the unread count and the recent list.
5. **Bulk actions have no new backend endpoint.**
   - Bulk "mark read" and "delete" on selected rows issue one request per row (`Promise.allSettled`), then reload.
   - "Mark all as read" and "Delete all read" use the existing bulk endpoints.
6. **The store keeps the field name `notifications`** (the recent 10) so existing consumers keep compiling between tasks. Actions return `Promise<boolean>` (`true` = saved) instead of throwing, so components can toast a failure.
7. **UI category vs server type.** The UI keeps a small set of categories for icon and colour (`NotificationType`), and each item keeps its raw `serverType` for filtering and toasts. An unknown server type maps to `system`.

## File Structure

| File | Responsibility |
|---|---|
| `backend/media-service/src/main/java/com/bidnow/media/dto/response/NotificationResponse.java` (modify) | `createdAt` → `OffsetDateTime` |
| `frontend/types/api/notification.api.ts` (modify) | Server type list, list params, count/bulk DTOs |
| `frontend/types/ui/notification.ui.ts` (modify) | UI `Notification` + `NotificationType` categories |
| `frontend/types/mappers/notification.mapper.ts` (create) | DTO → UI, category mapping, `shouldToast` |
| `frontend/components/notification/notification-style.ts` (create) | Category → icon + accent colour |
| `frontend/lib/formatTimeAgo.ts` (create) | "just now / 5 min ago / 2 hours ago / yesterday / Sep 28" |
| `frontend/services/notification.service.ts` (create) | REST calls |
| `frontend/store/notificationStore.ts` (rewrite) | Server-backed recent list + count, push, optimistic actions |
| `frontend/lib/realtime/dispatch.ts` (modify) | `parseUserNotificationMessage` |
| `frontend/hooks/useUserNotifications.ts` (create), `components/notification/UserNotificationsBridge.tsx` (create), `app/layout.tsx` (modify) | Live user-queue connection, focus resync, toasts |
| `frontend/hooks/useAuctionSocket.ts` (modify) | Stop subscribing to the user queue (no double toast) |
| `frontend/components/notification/NotificationToast.tsx` (modify) | Toast styles for the new categories |
| `frontend/components/notification/NotificationItem.tsx` (create), `NotificationPanel.tsx`, `NotificationBell.tsx` (modify), `hooks/useNotifications.ts` (delete) | Bell + dropdown |
| `frontend/lib/notification-filters.ts` (create), `app/(dashboard)/notifications/page.tsx` (create) | Full page with URL-backed filters |
| `frontend/CLAUDE.md`, `docs/architecture.md`, roadmap (modify) | Docs |

Paths below use `F = frontend` and `MT = backend/media-service/src/test/java/com/bidnow/media`.

---

### Task 1: Backend: `createdAt` with offset

**Files:**
- Modify: `backend/media-service/src/main/java/com/bidnow/media/dto/response/NotificationResponse.java`
- Test: `MT/dto/response/NotificationResponseTest.java` (create), `MT/realtime/UserNotificationPushJsonTest.java` (modify), plus any other test that builds `NotificationResponse.createdAt(LocalDateTime)`

**Interfaces:**
- Produces: `NotificationResponse.createdAt` of type `java.time.OffsetDateTime`. JSON is ISO-8601 with an offset, e.g. `"2026-10-01T10:00:00Z"`.

- [ ] **Step 1: Write the failing tests**

Create `MT/dto/response/NotificationResponseTest.java`:

```java
package com.bidnow.media.dto.response;

import com.bidnow.media.domain.entity.Notification;
import com.bidnow.media.domain.enums.NotificationChannel;
import com.bidnow.media.domain.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    void from_givesCreatedAtTheServerZoneOffset() {
        LocalDateTime stored = LocalDateTime.of(2026, 10, 1, 10, 0);
        Notification n = Notification.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .type(NotificationType.AUCTION_WON).channel(NotificationChannel.IN_APP)
                .title("t").message("m").dedupKey("k").build();
        n.setCreatedAt(stored);

        OffsetDateTime createdAt = NotificationResponse.from(n).getCreatedAt();

        assertThat(createdAt.toLocalDateTime()).isEqualTo(stored);
        assertThat(createdAt.getOffset()).isEqualTo(ZoneId.systemDefault().getRules().getOffset(stored));
    }
}
```

In `MT/realtime/UserNotificationPushJsonTest.java`, change the fixture's `.createdAt(LocalDateTime.of(2026, 10, 1, 10, 0))` to `.createdAt(OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC))`. Replace the `LocalDateTime` import with `java.time.OffsetDateTime` and `java.time.ZoneOffset`. In `stompMessage_hasContractShape`, change the expected `createdAt` text to `"2026-10-01T10:00:00Z"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`): `mvn -q -pl media-service -am test -Dtest='NotificationResponseTest,UserNotificationPushJsonTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, because `createdAt` is still a `LocalDateTime`.

- [ ] **Step 3: Implement**

In `NotificationResponse.java`:
- Change the field to `private OffsetDateTime createdAt;`.
- Replace the `java.time.LocalDateTime` import with `java.time.OffsetDateTime` and `java.time.ZoneId`.
- In `from(...)`, change the line to `.createdAt(notification.getCreatedAt() == null ? null : notification.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime())`.
- Extend the class Javadoc on `from`: `createdAt is stored as server-local LocalDateTime (BaseEntity); it is sent with the server zone's offset so browsers compute the right instant.`

Then run `mvn -q -pl media-service -am test-compile`. Fix every other test that builds `NotificationResponse.builder().createdAt(<LocalDateTime>)` by switching to an `OffsetDateTime` (UTC). Find them with `grep -rn "createdAt(" backend/media-service/src/test`. Tests asserting on JSON text must expect the offset form. List each changed file in the report.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 2: Notification types, mapper, styles and `formatTimeAgo` (frontend)

**Files:**
- Modify: `F/types/api/notification.api.ts`, `F/types/ui/notification.ui.ts`, `F/components/notification/NotificationPanel.tsx` (colour map only), `F/components/notification/NotificationToast.tsx` (duration map only)
- Create: `F/types/mappers/notification.mapper.ts`, `F/components/notification/notification-style.ts`, `F/lib/formatTimeAgo.ts`
- Test: `F/types/mappers/notification.mapper.test.ts`, `F/lib/formatTimeAgo.test.ts` (create)

**Interfaces:**
- Produces:
  - `NOTIFICATION_SERVER_TYPES` (readonly tuple of the 21 backend enum names) and `type NotificationServerType`
  - `interface NotificationListParams { page?: number; size?: number; read?: boolean; types?: string[]; search?: string }`
  - `interface UnreadCountDto { count: number }` and `interface BulkUpdateDto { updated: number }`
  - `NotificationDto.createdAt`, now documented as ISO-8601 with an offset
  - UI `type NotificationType = 'outbid'|'won'|'lost'|'ending_soon'|'bid_placed'|'payment_due'|'payment'|'payment_failed'|'auction'|'refund'|'system'`
  - UI `interface Notification { id; type: NotificationType; serverType: string; title; message; isRead: boolean; createdAt: Date; auctionId?: string; linkUrl?: string }`. `userId` is removed.
  - `toNotificationType(serverType: string): NotificationType`, `toNotification(dto: NotificationDto): Notification`, `shouldToast(n: Notification): boolean`
  - `NOTIFICATION_STYLE: Record<NotificationType, { icon: LucideIcon; accent: string }>`, where `accent` is a CSS colour expression such as `'var(--color-danger-default)'`
  - `formatTimeAgo(date: Date, now?: Date): string`

- [ ] **Step 1: Write the failing tests**

Create `F/types/mappers/notification.mapper.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { NOTIFICATION_SERVER_TYPES, type NotificationDto } from '@/types/api/notification.api'
import { shouldToast, toNotification, toNotificationType } from './notification.mapper'

const dto: NotificationDto = {
  id: 'n-1',
  type: 'BID_OUTBID',
  title: "You've been outbid",
  message: 'Someone outbid you on "Vase". Current price: $120.00.',
  actionUrl: '/auctions/a-1',
  auctionId: 'a-1',
  metadata: { currentPrice: '120' },
  read: false,
  createdAt: '2026-10-01T10:00:00+07:00',
}

describe('toNotificationType', () => {
  it('gives every backend type a category; only account/system types fall back to system', () => {
    const system = new Set(['USER_REGISTERED', 'OTP_VERIFICATION', 'SYSTEM_ANNOUNCEMENT'])
    for (const type of NOTIFICATION_SERVER_TYPES) {
      expect(toNotificationType(type) === 'system', type).toBe(system.has(type))
    }
  })

  it('maps the key types', () => {
    expect(toNotificationType('BID_OUTBID')).toBe('outbid')
    expect(toNotificationType('AUCTION_WON')).toBe('won')
    expect(toNotificationType('AUCTION_LOST')).toBe('lost')
    expect(toNotificationType('AUCTION_ENDING_SOON')).toBe('ending_soon')
    expect(toNotificationType('NEW_BID')).toBe('bid_placed')
    expect(toNotificationType('PAYMENT_REMINDER')).toBe('payment_due')
    expect(toNotificationType('PAYMENT_RECEIVED')).toBe('payment')
    expect(toNotificationType('PAYMENT_FAILED')).toBe('payment_failed')
    expect(toNotificationType('DEPOSIT_REFUNDED')).toBe('refund')
    expect(toNotificationType('AUCTION_EXTENDED')).toBe('auction')
  })

  it('maps unknown types to system', () => {
    expect(toNotificationType('SOMETHING_NEW')).toBe('system')
  })
})

describe('toNotification', () => {
  it('maps the DTO, keeping the server type and parsing the offset timestamp', () => {
    expect(toNotification(dto)).toEqual({
      id: 'n-1',
      type: 'outbid',
      serverType: 'BID_OUTBID',
      title: "You've been outbid",
      message: 'Someone outbid you on "Vase". Current price: $120.00.',
      isRead: false,
      createdAt: new Date('2026-10-01T03:00:00Z'),
      auctionId: 'a-1',
      linkUrl: '/auctions/a-1',
    })
  })

  it('turns null links into undefined', () => {
    const n = toNotification({ ...dto, actionUrl: null, auctionId: null })
    expect(n.linkUrl).toBeUndefined()
    expect(n.auctionId).toBeUndefined()
  })
})

describe('shouldToast', () => {
  it('toasts only the important types', () => {
    for (const type of ['BID_OUTBID', 'AUCTION_WON', 'PAYMENT_REQUIRED', 'PAYMENT_REMINDER', 'AUCTION_ENDING_SOON']) {
      expect(shouldToast(toNotification({ ...dto, type })), type).toBe(true)
    }
    for (const type of ['NEW_BID', 'AUCTION_LOST', 'DEPOSIT_REFUNDED', 'AUCTION_EXTENDED']) {
      expect(shouldToast(toNotification({ ...dto, type })), type).toBe(false)
    }
  })
})
```

Create `F/lib/formatTimeAgo.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { formatTimeAgo } from './formatTimeAgo'

// Local-time constructors keep the calendar-day rules independent of the machine's zone.
const NOW = new Date(2026, 9, 2, 12, 0, 0) // 2 Oct 2026 12:00 local

describe('formatTimeAgo', () => {
  it('says "just now" under a minute (and for small clock skew into the future)', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 59, 30), NOW)).toBe('just now')
    expect(formatTimeAgo(new Date(2026, 9, 2, 12, 0, 20), NOW)).toBe('just now')
  })

  it('counts minutes under an hour', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 55), NOW)).toBe('5 min ago')
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 1), NOW)).toBe('59 min ago')
  })

  it('counts hours under a day', () => {
    expect(formatTimeAgo(new Date(2026, 9, 2, 11, 0), NOW)).toBe('1 hour ago')
    expect(formatTimeAgo(new Date(2026, 9, 2, 10, 0), NOW)).toBe('2 hours ago')
    expect(formatTimeAgo(new Date(2026, 9, 1, 13, 0), NOW)).toBe('23 hours ago')
  })

  it('says "yesterday" for the previous calendar day beyond 24 hours', () => {
    expect(formatTimeAgo(new Date(2026, 9, 1, 9, 0), NOW)).toBe('yesterday')
  })

  it('shows a short date for older items, with the year only when it differs', () => {
    expect(formatTimeAgo(new Date(2026, 8, 28, 9, 0), NOW)).toBe('Sep 28')
    expect(formatTimeAgo(new Date(2025, 11, 31, 9, 0), NOW)).toBe('Dec 31, 2025')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `frontend/`): `npx vitest run types/mappers/notification.mapper.test.ts lib/formatTimeAgo.test.ts`
Expected: FAIL, because `NOTIFICATION_SERVER_TYPES`, `notification.mapper` and `formatTimeAgo` cannot be resolved.

- [ ] **Step 3: Implement**

Replace `F/types/api/notification.api.ts` with:

```ts
/** media-service NotificationType names (backend enum). New backend values still parse; the UI maps them to "system". */
export const NOTIFICATION_SERVER_TYPES = [
  'USER_REGISTERED', 'OTP_VERIFICATION', 'BID_PLACED', 'BID_OUTBID', 'AUCTION_ENDING_SOON', 'AUCTION_WON',
  'AUCTION_LOST', 'AUCTION_CANCELLED', 'PAYMENT_REMINDER', 'PAYMENT_RECEIVED', 'DEPOSIT_REFUNDED',
  'DEPOSIT_FORFEITED', 'WATCHLIST_ITEM_STARTING', 'SYSTEM_ANNOUNCEMENT', 'FIRST_BID', 'NEW_BID',
  'AUCTION_EXTENDED', 'AUCTION_CREATED', 'PAYMENT_REQUIRED', 'PAYMENT_FAILED', 'AUCTION_UNSOLD',
] as const

export type NotificationServerType = (typeof NOTIFICATION_SERVER_TYPES)[number]

/** A stored notification, as listed by /api/v1/notifications and pushed on /user/queue/notifications. */
export interface NotificationDto {
  id: string
  /** media-service NotificationType, e.g. BID_OUTBID, NEW_BID, AUCTION_WON (string: tolerate new values) */
  type: string
  title: string
  message: string
  actionUrl: string | null
  auctionId: string | null
  metadata: Record<string, unknown> | null
  read: boolean
  /** ISO-8601 with offset, e.g. 2026-10-01T10:00:00Z */
  createdAt: string
}

/** STOMP envelope on /user/queue/notifications. `unreadCount` is the server's count after this notification. */
export interface UserNotificationMessage {
  type: 'NOTIFICATION'
  notification: NotificationDto
  unreadCount: number
}

/** Query for GET /api/v1/notifications (page is 0-based). */
export interface NotificationListParams {
  page?: number
  size?: number
  read?: boolean
  types?: string[]
  search?: string
}

export interface UnreadCountDto {
  count: number
}

export interface BulkUpdateDto {
  updated: number
}
```

Replace `F/types/ui/notification.ui.ts` with:

```ts
/** UI category: drives icon and colour. The raw backend type is kept on `serverType`. */
export type NotificationType =
  | 'outbid'
  | 'won'
  | 'lost'
  | 'ending_soon'
  | 'bid_placed'
  | 'payment_due'
  | 'payment'
  | 'payment_failed'
  | 'auction'
  | 'refund'
  | 'system'

export interface Notification {
  id:         string
  type:       NotificationType
  serverType: string
  title:      string
  message:    string
  isRead:     boolean
  createdAt:  Date
  auctionId?: string
  linkUrl?:   string
}
```

Create `F/types/mappers/notification.mapper.ts`:

```ts
import type { NotificationDto, NotificationServerType } from '@/types/api/notification.api'
import type { Notification, NotificationType } from '@/types/ui/notification.ui'

const CATEGORY: Record<NotificationServerType, NotificationType> = {
  USER_REGISTERED:         'system',
  OTP_VERIFICATION:        'system',
  SYSTEM_ANNOUNCEMENT:     'system',
  BID_PLACED:              'bid_placed',
  FIRST_BID:               'bid_placed',
  NEW_BID:                 'bid_placed',
  BID_OUTBID:              'outbid',
  AUCTION_ENDING_SOON:     'ending_soon',
  AUCTION_WON:             'won',
  AUCTION_LOST:            'lost',
  AUCTION_CANCELLED:       'auction',
  AUCTION_EXTENDED:        'auction',
  AUCTION_CREATED:         'auction',
  AUCTION_UNSOLD:          'auction',
  WATCHLIST_ITEM_STARTING: 'auction',
  PAYMENT_REQUIRED:        'payment_due',
  PAYMENT_REMINDER:        'payment_due',
  PAYMENT_RECEIVED:        'payment',
  PAYMENT_FAILED:          'payment_failed',
  DEPOSIT_FORFEITED:       'payment_failed',
  DEPOSIT_REFUNDED:        'refund',
}

/** Server types that also pop a toast when they arrive live. */
const TOASTED: ReadonlySet<string> = new Set([
  'BID_OUTBID', 'AUCTION_WON', 'PAYMENT_REQUIRED', 'PAYMENT_REMINDER', 'AUCTION_ENDING_SOON',
])

export function toNotificationType(serverType: string): NotificationType {
  return (CATEGORY as Record<string, NotificationType | undefined>)[serverType] ?? 'system'
}

export function toNotification(dto: NotificationDto): Notification {
  return {
    id:         dto.id,
    type:       toNotificationType(dto.type),
    serverType: dto.type,
    title:      dto.title,
    message:    dto.message,
    isRead:     dto.read,
    createdAt:  new Date(dto.createdAt),
    auctionId:  dto.auctionId ?? undefined,
    linkUrl:    dto.actionUrl ?? undefined,
  }
}

export function shouldToast(notification: Notification): boolean {
  return TOASTED.has(notification.serverType)
}
```

Create `F/components/notification/notification-style.ts`:

```ts
import {
  ArrowDown, Bell, CheckCircle2, CreditCard, Gavel, Megaphone, RotateCcw, Timer, Trophy, XCircle,
  AlertTriangle, type LucideIcon,
} from 'lucide-react'
import type { NotificationType } from '@/types/ui/notification.ui'

/** Icon + accent colour per UI category (design tokens from app/globals.css; no raw hex). */
export const NOTIFICATION_STYLE: Record<NotificationType, { icon: LucideIcon; accent: string }> = {
  outbid:         { icon: ArrowDown,     accent: 'var(--color-danger-default)' },
  won:            { icon: Trophy,        accent: 'var(--color-auction-won-accent)' },
  lost:           { icon: XCircle,       accent: 'var(--color-danger-subtle)' },
  ending_soon:    { icon: Timer,         accent: 'var(--color-auction-ending-accent)' },
  bid_placed:     { icon: Gavel,         accent: 'var(--color-brand-500)' },
  payment_due:    { icon: CreditCard,    accent: 'var(--color-warning-default)' },
  payment:        { icon: CheckCircle2,  accent: 'var(--color-success-default)' },
  payment_failed: { icon: AlertTriangle, accent: 'var(--color-danger-default)' },
  auction:        { icon: Megaphone,     accent: 'var(--color-info-default)' },
  refund:         { icon: RotateCcw,     accent: 'var(--color-success-default)' },
  system:         { icon: Bell,          accent: 'var(--muted-foreground)' },
}
```

Check that every variable used above exists in `F/app/globals.css` (`grep -o -- "--color-[a-z0-9-]*" app/globals.css | sort -u`). If one is missing (for example `--color-warning-default` or `--color-brand-500`), use the closest existing token and report it.

Create `F/lib/formatTimeAgo.ts`:

```ts
const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function isPreviousCalendarDay(date: Date, now: Date): boolean {
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  return date.getFullYear() === yesterday.getFullYear()
    && date.getMonth() === yesterday.getMonth()
    && date.getDate() === yesterday.getDate()
}

/** "just now", "5 min ago", "2 hours ago", "yesterday", "Sep 28" (or "Sep 28, 2025" in another year). */
export function formatTimeAgo(date: Date, now: Date = new Date()): string {
  const diff = now.getTime() - date.getTime()
  if (diff < MINUTE) return 'just now'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`
  if (diff < DAY) {
    const hours = Math.floor(diff / HOUR)
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`
  }
  if (isPreviousCalendarDay(date, now)) return 'yesterday'
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    ...(date.getFullYear() === now.getFullYear() ? {} : { year: 'numeric' }),
  })
}
```

Keep the existing components compiling against the widened `NotificationType`:
- `F/components/notification/NotificationPanel.tsx`: delete the `TYPE_COLOR` constant and import `NOTIFICATION_STYLE` from `./notification-style`. Replace the dot's class `TYPE_COLOR[n.type]` with an inline style. The dot `<span>` keeps `className={cn('mt-1 size-2 shrink-0 rounded-full', n.isRead && 'invisible')}` and gets `style={{ background: NOTIFICATION_STYLE[n.type].accent }}`.
- `F/components/notification/NotificationToast.tsx`: replace the `DURATION` map with one covering all categories:

```ts
const DURATION: Record<Notification['type'], number | undefined> = {
  won:            8_000,
  outbid:         12_000,
  ending_soon:    8_000,
  lost:           8_000,
  bid_placed:     4_000,
  payment_due:    undefined,  // persistent until dismissed
  payment:        5_000,
  payment_failed: 8_000,
  auction:        5_000,
  refund:         5_000,
  system:         5_000,
}
```

- In the same file's `switch`, add `case 'payment_due':` returning `toast.warning(notification.title, { ...base, icon: <CreditCard className="size-4" />, classNames: { toast: 'border-l-4 border-l-[var(--color-warning-default)]' } })`, and add `CreditCard` to the lucide import.

- [ ] **Step 4: Run the tests and type-check**

Run: `npx vitest run types/mappers/notification.mapper.test.ts lib/formatTimeAgo.test.ts`
Expected: PASS.

Run: `npx tsc --noEmit`
Expected: no errors. If `store/notificationStore.ts` or another file fails because `Notification` lost `userId`, remove that use; nothing should construct notifications with `userId` any more.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 3: Notification service and server-backed store

**Files:**
- Create: `F/services/notification.service.ts`
- Rewrite: `F/store/notificationStore.ts`
- Test: `F/services/notification.service.test.ts`, `F/store/notificationStore.test.ts` (create)

**Interfaces:**
- Consumes: `apiFetch(url, init?)` (`lib/apiClient.ts`); `ApiResponse<T>` and `PageResponse<T>` (`types/api/common.api.ts`); `NotificationDto`, `NotificationListParams`, `UnreadCountDto`, `BulkUpdateDto` and `UserNotificationMessage`; `toNotification` (Task 2).
- Produces:
  - `notificationService.list(params)`, `.unreadCount()`, `.markRead(id)`, `.markUnread(id)`, `.markAllRead()`, `.remove(id)`, `.removeRead()`, `.removeAll()`. Each returns the parsed `ApiResponse` and throws the parsed error body on a non-2xx response.
  - `useNotificationStore` with state `{ notifications: Notification[] (≤ RECENT_LIMIT, newest first), unreadCount: number, loading: boolean }`
  - Actions: `loadRecent(): Promise<void>`, `refreshCount(): Promise<void>`, `applyPush(msg: UserNotificationMessage): Notification | null`, `markRead(id): Promise<boolean>`, `markAllRead(): Promise<boolean>`, `remove(id): Promise<boolean>`, `reset(): void`
  - `RECENT_LIMIT = 10`

- [ ] **Step 1: Write the failing tests**

Create `F/services/notification.service.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/apiClient', () => ({ apiFetch: vi.fn() }))

import { apiFetch } from '@/lib/apiClient'
import { notificationService } from './notification.service'

const mockFetch = vi.mocked(apiFetch)

function ok(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('notificationService', () => {
  beforeEach(() => mockFetch.mockReset())

  it('lists with page, size, read, each type and a trimmed search', async () => {
    mockFetch.mockResolvedValue(ok({ data: { data: [], pagination: {} } }))

    await notificationService.list({ page: 1, size: 20, read: false, types: ['BID_OUTBID', 'AUCTION_WON'], search: ' watch ' })

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/notifications?page=1&size=20&read=false&types=BID_OUTBID&types=AUCTION_WON&search=watch')
  })

  it('omits unset filters', async () => {
    mockFetch.mockResolvedValue(ok({ data: { data: [], pagination: {} } }))

    await notificationService.list({})

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/notifications?page=0&size=20')
  })

  it('uses the right method and path for each action', async () => {
    mockFetch.mockResolvedValue(ok({ data: {} }))

    await notificationService.unreadCount()
    await notificationService.markRead('n-1')
    await notificationService.markUnread('n-1')
    await notificationService.markAllRead()
    await notificationService.remove('n-1')
    await notificationService.removeRead()
    await notificationService.removeAll()

    expect(mockFetch.mock.calls).toEqual([
      ['/api/v1/notifications/unread-count'],
      ['/api/v1/notifications/n-1/read', { method: 'PUT' }],
      ['/api/v1/notifications/n-1/unread', { method: 'PUT' }],
      ['/api/v1/notifications/mark-all-read', { method: 'PUT' }],
      ['/api/v1/notifications/n-1', { method: 'DELETE' }],
      ['/api/v1/notifications/delete-read', { method: 'DELETE' }],
      ['/api/v1/notifications/delete-all', { method: 'DELETE' }],
    ])
  })

  it('throws the error body on failure', async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ errorCode: 'NOT_FOUND' }), { status: 404 }))

    await expect(notificationService.markRead('x')).rejects.toEqual({ errorCode: 'NOT_FOUND' })
  })
})
```

Create `F/store/notificationStore.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/notification.service', () => ({
  notificationService: {
    list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn(), remove: vi.fn(),
  },
}))

import { notificationService } from '@/services/notification.service'
import type { NotificationDto } from '@/types/api/notification.api'
import { RECENT_LIMIT, useNotificationStore } from './notificationStore'

const service = vi.mocked(notificationService)

function dto(id: string, read = false): NotificationDto {
  return {
    id, type: 'BID_OUTBID', title: 't', message: 'm', actionUrl: null, auctionId: null, metadata: null,
    read, createdAt: '2026-10-01T10:00:00Z',
  }
}

const store = () => useNotificationStore.getState()

describe('notificationStore', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useNotificationStore.getState().reset()
  })

  it('loadRecent fetches the latest page and maps it', async () => {
    service.list.mockResolvedValue({ data: { data: [dto('a'), dto('b', true)], pagination: {} } } as never)

    await store().loadRecent()

    expect(service.list).toHaveBeenCalledWith({ page: 0, size: RECENT_LIMIT })
    expect(store().notifications.map((n) => n.id)).toEqual(['a', 'b'])
    expect(store().loading).toBe(false)
  })

  it('refreshCount takes the server count', async () => {
    service.unreadCount.mockResolvedValue({ data: { count: 7 } } as never)

    await store().refreshCount()

    expect(store().unreadCount).toBe(7)
  })

  it('applyPush prepends, caps the list and uses the server count, not +1', () => {
    useNotificationStore.setState({
      notifications: Array.from({ length: RECENT_LIMIT }, (_, i) => ({
        id: `old-${i}`, type: 'system' as const, serverType: 'X', title: '', message: '', isRead: true, createdAt: new Date(),
      })),
      unreadCount: 1,
    })

    const added = store().applyPush({ type: 'NOTIFICATION', notification: dto('new'), unreadCount: 5 })

    expect(added?.id).toBe('new')
    expect(store().notifications[0].id).toBe('new')
    expect(store().notifications).toHaveLength(RECENT_LIMIT)
    expect(store().unreadCount).toBe(5)
  })

  it('applyPush ignores a duplicate id', () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 1 })

    const again = store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 9 })

    expect(again).toBeNull()
    expect(store().notifications).toHaveLength(1)
    expect(store().unreadCount).toBe(1)
  })

  it('markRead is optimistic and rolls back on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 3 })
    let reject: (e: unknown) => void = () => {}
    service.markRead.mockReturnValue(new Promise((_, r) => { reject = r }) as never)

    const pending = store().markRead('a')
    expect(store().notifications[0].isRead).toBe(true)
    expect(store().unreadCount).toBe(2)

    reject({ errorCode: 'X' })
    expect(await pending).toBe(false)
    expect(store().notifications[0].isRead).toBe(false)
    expect(store().unreadCount).toBe(3)
  })

  it('markAllRead zeroes the count and rolls back on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 4 })
    service.markAllRead.mockRejectedValue({ errorCode: 'X' })

    const saved = await store().markAllRead()

    expect(saved).toBe(false)
    expect(store().unreadCount).toBe(4)
    expect(store().notifications[0].isRead).toBe(false)
  })

  it('markAllRead keeps the optimistic state on success', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 4 })
    service.markAllRead.mockResolvedValue({ data: { updated: 4 } } as never)

    expect(await store().markAllRead()).toBe(true)
    expect(store().unreadCount).toBe(0)
    expect(store().notifications[0].isRead).toBe(true)
  })

  it('remove drops the item (and its unread count) and rolls back on failure', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 2 })
    service.remove.mockRejectedValue({ errorCode: 'X' })

    const saved = await store().remove('a')

    expect(saved).toBe(false)
    expect(store().notifications.map((n) => n.id)).toEqual(['a'])
    expect(store().unreadCount).toBe(2)
  })

  it('remove succeeds and decrements for an unread item', async () => {
    store().applyPush({ type: 'NOTIFICATION', notification: dto('a'), unreadCount: 2 })
    service.remove.mockResolvedValue({ data: 'ok' } as never)

    expect(await store().remove('a')).toBe(true)
    expect(store().notifications).toEqual([])
    expect(store().unreadCount).toBe(1)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npx vitest run services/notification.service.test.ts store/notificationStore.test.ts`
Expected: FAIL. `notification.service` cannot be resolved, and `RECENT_LIMIT`, `loadRecent` and the rest are not defined.

- [ ] **Step 3: Implement**

Create `F/services/notification.service.ts`:

```ts
import { apiFetch } from '@/lib/apiClient'
import type { ApiResponse, PageResponse } from '@/types/api/common.api'
import type {
  BulkUpdateDto, NotificationDto, NotificationListParams, UnreadCountDto,
} from '@/types/api/notification.api'

const BASE = '/api/v1/notifications'

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await response.json()
  }
  return response.json()
}

export const notificationService = {
  async list(params: NotificationListParams = {}): Promise<ApiResponse<PageResponse<NotificationDto>>> {
    const query = new URLSearchParams()
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    if (params.read !== undefined) query.set('read', String(params.read))
    params.types?.forEach((type) => query.append('types', type))
    const search = params.search?.trim()
    if (search) query.set('search', search)
    return parse(await apiFetch(`${BASE}?${query}`))
  },

  async unreadCount(): Promise<ApiResponse<UnreadCountDto>> {
    return parse(await apiFetch(`${BASE}/unread-count`))
  },

  async markRead(id: string): Promise<ApiResponse<NotificationDto>> {
    return parse(await apiFetch(`${BASE}/${id}/read`, { method: 'PUT' }))
  },

  async markUnread(id: string): Promise<ApiResponse<NotificationDto>> {
    return parse(await apiFetch(`${BASE}/${id}/unread`, { method: 'PUT' }))
  },

  async markAllRead(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/mark-all-read`, { method: 'PUT' }))
  },

  async remove(id: string): Promise<ApiResponse<string>> {
    return parse(await apiFetch(`${BASE}/${id}`, { method: 'DELETE' }))
  },

  async removeRead(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/delete-read`, { method: 'DELETE' }))
  },

  async removeAll(): Promise<ApiResponse<BulkUpdateDto>> {
    return parse(await apiFetch(`${BASE}/delete-all`, { method: 'DELETE' }))
  },
}
```

Replace `F/store/notificationStore.ts` with:

```ts
import { create } from 'zustand'
import { notificationService } from '@/services/notification.service'
import { toNotification } from '@/types/mappers/notification.mapper'
import type { UserNotificationMessage } from '@/types/api/notification.api'
import type { Notification } from '@/types/ui/notification.ui'

export const RECENT_LIMIT = 10

interface NotificationState {
  /** The most recent notifications (≤ RECENT_LIMIT), newest first — the bell dropdown. */
  notifications: Notification[]
  unreadCount:   number
  loading:       boolean
  loadRecent:    () => Promise<void>
  refreshCount:  () => Promise<void>
  /** Applies a live push; returns the added notification, or null for a duplicate. */
  applyPush:     (msg: UserNotificationMessage) => Notification | null
  /** Optimistic; resolves false (state rolled back) if the server rejected it. */
  markRead:      (id: string) => Promise<boolean>
  markAllRead:   () => Promise<boolean>
  remove:        (id: string) => Promise<boolean>
  reset:         () => void
}

export const useNotificationStore = create<NotificationState>((set, get) => {
  /** Runs an optimistic change; restores the previous list and count if the request fails. */
  async function optimistic(change: () => void, request: () => Promise<unknown>): Promise<boolean> {
    const { notifications, unreadCount } = get()
    change()
    try {
      await request()
      return true
    } catch {
      set({ notifications, unreadCount })
      return false
    }
  }

  return {
    notifications: [],
    unreadCount:   0,
    loading:       false,

    loadRecent: async () => {
      set({ loading: true })
      try {
        const res = await notificationService.list({ page: 0, size: RECENT_LIMIT })
        set({ notifications: res.data.data.map(toNotification) })
      } catch {
        // keep what we have; the next focus / reconnect retries
      } finally {
        set({ loading: false })
      }
    },

    refreshCount: async () => {
      try {
        const res = await notificationService.unreadCount()
        set({ unreadCount: res.data.count })
      } catch {
        // keep the last known count
      }
    },

    applyPush: (msg) => {
      if (get().notifications.some((n) => n.id === msg.notification.id)) return null
      const added = toNotification(msg.notification)
      set((s) => ({
        notifications: [added, ...s.notifications].slice(0, RECENT_LIMIT),
        unreadCount:   msg.unreadCount,
      }))
      return added
    },

    markRead: (id) => {
      const target = get().notifications.find((n) => n.id === id)
      return optimistic(
        () => {
          if (!target || target.isRead) return
          set((s) => ({
            notifications: s.notifications.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
            unreadCount:   Math.max(0, s.unreadCount - 1),
          }))
        },
        () => notificationService.markRead(id),
      )
    },

    markAllRead: () =>
      optimistic(
        () => set((s) => ({ notifications: s.notifications.map((n) => ({ ...n, isRead: true })), unreadCount: 0 })),
        () => notificationService.markAllRead(),
      ),

    remove: (id) => {
      const target = get().notifications.find((n) => n.id === id)
      return optimistic(
        () => set((s) => ({
          notifications: s.notifications.filter((n) => n.id !== id),
          unreadCount:   target && !target.isRead ? Math.max(0, s.unreadCount - 1) : s.unreadCount,
        })),
        () => notificationService.remove(id),
      )
    },

    reset: () => set({ notifications: [], unreadCount: 0, loading: false }),
  }
})
```

Keep the current UI compiling until Task 5 replaces it:
- `NotificationPanel` uses `notifications`, `unreadCount`, `markRead` and `markAllRead`. Their names are unchanged, and the async return values are fine for `onClick`.
- `hooks/useNotifications.ts` still returns the store.
- Run `grep -rn "addNotification\|removeNotification" frontend --include=*.ts --include=*.tsx --exclude-dir=node_modules` and remove any remaining caller.

- [ ] **Step 4: Run the tests and type-check**

Run: `npx vitest run services/notification.service.test.ts store/notificationStore.test.ts`
Expected: PASS.

Run: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 4: Live connection, toasts, and removing the auction page's user-queue subscription

**Files:**
- Modify: `F/lib/realtime/dispatch.ts`, `F/lib/realtime/dispatch.test.ts`, `F/hooks/useAuctionSocket.ts`, `F/app/layout.tsx`
- Create: `F/hooks/useUserNotifications.ts`, `F/components/notification/UserNotificationsBridge.tsx`
- Modify: `F/components/notification/index.ts`

**Interfaces:**
- Consumes:
  - `useNotificationStore` (`applyPush`, `loadRecent`, `refreshCount`, `reset`) from Task 3
  - `shouldToast` (Task 2) and `showNotificationToast(notification)` (existing, updated in Task 2)
  - `resolveWsEndpoint` and `withAccessToken` (`lib/realtime/ws-url.ts`)
  - `getFreshAccessToken` (`lib/apiClient.ts`)
  - `useAuthStore` (`user?.id`, `user?.role`)
- Produces:
  - `parseUserNotificationMessage(body: string): UserNotificationMessage | null`, which replaces `parseUserNotification`
  - `useUserNotifications(): void`
  - `<UserNotificationsBridge />`, which renders null and is mounted once in the root layout

- [ ] **Step 1: Write the failing test**

In `F/lib/realtime/dispatch.test.ts`:
- Change the import from `parseUserNotification` to `parseUserNotificationMessage`.
- Replace the `describe('parseUserNotification', …)` block with:

```ts
describe('parseUserNotificationMessage', () => {
  it('returns the envelope with the server unread count', () => {
    const body = JSON.stringify({ type: 'NOTIFICATION', notification, unreadCount: 3 })

    expect(parseUserNotificationMessage(body)).toEqual({ type: 'NOTIFICATION', notification, unreadCount: 3 })
  })

  it('rejects other envelopes, malformed JSON, incomplete notifications and a missing count', () => {
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'OUTBID', auctionId: 'a-1', payload: {} }))).toBeNull()
    expect(parseUserNotificationMessage('not json')).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', unreadCount: 1 }))).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', notification: { id: 'n-1' }, unreadCount: 1 }))).toBeNull()
    expect(parseUserNotificationMessage(JSON.stringify({ type: 'NOTIFICATION', notification }))).toBeNull()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run lib/realtime/dispatch.test.ts`
Expected: FAIL (`parseUserNotificationMessage` is not exported).

- [ ] **Step 3: Implement**

In `F/lib/realtime/dispatch.ts`, change the type import to `import type { UserNotificationMessage } from '@/types/api/notification.api'` and replace `parseUserNotification` with:

```ts
/** A `/user/queue/notifications` NOTIFICATION envelope (notification + server unread count), or null. */
export function parseUserNotificationMessage(body: string): UserNotificationMessage | null {
  const parsed = parseJson(body)
  if (!isRecord(parsed) || parsed.type !== 'NOTIFICATION' || !isRecord(parsed.notification)) return null
  if (typeof parsed.unreadCount !== 'number') return null
  const n = parsed.notification
  if (typeof n.id !== 'string' || typeof n.type !== 'string' || typeof n.message !== 'string') return null
  return parsed as unknown as UserNotificationMessage
}
```

In `F/hooks/useAuctionSocket.ts`:
- Delete the `onUserMessage` function and its comment.
- Delete the `USER_QUEUE` constant.
- Delete the line `if (userId) client.subscribe(USER_QUEUE, onUserMessage)`.
- Remove `parseUserNotification` from the dispatch import.
- Keep the `toast` import, which the extended handler still uses, and keep the `beforeConnect` token logic.
- Update the hook's doc comment so it no longer mentions the private queue: "Subscribes to the auction topic; personal notifications (incl. outbid toasts) come from `useUserNotifications`."

Create `F/hooks/useUserNotifications.ts`:

```ts
'use client'

import { useEffect } from 'react'
import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'
import { useAuthStore } from '@/store/authStore'
import { useNotificationStore } from '@/store/notificationStore'
import { getFreshAccessToken } from '@/lib/apiClient'
import { parseUserNotificationMessage } from '@/lib/realtime/dispatch'
import { resolveWsEndpoint, withAccessToken } from '@/lib/realtime/ws-url'
import { shouldToast } from '@/types/mappers/notification.mapper'
import { showNotificationToast } from '@/components/notification/NotificationToast'

const USER_QUEUE = '/user/queue/notifications'

/**
 * Personal notifications for the logged-in (non-admin) user: loads the recent list and unread count, keeps them live
 * from `/user/queue/notifications`, toasts the important types, and resyncs on every (re)connect and window focus
 * (which also picks up changes made in another tab). Mount once (UserNotificationsBridge in the root layout).
 */
export function useUserNotifications(): void {
  const userId = useAuthStore((s) => s.user?.id ?? null)
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN')

  useEffect(() => {
    const store = useNotificationStore.getState()
    if (!userId || isAdmin) {
      store.reset()
      return
    }

    const resync = () => {
      const s = useNotificationStore.getState()
      void s.loadRecent()
      void s.refreshCount()
    }
    resync()
    window.addEventListener('focus', resync)

    const endpoint = resolveWsEndpoint({
      wsUrl:  process.env.NEXT_PUBLIC_WS_URL,
      apiUrl: process.env.NEXT_PUBLIC_API_URL,
    })

    const onMessage = (message: IMessage) => {
      const parsed = parseUserNotificationMessage(message.body)
      if (!parsed) return
      const added = useNotificationStore.getState().applyPush(parsed)
      if (added && shouldToast(added)) showNotificationToast(added)
    }

    const client = new Client({
      reconnectDelay:    1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 0, // the broker has no heartbeats configured
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        c.brokerURL = withAccessToken(endpoint, await getFreshAccessToken())
      },
      onConnect: () => {
        client.subscribe(USER_QUEUE, onMessage)
        resync() // recover anything pushed while disconnected
      },
    })
    client.activate()

    return () => {
      window.removeEventListener('focus', resync)
      void client.deactivate()
    }
  }, [userId, isAdmin])
}
```

Before relying on it, check that `user.role` exists on the auth store's user type (`F/store/authStore.ts`). Use the actual field name if it differs.

Create `F/components/notification/UserNotificationsBridge.tsx`:

```tsx
'use client'

import { useUserNotifications } from '@/hooks/useUserNotifications'

/** Mounts the personal-notification connection once for the whole app (root layout). Renders nothing. */
export function UserNotificationsBridge() {
  useUserNotifications()
  return null
}
```

In `F/app/layout.tsx`, import `{ UserNotificationsBridge } from '@/components/notification/UserNotificationsBridge'` and render `<UserNotificationsBridge />` inside `<ThemeProvider>`, just before `<Toaster position="bottom-right" />`.

In `F/components/notification/index.ts`, add `export { UserNotificationsBridge } from './UserNotificationsBridge'`.

`NotificationToast.tsx` is imported by a hook now. It contains JSX, so it must stay a `.tsx` module, and its exports are unchanged. No change is needed beyond Task 2.

- [ ] **Step 4: Run the tests, type-check and build**

Run: `npx vitest run lib/realtime/dispatch.test.ts`
Expected: PASS.

Run: `npx tsc --noEmit && npm run lint`
Expected: no errors. If `grep -rn "parseUserNotification\b" frontend --include=*.ts --include=*.tsx --exclude-dir=node_modules` finds anything, it is a leftover reference; remove it.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 5: Bell, dropdown and shared `NotificationItem`

**Files:**
- Create: `F/components/notification/NotificationItem.tsx`
- Rewrite: `F/components/notification/NotificationPanel.tsx`, `F/components/notification/NotificationBell.tsx`
- Delete: `F/hooks/useNotifications.ts`, and update any import of it

**Interfaces:**
- Consumes: `useNotificationStore` (Task 3); `NOTIFICATION_STYLE` and `formatTimeAgo` (Task 2); `Popover`, `PopoverTrigger`, `PopoverContent`, `Button`, `ScrollArea` and `Separator` (`components/ui`); `useRouter` and `Link` from Next.
- Produces:
  - `NotificationItem({ notification, onSelect, leading?, trailing?, className? })`, which renders the icon, title, message, time ago and unread dot. The whole row is one button that calls `onSelect`. `leading` and `trailing` are slots used by the full page for a checkbox and row actions.
  - `NotificationPanel({ onNavigate })`
  - `NotificationBell()`

Read `docs/design-system.md` before writing UI: the dropdown/popover z-index, radius, motion (`duration`/`easing` tokens), focus ring and the notification patterns. Base UI's Popover already closes on `Esc` and returns focus to the trigger. Keep that behaviour (do not stop propagation of `Escape`), and verify it manually.

- [ ] **Step 1: Implement `NotificationItem`**

Create `F/components/notification/NotificationItem.tsx`:

```tsx
'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { formatTimeAgo } from '@/lib/formatTimeAgo'
import { NOTIFICATION_STYLE } from './notification-style'
import type { Notification } from '@/types/ui/notification.ui'

interface NotificationItemProps {
  notification: Notification
  onSelect:     (notification: Notification) => void
  /** e.g. a selection checkbox (full page) — rendered outside the clickable area */
  leading?:     ReactNode
  /** e.g. row actions (full page) — rendered outside the clickable area */
  trailing?:    ReactNode
  className?:   string
}

export function NotificationItem({ notification: n, onSelect, leading, trailing, className }: NotificationItemProps) {
  const { icon: Icon, accent } = NOTIFICATION_STYLE[n.type]
  return (
    <div className={cn('flex items-start gap-2 px-3 py-3', !n.isRead && 'bg-accent/50', className)}>
      {leading}
      <button
        type="button"
        onClick={() => onSelect(n)}
        className="flex min-w-0 flex-1 items-start gap-2.5 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={`${n.isRead ? '' : 'Unread: '}${n.title}`}
      >
        <span
          className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full"
          style={{ color: accent, background: `color-mix(in oklab, ${accent} 15%, transparent)` }}
          aria-hidden
        >
          <Icon className="size-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className={cn('block text-sm', !n.isRead && 'font-medium')}>{n.title}</span>
          <span className="block text-xs text-muted-foreground line-clamp-2">{n.message}</span>
          <time className="mt-0.5 block text-[10px] text-muted-foreground" dateTime={n.createdAt.toISOString()}>
            {formatTimeAgo(n.createdAt)}
          </time>
        </span>
        <span
          className={cn('mt-1.5 size-2 shrink-0 rounded-full bg-primary', n.isRead && 'invisible')}
          aria-hidden
        />
      </button>
      {trailing}
    </div>
  )
}
```

- [ ] **Step 2: Implement the panel**

Replace `F/components/notification/NotificationPanel.tsx` with:

```tsx
'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Bell, CheckCheck } from 'lucide-react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useNotificationStore } from '@/store/notificationStore'
import { NotificationItem } from './NotificationItem'
import type { Notification } from '@/types/ui/notification.ui'

interface NotificationPanelProps {
  /** Called when the user follows a link from the panel (closes the popover). */
  onNavigate: () => void
}

export function NotificationPanel({ onNavigate }: NotificationPanelProps) {
  const router = useRouter()
  const notifications = useNotificationStore((s) => s.notifications)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const loading = useNotificationStore((s) => s.loading)
  const markRead = useNotificationStore((s) => s.markRead)
  const markAllRead = useNotificationStore((s) => s.markAllRead)

  const select = (n: Notification) => {
    if (!n.isRead) void markRead(n.id)
    onNavigate()
    router.push(n.linkUrl ?? '/notifications')
  }

  const readAll = async () => {
    if (!(await markAllRead())) toast.error('Could not mark notifications as read. Please try again.')
  }

  return (
    <div className="flex w-[min(22rem,calc(100vw-2rem))] flex-col">
      <div className="flex items-center justify-between border-b px-3 py-2.5">
        <div className="flex items-center gap-2">
          <Bell className="size-4" aria-hidden />
          <span className="text-sm font-medium">Notifications</span>
          {unreadCount > 0 && (
            <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-medium text-primary-foreground">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <Button variant="ghost" size="xs" onClick={readAll} className="gap-1 text-xs">
            <CheckCheck className="size-3" aria-hidden />
            Mark all as read
          </Button>
        )}
      </div>

      {loading && notifications.length === 0 ? (
        <div className="flex flex-col gap-3 p-3" aria-busy>
          {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}
        </div>
      ) : notifications.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">You&apos;re all caught up!</p>
      ) : (
        <ScrollArea className="max-h-96">
          <ul>
            {notifications.map((n, i) => (
              <li key={n.id}>
                {i > 0 && <Separator />}
                <NotificationItem notification={n} onSelect={select} className="hover:bg-accent transition-colors" />
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}

      <div className="border-t p-1.5">
        <Button variant="ghost" size="sm" className="w-full" render={<Link href="/notifications" onClick={onNavigate} />} nativeButton={false}>
          View all notifications
        </Button>
      </div>
    </div>
  )
}
```

If `Skeleton`, `Button size="xs"` or `Button render={…} nativeButton={false}` do not exist with these props in `components/ui`, check `components/layout/Header.tsx` for how Button-as-Link is done and adapt. Report the adaptation.

- [ ] **Step 3: Implement the bell**

Replace `F/components/notification/NotificationBell.tsx` with:

```tsx
'use client'

import { useState } from 'react'
import { Bell, BellRing } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { useNotificationStore } from '@/store/notificationStore'
import { NotificationPanel } from './NotificationPanel'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const hasUnread = unreadCount > 0
  const label = hasUnread ? `Notifications, ${unreadCount} unread` : 'Notifications'

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger render={<Button variant="ghost" size="icon" className="relative" aria-label={label} />}>
        {hasUnread ? <BellRing className="size-5" aria-hidden /> : <Bell className="size-5" aria-hidden />}
        {hasUnread && (
          <span
            className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-0.5 text-[10px] font-medium text-white"
            aria-hidden
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent side="bottom" align="end" sideOffset={8} className="w-auto p-0">
        <NotificationPanel onNavigate={() => setOpen(false)} />
      </PopoverContent>
    </Popover>
  )
}
```

If Base UI's `Popover` root uses different controlled-state props (check `components/ui/popover.tsx` → `PopoverPrimitive.Root.Props`), use the actual names, typically `open` and `onOpenChange(open, eventDetails)`.

Delete `F/hooks/useNotifications.ts`. Run `grep -rn "useNotifications" frontend --exclude-dir=node_modules`; it must find nothing apart from `useUserNotifications`.

- [ ] **Step 4: Type-check, lint and build**

Run (from `frontend/`): `npx tsc --noEmit && npm run lint && npm test`
Expected: no errors; all tests pass.

- [ ] **Step 5: Commit**: skipped (the user commits).

---

### Task 6: Full notifications page

**Files:**
- Create: `F/lib/notification-filters.ts`, `F/lib/notification-filters.test.ts`, `F/app/(dashboard)/notifications/page.tsx`

**Interfaces:**
- Consumes:
  - `notificationService` (Task 3)
  - `useNotificationStore` (`markAllRead`, `refreshCount`, `loadRecent`)
  - `toNotification` (Task 2) and `NotificationItem` (Task 5)
  - UI primitives: `Tabs`, `TabsList`, `TabsTrigger`, `Select*`, `Input`, `Checkbox`, `Button`, `Dialog*` and `Skeleton`
- Produces:
  - `interface NotificationFilters { tab: 'all' | 'unread'; type: string | null; q: string; page: number }`, where `page` is 1-based
  - `parseNotificationFilters(params: URLSearchParams): NotificationFilters`
  - `toNotificationSearchParams(f: NotificationFilters): URLSearchParams`, which omits defaults
  - `toListParams(f: NotificationFilters, size: number): NotificationListParams`
  - `NOTIFICATION_TYPE_FILTERS: ReadonlyArray<{ value: string; label: string }>`
  - `PAGE_SIZE = 20`

- [ ] **Step 1: Write the failing test**

Create `F/lib/notification-filters.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import {
  NOTIFICATION_TYPE_FILTERS, parseNotificationFilters, toListParams, toNotificationSearchParams,
} from './notification-filters'
import { NOTIFICATION_SERVER_TYPES } from '@/types/api/notification.api'

describe('notification filters', () => {
  it('parses defaults from empty params', () => {
    expect(parseNotificationFilters(new URLSearchParams())).toEqual({ tab: 'all', type: null, q: '', page: 1 })
  })

  it('parses every param and ignores invalid values', () => {
    expect(parseNotificationFilters(new URLSearchParams('tab=unread&type=BID_OUTBID&q=watch&page=3')))
      .toEqual({ tab: 'unread', type: 'BID_OUTBID', q: 'watch', page: 3 })
    expect(parseNotificationFilters(new URLSearchParams('tab=bogus&type=NOPE&page=-2')))
      .toEqual({ tab: 'all', type: null, q: '', page: 1 })
  })

  it('round-trips through search params, omitting defaults', () => {
    const f = { tab: 'unread' as const, type: 'AUCTION_WON', q: 'vase', page: 2 }
    expect(toNotificationSearchParams(f).toString()).toBe('tab=unread&type=AUCTION_WON&q=vase&page=2')
    expect(parseNotificationFilters(toNotificationSearchParams(f))).toEqual(f)
    expect(toNotificationSearchParams({ tab: 'all', type: null, q: '', page: 1 }).toString()).toBe('')
  })

  it('maps to the 0-based API query', () => {
    expect(toListParams({ tab: 'unread', type: 'BID_OUTBID', q: ' watch ', page: 2 }, 20))
      .toEqual({ page: 1, size: 20, read: false, types: ['BID_OUTBID'], search: 'watch' })
    expect(toListParams({ tab: 'all', type: null, q: '', page: 1 }, 20)).toEqual({ page: 0, size: 20 })
  })

  it('offers only real backend types in the type filter', () => {
    for (const { value } of NOTIFICATION_TYPE_FILTERS) {
      expect(NOTIFICATION_SERVER_TYPES).toContain(value)
    }
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run lib/notification-filters.test.ts`
Expected: FAIL (the module cannot be resolved).

- [ ] **Step 3: Implement the filters module**

Create `F/lib/notification-filters.ts`:

```ts
import { NOTIFICATION_SERVER_TYPES, type NotificationListParams } from '@/types/api/notification.api'

export const PAGE_SIZE = 20

export interface NotificationFilters {
  tab:  'all' | 'unread'
  type: string | null
  q:    string
  /** 1-based (URL) */
  page: number
}

/** User-facing types offered in the filter (account/system types omitted). */
export const NOTIFICATION_TYPE_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'BID_OUTBID',          label: 'Outbid' },
  { value: 'AUCTION_ENDING_SOON', label: 'Ending soon' },
  { value: 'AUCTION_WON',         label: 'Won' },
  { value: 'AUCTION_LOST',        label: 'Lost' },
  { value: 'PAYMENT_REQUIRED',    label: 'Payment required' },
  { value: 'PAYMENT_REMINDER',    label: 'Payment reminder' },
  { value: 'PAYMENT_RECEIVED',    label: 'Payment received' },
  { value: 'PAYMENT_FAILED',      label: 'Payment failed' },
  { value: 'DEPOSIT_REFUNDED',    label: 'Deposit refunded' },
  { value: 'NEW_BID',             label: 'New bid (seller)' },
  { value: 'FIRST_BID',           label: 'First bid (seller)' },
  { value: 'AUCTION_CREATED',     label: 'Auction live' },
  { value: 'AUCTION_EXTENDED',    label: 'Auction extended' },
  { value: 'AUCTION_CANCELLED',   label: 'Auction cancelled' },
  { value: 'AUCTION_UNSOLD',      label: 'Auction unsold' },
]

const KNOWN_TYPES: ReadonlySet<string> = new Set(NOTIFICATION_SERVER_TYPES)

export function parseNotificationFilters(params: URLSearchParams): NotificationFilters {
  const type = params.get('type')
  const page = Number.parseInt(params.get('page') ?? '', 10)
  return {
    tab:  params.get('tab') === 'unread' ? 'unread' : 'all',
    type: type && KNOWN_TYPES.has(type) ? type : null,
    q:    params.get('q') ?? '',
    page: Number.isFinite(page) && page >= 1 ? page : 1,
  }
}

export function toNotificationSearchParams(f: NotificationFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (f.tab === 'unread') params.set('tab', 'unread')
  if (f.type) params.set('type', f.type)
  if (f.q) params.set('q', f.q)
  if (f.page > 1) params.set('page', String(f.page))
  return params
}

export function toListParams(f: NotificationFilters, size: number): NotificationListParams {
  const params: NotificationListParams = { page: f.page - 1, size }
  if (f.tab === 'unread') params.read = false
  if (f.type) params.types = [f.type]
  const search = f.q.trim()
  if (search) params.search = search
  return params
}
```

Run: `npx vitest run lib/notification-filters.test.ts`
Expected: PASS.

- [ ] **Step 4: Implement the page**

Read the Next 16 docs on `useSearchParams`, `useRouter` and client pages first (`frontend/node_modules/next/dist/docs/`). Follow `app/(auth)/login/page.tsx`: an outer default export wraps the client component that uses `useSearchParams` in `<Suspense>`. Read `docs/design-system.md` for list, empty-state and dialog patterns.

Create `F/app/(dashboard)/notifications/page.tsx` with this behaviour (complete code below):

```tsx
'use client'

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { BellOff, CheckCheck, Mail, MailOpen, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { NotificationItem } from '@/components/notification/NotificationItem'
import { notificationService } from '@/services/notification.service'
import { useNotificationStore } from '@/store/notificationStore'
import { toNotification } from '@/types/mappers/notification.mapper'
import {
  NOTIFICATION_TYPE_FILTERS, PAGE_SIZE, parseNotificationFilters, toListParams, toNotificationSearchParams,
  type NotificationFilters,
} from '@/lib/notification-filters'
import type { Notification } from '@/types/ui/notification.ui'

const ALL_TYPES = 'all'

type Confirm = { kind: 'selected'; ids: string[] } | { kind: 'read' } | { kind: 'one'; id: string } | null

function NotificationsView() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const filters = useMemo(() => parseNotificationFilters(new URLSearchParams(searchParams.toString())), [searchParams])

  const [items, setItems] = useState<Notification[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [search, setSearch] = useState(filters.q)
  const [confirm, setConfirm] = useState<Confirm>(null)

  const navigate = useCallback((next: NotificationFilters) => {
    const qs = toNotificationSearchParams(next).toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [pathname, router])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await notificationService.list(toListParams(filters, PAGE_SIZE))
      setItems(res.data.data.map(toNotification))
      setTotalPages(Math.max(1, res.data.pagination.totalPages))
    } catch {
      toast.error('Could not load notifications.')
    } finally {
      setLoading(false)
      setSelected(new Set())
    }
  }, [filters])

  useEffect(() => { void load() }, [load])

  // Debounced search → URL (resets to page 1)
  useEffect(() => {
    if (search === filters.q) return
    const timer = setTimeout(() => navigate({ ...filters, q: search, page: 1 }), 300)
    return () => clearTimeout(timer)
  }, [search, filters, navigate])

  /** Reloads this page and the bell (list + count) after a change. */
  const afterChange = useCallback(async () => {
    const store = useNotificationStore.getState()
    await Promise.all([load(), store.refreshCount(), store.loadRecent()])
  }, [load])

  const run = async (requests: Promise<unknown>[], failure: string) => {
    const results = await Promise.allSettled(requests)
    if (results.some((r) => r.status === 'rejected')) toast.error(failure)
    await afterChange()
  }

  const open = (n: Notification) => {
    if (!n.isRead) void run([notificationService.markRead(n.id)], 'Could not mark as read.')
    if (n.linkUrl) router.push(n.linkUrl)
  }

  const toggleRead = (n: Notification) =>
    run([n.isRead ? notificationService.markUnread(n.id) : notificationService.markRead(n.id)], 'Could not update.')

  const markSelectedRead = () =>
    run(items.filter((n) => selected.has(n.id) && !n.isRead).map((n) => notificationService.markRead(n.id)),
      'Some notifications could not be marked as read.')

  const markAllRead = async () => {
    if (!(await useNotificationStore.getState().markAllRead())) toast.error('Could not mark all as read.')
    await afterChange()
  }

  const confirmDelete = async () => {
    const c = confirm
    setConfirm(null)
    if (!c) return
    if (c.kind === 'read') await run([notificationService.removeRead()], 'Could not delete read notifications.')
    if (c.kind === 'one') await run([notificationService.remove(c.id)], 'Could not delete.')
    if (c.kind === 'selected') await run(c.ids.map((id) => notificationService.remove(id)), 'Some notifications could not be deleted.')
  }

  const allSelected = items.length > 0 && items.every((n) => selected.has(n.id))
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(items.map((n) => n.id)))
  const toggleOne = (id: string) => setSelected((prev) => {
    const next = new Set(prev)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

  const filtered = filters.tab === 'unread' || filters.type !== null || filters.q !== ''

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-semibold">Notifications</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={markAllRead} className="gap-1.5">
            <CheckCheck className="size-4" aria-hidden /> Mark all as read
          </Button>
          <Button variant="outline" size="sm" onClick={() => setConfirm({ kind: 'read' })} className="gap-1.5">
            <Trash2 className="size-4" aria-hidden /> Delete read
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Tabs value={filters.tab} onValueChange={(tab) => navigate({ ...filters, tab: tab === 'unread' ? 'unread' : 'all', page: 1 })}>
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="unread">Unread</TabsTrigger>
          </TabsList>
        </Tabs>
        <Select
          value={filters.type ?? ALL_TYPES}
          onValueChange={(v) => navigate({ ...filters, type: !v || v === ALL_TYPES ? null : String(v), page: 1 })}
        >
          <SelectTrigger className="w-full sm:w-52" aria-label="Filter by type">
            <SelectValue>
              {NOTIFICATION_TYPE_FILTERS.find((t) => t.value === filters.type)?.label ?? 'All types'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_TYPES}>All types</SelectItem>
            {NOTIFICATION_TYPE_FILTERS.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search notifications"
          aria-label="Search notifications"
          maxLength={100}
          className="sm:max-w-xs"
        />
      </div>

      {items.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-lg border px-3 py-2">
          <Checkbox checked={allSelected} onCheckedChange={toggleAll} aria-label="Select all on this page" />
          <span className="text-sm text-muted-foreground">{selected.size} selected</span>
          <Button variant="ghost" size="sm" disabled={selected.size === 0} onClick={markSelectedRead}>Mark read</Button>
          <Button variant="ghost" size="sm" disabled={selected.size === 0}
            onClick={() => setConfirm({ kind: 'selected', ids: [...selected] })}>Delete</Button>
        </div>
      )}

      {loading ? (
        <div className="flex flex-col gap-3" aria-busy>
          {Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-16 w-full" />)}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-16 text-center">
          <BellOff className="size-8 text-muted-foreground" aria-hidden />
          <p className="font-medium">{filtered ? 'No notifications match these filters' : 'No notifications yet'}</p>
          <p className="text-sm text-muted-foreground">
            {filtered ? 'Try another tab, type or search.' : 'Bids, wins and payments will show up here.'}
          </p>
        </div>
      ) : (
        <ul className="divide-y rounded-lg border">
          {items.map((n) => (
            <li key={n.id}>
              <NotificationItem
                notification={n}
                onSelect={open}
                leading={
                  <Checkbox className="mt-1.5" checked={selected.has(n.id)} onCheckedChange={() => toggleOne(n.id)}
                    aria-label={`Select ${n.title}`} />
                }
                trailing={
                  <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" size="icon-sm" onClick={() => toggleRead(n)}
                      aria-label={n.isRead ? 'Mark as unread' : 'Mark as read'}>
                      {n.isRead ? <Mail className="size-4" /> : <MailOpen className="size-4" />}
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => setConfirm({ kind: 'one', id: n.id })}
                      aria-label="Delete notification">
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                }
              />
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <nav className="flex items-center justify-between" aria-label="Pagination">
          <Button variant="outline" size="sm" disabled={filters.page <= 1}
            onClick={() => navigate({ ...filters, page: filters.page - 1 })}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {filters.page} of {totalPages}</span>
          <Button variant="outline" size="sm" disabled={filters.page >= totalPages}
            onClick={() => navigate({ ...filters, page: filters.page + 1 })}>Next</Button>
        </nav>
      )}

      <Dialog open={confirm !== null} onOpenChange={(o) => { if (!o) setConfirm(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete notifications?</DialogTitle>
            <DialogDescription>
              {confirm?.kind === 'read' && 'All read notifications will be deleted.'}
              {confirm?.kind === 'one' && 'This notification will be deleted.'}
              {confirm?.kind === 'selected' && `${confirm.ids.length} selected notification(s) will be deleted.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>Cancel</DialogClose>
            <Button variant="destructive" onClick={confirmDelete}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default function NotificationsPage() {
  return (
    <Suspense fallback={null}>
      <NotificationsView />
    </Suspense>
  )
}
```

Adapt only where the local primitives differ. Check:
- `Button` sizes (`icon-sm`, `xs`) and the `destructive` variant in `components/ui/button.tsx`
- the `Checkbox` prop names (`checked` and `onCheckedChange`)
- whether `Tabs` takes `onValueChange(value)`
- the `Select` `onValueChange` value type
- the `Dialog` controlled props and whether `DialogFooter` already renders a close button (`showCloseButton`)
- the `font-display` class: check globals for the DM Sans utility name

Keep the behaviour identical, and list each adaptation in the report. Next 16 may require `metadata` to come from a server file. If page metadata is wanted, add a `layout.tsx` beside the page that exports `metadata = { title: 'Notifications' }` and renders `children`.

- [ ] **Step 5: Type-check, lint and build**

Run (from `frontend/`): `npx tsc --noEmit && npm run lint && npm test && npm run build`
Expected: all succeed, and `/notifications` appears in the build's route list.

- [ ] **Step 6: Commit**: skipped (the user commits).

---

### Task 7: Docs, verification and roadmap

**Files:**
- Modify: `F/CLAUDE.md`, `docs/architecture.md`, `docs/superpowers/plans/2026-09-30-notification-epic-roadmap.md`

- [ ] **Step 1: Update the docs**

In `F/CLAUDE.md`:
- In the "Real-time (WebSocket)" paragraph, replace "and, when logged in, `/user/queue/notifications` (`OUTBID`)" with ". Personal notifications use a separate connection owned by `hooks/useUserNotifications` (mounted once via `UserNotificationsBridge` in the root layout), which subscribes to `/user/queue/notifications` (`NOTIFICATION` envelopes), feeds `notificationStore`, toasts outbid/won/payment/ending-soon, and resyncs the badge on reconnect and window focus".
- In the State Management list, change the `notificationStore` line to "`notificationStore` — server-backed recent notifications (10) and unread count; live pushes via `applyPush`; optimistic read/delete with rollback".

In `docs/architecture.md`, in the Private queue bullet (around line 65), append: "The frontend consumes it from one root-level connection (`useUserNotifications`); the auction page's socket subscribes only to its auction topic. `NotificationResponse.createdAt` is ISO-8601 with the server zone's offset."

- [ ] **Step 2: Run the full verification**

Run (from `frontend/`): `npm test && npm run lint && npm run build`
Expected: all succeed.

Run (from `backend/`): `mvn -q -pl media-service -am test`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Update the roadmap**

In Story 8 of the roadmap:
- Tick tasks 8.1–8.5. Mark 8.6's manual end-to-end check "(handed to the user)".
- Replace the `package.json` bullet ("add `sockjs-client`") with "no new dependency (`@stomp/stompjs` already present; native WebSocket)".
- Replace the `lib/realtime/stompClient.ts` bullet with the root-mounted `useUserNotifications` connection.
- Add:

```
**Refinements (implemented):** no SockJS and no ref-counted STOMP singleton — the user queue has one consumer, `useUserNotifications`, mounted once in the root layout (`UserNotificationsBridge`) so it also runs on public auction pages and survives client navigation; the auction page's socket no longer subscribes to the user queue (one toast per outbid). Tab sync = reload count + recent on window focus and on every (re)connect. `NotificationResponse.createdAt` now carries the server zone's offset so "time ago" is right in any browser zone. Bulk actions on selected rows send one request per row (no bulk-by-id endpoint); "Mark all as read" / "Delete read" use the bulk endpoints. Store actions return `Promise<boolean>` and roll back on failure.
```

- [ ] **Step 4: Manual end-to-end (handed to the user; a merge gate)**

The user runs this with `docker compose up` and `npm run dev`:
1. Use two browsers, users A and B. B outbids A on an auction. A's bell badge increments live and an "outbid" toast appears once, even with the auction page open.
2. A opens the bell. The 10 most recent notifications show, with icons and "time ago". Clicking one marks it read and navigates to its auction. `Esc` closes the dropdown and focus returns to the bell.
3. In tab 1, A marks all as read. Switching to tab 2 updates its badge on focus.
4. `/notifications`:
   - The tabs, type filter and debounced search update the URL, and a reload restores them.
   - Row read/unread/delete work.
   - Bulk select, then "Mark read" or "Delete" (with confirmation), works.
   - The empty and loading states show.
5. A 375 px viewport renders the bell dropdown and the page without horizontal scroll.

- [ ] **Step 5: Commit**: skipped. The user commits `feat(notification): frontend notification center (NOTIF-108)`.
