# Business Clarifications & Decisions

This document tracks the key business decisions and clarifications made during the initial phase of the BidNow project.

## 1. Auction Mechanism
- **Model:** English Auction (Ascending price).
- **Winner:** The highest bidder at the end of the auction time wins the product.
- **Buy It Now:** Supported. Users can purchase the item immediately at a fixed price, terminating the auction.

## 2. Bidding Features
- **Bid Extension (Anti-Sniping):** If a bid is placed within the final minutes of an auction, the end time will be automatically extended to allow others to respond.
- **Auto-bidding:** Users can set a maximum bid, and the system will automatically outbid others on their behalf up to that limit.

## 3. Financials & Wallet
- **Wallet:** Simple internal wallet system for users.
- **Deposit Mechanism:** Mandatory deposit is required to participate in an auction.
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
    - Email notifications for critical updates (outbid, winning, payment reminders).

---
*Last updated: 2026-04-18*
