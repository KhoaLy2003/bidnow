# Business Clarifications & Decisions

This document tracks the key business decisions and clarifications made during the initial phase of the BidNow project.

## 1. Auction Mechanism
- **Model:** English Auction (Ascending price).
- **Winner:** The highest bidder at the end of the auction time wins the product.
- **Buy It Now:** Supported. Users can purchase the item immediately at a fixed price, terminating the auction.

## 2. Bidding Features
- **Minimum bid:** The first bid may equal the starting price; each later bid must be at least the current price plus the bid increment.
- **Bid Extension (Anti-Sniping):** An accepted bid placed with strictly less than 120 seconds left extends the end time by 300 seconds (from the current end time, not from the bid time), with no limit on repeated extensions. The window and extension are configurable (`auction.anti-snipe.window-seconds` / `extension-seconds`).
- **Auto-bidding:** Users can set a maximum bid, and the system will automatically outbid others on their behalf up to that limit.

## 3. Financials & Wallet
- **Wallet:** Simple internal wallet system for users.
- **Deposit Mechanism:** Mandatory deposit is required to participate in an auction. It is locked automatically on the bidder's first bid (implicit registration); if that first bid is then rejected (e.g. outbid in the same instant), the deposit stays locked as the registration and is refunded at auction end.
- **Refund Policy:** Deposits are returned to the wallets of losing bidders immediately after the auction ends.
- **Winning Flow:**
    - The winner has a designated timeframe to complete the full payment.
    - If the winner fails to pay within the timeframe, their deposit is forfeited, and the auction result is cancelled.

## 4. User Roles & Moderation
- **Account Type:** A single account can function as both a Seller and a Bidder.
- **Restrictions:** Users are strictly prohibited from bidding on their own auction listings.
- **Moderation (MVP):** 
    - Auto-approval for new auction listings.
    - Admin users retain the right to manually cancel any auction that violates terms.

## 5. Notifications
- **Channels (MVP):**
    - Real-time Web/In-app notifications.
    - Email notifications for critical updates (winning, payment reminders, payment results).
- **Decisions:**
    1. The outbid/new-bid batch window is 5 minutes. The first alert is immediate, and later ones are summed.
    2. The first bid on an auction is sent immediately; later seller alerts are batched.
    3. The winner gets one email, at payment-required. The auction-ended event is in-app only.
    4. Language and email opt-out come from user-service preferences, and payment emails ignore the opt-out.
    5. The ending-soon thresholds are a platform default (60 and 15 min), with no per-seller setting.
    6. Emails are sent once. A failure is logged with no retry, and the in-app notification is still created.

## 6. User Registration & Verification
- **Verification Method:** Email OTP (One-Time Password).
- **OTP Details:**
    - Length: 6 digits.
    - Expiry: 5-10 minutes.
    - Rate Limiting: Maximum 3-5 attempts for verification before requiring a new OTP request.
- **Account Status:**
    - `PENDING_VERIFICATION`: Initial status after registration form submission.
    - `ACTIVE`: Status after successful OTP verification.
    - Only `ACTIVE` accounts can log in and participate in auctions.

---
*Last updated: 2026-04-18*
