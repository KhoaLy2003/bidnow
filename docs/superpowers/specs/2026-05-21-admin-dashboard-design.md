# Admin Dashboard — Design Spec

**Date:** 2026-05-21
**Epic:** Admin Dashboard
**Status:** Approved

---

## Overview

Replace the placeholder `/admin` page with a live metrics overview for platform administrators. The dashboard displays stat cards and trend charts across three domains: Users, Auctions, and Financials. Each domain fetches independently so a slow or failing service does not block the rest.

---

## Scope

**In scope:**
- New backend stat endpoints in `identity-service`, `auction-service`, and `wallet-service`
- API Gateway routing rules for the new endpoints
- Frontend dashboard page at `/admin` with stat cards + trend charts
- New reusable components: `StatCard`, `TrendChart`, `DashboardSection`
- New TypeScript types and `adminService` methods

**Out of scope:**
- Real-time WebSocket updates to dashboard metrics
- Export / download of metrics data
- Custom date range pickers (fixed 7d / 30d toggle only)
- Email / notification domain metrics (deferred)

---

## Backend

### Endpoints

All three endpoints are admin-only (require `ADMIN` role) and accept a `?days=7` or `?days=30` query parameter (default: `7`).

#### `GET /api/v1/admin/stats/users?days=N` → `identity-service`

```json
{
  "total": 1240,
  "active": 1100,
  "pendingVerification": 85,
  "suspendedOrBanned": 55,
  "dailyRegistrations": [
    { "date": "2026-05-14", "count": 12 }
  ]
}
```

- `total`: all users ever registered
- `active`: users with status `ACTIVE`
- `pendingVerification`: users with status `PENDING_VERIFICATION`
- `suspendedOrBanned`: users with status `SUSPENDED` or `BANNED` (combined)
- `dailyRegistrations`: one entry per day for the last N days, ordered ascending by date

#### `GET /api/v1/admin/stats/auctions?days=N` → `auction-service`

```json
{
  "total": 4300,
  "active": 210,
  "completed": 3800,
  "cancelledOrFailed": 290,
  "dailyCreated": [
    { "date": "2026-05-14", "count": 8 }
  ]
}
```

- `total`: all auction listings ever created
- `active`: auctions currently in `ACTIVE` or `PENDING` state
- `completed`: auctions that ended with a winner
- `cancelledOrFailed`: auctions cancelled by seller or closed with no winner / forfeited payment
- `dailyCreated`: auction listings created per day for the last N days

#### `GET /api/v1/admin/stats/financials?days=N` → `wallet-service`

```json
{
  "totalPlatformBalance": 125000.00,
  "depositsThisPeriod": 18500.00,
  "forfeitedDeposits": 320.00,
  "dailyDepositVolume": [
    { "date": "2026-05-14", "amount": 2400.00 }
  ]
}
```

- `totalPlatformBalance`: sum of all user wallet balances (snapshot)
- `depositsThisPeriod`: total deposit amount within the selected N-day window
- `forfeitedDeposits`: total forfeited deposit amount within the selected N-day window
- `dailyDepositVolume`: deposit amount summed per day for the last N days

### API Gateway

Add three new route rules forwarding:
- `/api/v1/admin/stats/users/**` → `identity-service`
- `/api/v1/admin/stats/auctions/**` → `auction-service`
- `/api/v1/admin/stats/financials/**` → `wallet-service`

All three routes require admin role validation at the gateway level (consistent with existing `/api/v1/admin/**` rules).

---

## Frontend

### Page: `app/admin/page.tsx`

Replaces the current placeholder. Page-level state:
- `days: 7 | 30` — shared time range toggle, drives refetch of all three sections

The page renders a header row (title + 7d/30d toggle) followed by three `DashboardSection` components. Each section manages its own fetch, loading, and error state independently.

**Data fetching:** Three parallel calls on mount and on `days` change:
```
adminService.getUserStats(days)
adminService.getAuctionStats(days)
adminService.getFinancialStats(days)
```

### New Components: `components/admin/`

#### `StatCard`

A shadcn `Card` displaying a single metric.

Props:
```ts
interface StatCardProps {
  label: string
  value: string | number
  icon: LucideIcon
}
```

- Value rendered in `font-mono` per design system
- Icon rendered at `size-4` in `text-muted-foreground`
- Used in a `grid grid-cols-2 lg:grid-cols-4` grid

#### `TrendChart`

A thin Recharts wrapper for bar or area charts.

Props:
```ts
interface TrendChartProps {
  data: Array<{ date: string; value: number }>
  type: 'bar' | 'area'
  valueLabel: string
}
```

- Uses CSS variable colors from `globals.css` for stroke/fill (no hardcoded hex)
- `ResponsiveContainer` fills the card width at a fixed height of `180px`
- Tooltip shows formatted date and value
- X-axis shows abbreviated date labels

#### `DashboardSection`

Composes one domain's stat cards + chart into a titled card with independent loading and error states.

Props:
```ts
interface DashboardSectionProps {
  title: string
  stats: StatCardProps[]
  chartData: Array<{ date: string; value: number }>
  chartType: 'bar' | 'area'
  chartValueLabel: string
  isLoading: boolean
  error: string | null
}
```

- Loading state: skeleton placeholders for stat cards and chart area
- Error state: inline error message with a retry button
- No error in one section does not affect others

### New Types: `types/admin.ts`

```ts
interface DailyCount {
  date: string
  count: number
}

interface DailyAmount {
  date: string
  amount: number
}

interface UserStatsResponse {
  total: number
  active: number
  pendingVerification: number
  suspendedOrBanned: number
  dailyRegistrations: DailyCount[]
}

interface AuctionStatsResponse {
  total: number
  active: number
  completed: number
  cancelledOrFailed: number
  dailyCreated: DailyCount[]
}

interface FinancialStatsResponse {
  totalPlatformBalance: number
  depositsThisPeriod: number
  forfeitedDeposits: number
  dailyDepositVolume: DailyAmount[]
}
```

### New `adminService` Methods

Three new methods added to `services/adminService.ts`:

```ts
getUserStats(accessToken: string, days: 7 | 30): Promise<UserStatsResponse>
getAuctionStats(accessToken: string, days: 7 | 30): Promise<AuctionStatsResponse>
getFinancialStats(accessToken: string, days: 7 | 30): Promise<FinancialStatsResponse>
```

### Dependencies

- Install `recharts` via `npm install recharts`

---

## Error Handling

- Each `DashboardSection` catches its own fetch error and renders an inline error card with a "Retry" button that re-fires only that section's fetch.
- Monetary values (wallet balance, deposits) are formatted with the existing `formatCurrency` utility or a new one if it doesn't exist yet.

---

## Testing Considerations

- Backend: unit tests for each stat query (user count by status, daily bucketing logic)
- Frontend: the three service methods are the only external dependency — mock them in component tests to verify loading/error/success states render correctly

---

## Open Questions

None — all design decisions resolved.
