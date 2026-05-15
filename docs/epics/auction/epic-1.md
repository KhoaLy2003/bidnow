# Epic 1: Auction Management & Lifecycle

**Epic ID:** `EPIC-001`
**Epic Title:** Auction Management & Lifecycle
**Priority:** P0 (Critical - Core Platform Feature)
**Target Version:** MVP v1.0

---

## Epic Overview

This epic encompasses all functionality related to creating, managing, and operating auctions throughout their entire lifecycle. It includes seller capabilities for auction creation and management, automated system processes for auction closure and winner determination, and admin moderation tools.

---

## Business Value

- **For Sellers:** Enables users to list items for auction and monitor their sales
- **For Bidders:** Ensures fair and transparent auction closure with automatic winner selection
- **For Platform:** Establishes the core marketplace mechanism that generates transaction volume
- **For Admins:** Provides necessary tools for platform moderation and trust & safety

---

## Success Metrics

- **Adoption:** 100+ auctions created within first month of launch
- **Completion Rate:** >80% of auctions complete successfully (with bids and payment)
- **Seller Satisfaction:** Sellers can create auctions in <3 minutes
- **System Reliability:** 99.9% of auctions close automatically within 1 second of scheduled end time
- **Moderation Efficiency:** Admins can cancel fraudulent auctions within 2 minutes

---

## Epic Scope

### In Scope

✅ Auction creation with full metadata (title, description, images, pricing, duration)
✅ Seller capability to update or delete auction BEFORE it starts
✅ Seller dashboard to view active and historical auctions
✅ Automated auction closure triggered by scheduler
✅ Winner determination and notification event emission
✅ User registration for participation with mandatory deposit lock
✅ Handling auctions with no bids (mark as FAILED)
✅ Admin auction cancellation with refund triggering
✅ Audit logging for all state transitions
✅ Anti-sniping time extension integration

### Out of Scope

❌ Direct bidding functionality (handled by Bidding Service)
❌ Payment processing (handled by Wallet Service)
❌ User authentication (handled by Identity Service)
❌ Real-time WebSocket notifications (handled by Notification Service)
❌ Advanced analytics/reporting dashboards

---

## Technical Architecture

### Services Involved

- **Primary:** Auction Service
- **Dependencies:**
  - Identity Service (user validation)
  - Wallet Service (deposit validation)
  - Bidding Service (current bid synchronization)
  - Notification Service (event consumers)

### Key Components

1. **Auction CRUD API:** RESTful endpoints for auction operations
2. **Scheduled Job:** Background worker to close expired auctions
3. **Event Publisher:** Emits `AUCTION_ENDED`, `AUCTION_CANCELLED` events
4. **Event Listener:** Consumes `BID_PLACED` events for anti-sniping

### Data Flow

```
Seller → API Gateway → Auction Service → PostgreSQL
                    ↓
                Message Broker → Notification Service
                    ↑
Bidding Service ────┘ (BID_PLACED event)
```

---

## Database Schema Reference

See [Database Schema Document](./database-schema.md) for complete table definitions:

- `auction_items`
- `auction_categories`
- `auction_images`
- `auction_status_history`
- `auction_extensions`
