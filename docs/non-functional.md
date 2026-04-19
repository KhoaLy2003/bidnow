# Non-Functional Requirements - BidNow

## Overview

This document defines the quality attributes and constraints for the BidNow auction system. These requirements ensure the system is scalable, secure, and provides a seamless real-time bidding experience.

---

## Performance
- **Real-time Latency:** Bid updates must be broadcasted to all active observers via WebSockets within **200ms**.
- **Concurrent Users:** System should support at least **1,000** concurrent users per auction without degradation.
- **API Response:** Standard HTTP API responses should complete within **300ms** on average.
- **Auction Closing:** The system must process auction completions and winner selection within **1 second** of the scheduled end time.

---

## Security
- **Authentication:** All protected endpoints must require JWT-based authentication.
- **Data Protection:** Financial transactions and wallet balances must be encrypted and processed within atomic database transactions.
- **Integrity:** Input validation must strictly prevent SQL Injection, XSS, and "Over-bidding" (bidding more than wallet balance or bidding on own items).
- **Audit Trail:** Every bid and wallet transaction must be logged with an immutable audit trail.

---

## Reliability & Availability
- **Uptime:** System uptime target is **99.9%** (High Availability).
- **Concurrency Control:** Use optimistic or pessimistic locking to prevent race conditions during high-frequency bidding.
- **Data Consistency:** Transactions must strictly follow ACID properties, especially for the "Refund deposit" and "Payment" flows.
- **Failure Recovery:** System must be able to restore the state of active auctions after a process restart without losing any bids.

---

## Scalability
- **Horizontal Scaling:** The bidding engine should be capable of scaling horizontally (using Redis Pub/Sub or similar for cross-node WebSockets).
- **Database Scaling:** Read-heavy operations (browsing auctions) should be optimized with read-replicas or caching.
- **Stateless Services:** API services must be stateless to support easy containerization and scaling.

---

## Maintainability
- **Clean Architecture:** The codebase must separate Business Logic from Infrastructure/Delivery layers.
- **Automated Testing:** Unit test coverage should be at least **80%** for core bidding and wallet logic.
- **API Documentation:** OpenAPI (Swagger) documentation must be automatically generated and kept up-to-date.

---

## Usability & Accessibility
- **Responsive UI:** The web interface must be mobile-first and fully responsive across devices.
- **Visual Feedback:** Bidders must receive immediate visual cues (animations, color changes) when outbid or when time is critical.
- **Error Transparency:** Users must receive clear reasons if a bid is rejected (e.g., "Amount too low", "Insufficient funds").

---

## Logging & Auditing
- **Centralized Logs:** Logs must be aggregated (e.g., ELK stack or CloudWatch) for monitoring.
- **Sensitive Data:** Personal Identification Information (PII) and wallet secrets must never be logged in plain text.

---

## Data Integrity
- **Unique Constraints:** Prevent multiple bids from the same user at the exact same millisecond if needed.
- **Foreign Keys:** Ensure referential integrity between Users, Auctions, and Bids.
- **Soft Deletes:** Auction listings should be soft-deleted to maintain historical data for audits.

---

## Monitoring & Observability
- **Health Checks:** `/health` endpoints must be available for orchestration tools.
- **Alerting:** Real-time alerts for critical failures (e.g., wallet processing errors or high latency in bid broadcasting).

---

## Non-Goals (MVP)
- Support for multiple fiat currencies (standardizing on USD/VND for now).
- Low-latency edge computing for global bidding (initial deployment to a single region).

---

## Notes
- Performance is critical for the bidding experience; any delay in updating the "Current Bid" can lead to user frustration.

---

_Non-functional requirements should directly influence architectural and technical decisions._
