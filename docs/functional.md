# Functional Requirements - BidNow

## Overview

BidNow is an auction-based E-commerce platform where sellers can list items for bidding and buyers can compete to purchase them. This document outlines the core functional requirements for the MVP version of the system.

---

## User Roles

Describe all roles that interact with the system.

- **Guest**: Can browse active auctions, view product details, and search for items.
- **Registered User**: Can act as both a **Seller** (create auctions) and a **Bidder** (participate in auctions). Manage their internal wallet and view transaction history.
- **Admin**: Oversees the platform, manages users, and has the authority to cancel auctions or moderate content.

---

## Core Functional Requirements

### 1. User Management & Authentication
- Users must be able to register with email and password.
- Users must be able to log in and maintain a secure session.
- Users must be able to update their profile (avatar, display name, contact info).
- System must prevent users from bidding on their own auctions.

### 2. Wallet & Financials (MVP)
- Each user has a simple internal wallet.
- Users can deposit funds into their wallet (simulated or via gateway).
- System must require a mandatory deposit from a user before they can place a bid on an item.
- System must automatically refund deposits to losing bidders once an auction concludes.
- System must handle "forfeited deposits" if a winner fails to pay within the required time.

### 3. Auction Management (Seller)
- Sellers can create auction listings with: title, description, category, images, starting price, bid increment, "Buy It Now" price (optional), and duration.
- Listings are auto-approved by default for MVP.
- Sellers can view the list of their active and historical auctions.

### 4. Bidding System (Bidder)
- Bidders can place manual bids (must be > current price + increment).
- **Auto-bidding**: Users can set a maximum bid value for the system to bid automatically.
- **Anti-Sniping**: The system must extend the auction end time if a bid is placed in the final minutes (e.g., 5-minute extension for bids in the last 2 minutes).
- **Buy It Now**: Users can instantly purchase an item at the specified price, immediately ending the auction.

### 5. Auction Lifecycle & Closing
- System must automatically close auctions when the timer expires.
- System must identify the winner (highest bidder) and notify them.
- The winner is given a specific window (e.g., 24-48 hours) to complete the full payment from their wallet.
- If payment is not made, the system marks the auction as "Failed" and deducts the deposit.

### 6. Notifications
- Real-time in-app notifications for: Outbid alerts, Auction ending soon, Auction Won/Lost.
- Email notifications for: Registration, Successful Listing, Winning an Auction, Payment Reminders.

---

## Out of Scope
- Integrated shipping/logistics tracking (MVP focus is on the auction mechanism).
- Dispute resolution center.
- Complex seller analytics/dashboards.
- Multi-currency support (standardizing on one currency for MVP).

---

## Assumptions & Notes
- Users have a reliable internet connection for real-time bidding updates (WebSockets).
- The platform uses a central server time (UTC) to synchronize all auction clocks.
- Deposit amounts are either a flat fee per auction or a percentage of the starting price.

---

## Traceability (Optional)
- Related User Stories: [To be defined]
- Related APIs: [To be defined]
- Related Database Tables: `users`, `wallets`, `auctions`, `bids`, `transactions`, `notifications`.

---

_This document should evolve together with the product backlog and user stories._
