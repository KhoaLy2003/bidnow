# Purchase history (design)

## Goal
A buyer sees every auction they won, by bidding or Buy It Now, with its payment status. The pending-payment UI moves out of the wallet into this page.

## Decisions
- **Statuses:** Pending payment, Paid, Forfeited (won but unpaid past the deadline). Bid win versus Buy It Now is not distinguished (not persisted).
- **Data source:** auction-service only. A won auction is one `auction_items` row; status is derived from `status` and `winner_paid_at` (see `PurchasePaymentStatus`). No cross-service call, no new column; one composite index (migration `005-purchases-index.sql`).
- **Endpoint:** `GET /api/v1/auctions/me/purchases?status=&page=&size=`, newest first, `PurchaseResponse { auctionId, title, primaryImageUrl, finalPrice, paymentStatus, completedAt, paymentDeadline, paidAt }`. Unknown `status` is a 400.
- **Pay action:** unchanged. Pending rows join the wallet's pending holds (`usePendingPayments`) by auction id for the exact remainder and open the existing `PayNowDialog`. After a payment the row shows Paid at once (`paidIds`) and the list refetches.
- **Notifications:** payment-related links (required, reminder, completed, failed) point to `/purchases`; deposit refund and cancellation links stay on `/wallet`.
- **Wallet page:** the "Payments due" list is removed; a line links to Purchase history.

## Known limits
- Eventually consistent: `winner_paid_at` is set when auction-service consumes the wallet's payment event, so a just-paid row can briefly read Pending.
- A payment event lost to the dead-letter topic can leave a forfeit reading Pending (pre-existing).
- A pending row whose wallet hold has not loaded yet shows "View" instead of "Pay".
- No per-status counts on the filter pills (the endpoint returns the current page's total only).

## Prototype
Claude Design canvas "BidNow Purchase History": desktop, mobile, and empty / error / loading states.

## Tests
Backend: `PurchasePaymentStatusTest`, `AuctionMapperTest`, `AuctionServiceImplTest`, `AuctionControllerTest`; media-service link tests. Frontend: `purchase-utils`, `purchase.mapper`, `purchase.service` (Vitest, pure modules).
