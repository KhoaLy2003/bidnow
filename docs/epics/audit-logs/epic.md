# Epic 4: Differential Audit Logging System

**Epic ID:** `EPIC-004`  
**Epic Title:** Differential Audit Logging & Admin/User History  
**GitHub Issue:** [#52](https://github.com/KhoaLy2003/bidnow/issues/52)  
**Priority:** P1 (High - Compliance & Transparency)  
**Target Version:** MVP v1.0

---

## Epic Overview

This epic establishes a centralized, event-driven, and differential audit logging system for the BidNow platform. Unlike traditional logging that captures full snapshots, this system focuses on **Differential Logging**—storing only the fields that changed in a structured JSONB `delta`. This approach minimizes storage overhead, enhances data privacy (PII protection), and provides a clear audit trail for both internal compliance and user transparency.

The Audit Service acts as the **source of truth for behavioral history**, capturing:
- **Who** performed the action (User, Admin, or System).
- **What** exactly changed (Differential field-level delta).
- **When** and **How** (Timestamp, IP address, User-Agent).
- **Correlation** across microservices (Linking related events via `correlation_id`).

---

## Business Value

### For Compliance & Legal
- **Tamper-Proof Audit Trail:** Provides evidence for regulatory requirements and internal audits.
- **Dispute Resolution:** "Who cancelled my auction?" or "Why was my bid rejected?" can be answered with absolute certainty.
- **Immutability:** Ensures that audit records cannot be altered or deleted, maintaining the integrity of the history.

### For Operations & Security
- **Forensic Analysis:** Enables root cause analysis for system anomalies or suspicious activities.
- **Admin Accountability:** Tracks all administrative actions to prevent abuse of power.
- **PII Protection:** Proactive masking of sensitive data (passwords, tokens) before storage reduces data breach risks.

### For End-Users
- **Trust & Transparency:** Users can view their own "Activity History" to track significant changes to their account, auctions, or wallet.
- **Security Awareness:** Notifies users of critical changes (e.g., "Your password was changed from IP X"), similar to modern banking apps.

---

## Success Metrics

| Metric | Target | Notes |
| :--- | :--- | :--- |
| **Coverage** | 100% | All critical entity state changes (User, Auction, Bid, Wallet) captured. |
| **Performance Overhead** | <20ms | Minimal latency added to business transactions for diff calculation. |
| **Storage Efficiency** | >70% | Reduction in database growth compared to full-snapshot logging. |
| **Query Performance** | <500ms | Admin can find specific audit records even with 1M+ rows via partitioning. |
| **Data Loss Rate** | 0% | Guaranteed eventual consistency via Message Broker retries and DLQ. |
| **PII Compliance** | 100% | Zero instances of unmasked sensitive data in the audit database. |

---

## Epic Scope

### In Scope ✅

**Services & Entities:**
- **Identity Service:** User registration, logins, role changes, status transitions.
- **Auction Service:** Creation, status changes (ACTIVE→COMPLETED), admin cancellations.
- **Bidding Service:** Bid placement (manual/auto), bid retractions.
- **Wallet Service:** Deposits, locks, refunds, forfeitures.

**Core Features:**
- **Differential Storage:** JSONB `delta` format: `{ "field": { "old": "x", "new": "y" } }`.
- **System Actor:** Reserved UUID `00000000-0000-0000-0000-000000000000` for automated cron jobs.
- **PII Masking:** Proactive redaction of fields like `passwordHash`, `otpCode`, `paymentToken`.
- **Correlation Tracking:** Propagating `correlation_id` across service boundaries.
- **Partitioning:** Monthly PostgreSQL table partitioning for long-term scalability.

**Interfaces:**
- **Admin Dashboard:** Search/Filter/Timeline view with a syntax-highlighted diff viewer.
- **User History API:** Dedicated endpoint for users to view their own activity.

### Out of Scope ❌
- **Real-time Alerting:** Suspicious activity alerts (deferred to post-MVP).
- **Compliance Exports:** CSV/PDF export functionality (manual SQL for MVP).
- **Service-to-Service Internal Auditing:** Focused only on client-to-server entry points for MVP.
- **Meta-Auditing:** Logging access to the audit logs themselves.

---

## Technical Architecture

### High-Level Design

```
┌─────────────────┐       ┌──────────────────┐       ┌─────────────────┐
│ Client Request  │ ────> │ Business Service │ ────> │ Message Broker  │
└─────────────────┘       │ (Auction/Wallet) │       │ (RabbitMQ/Kafka)│
                          └────────┬─────────┘       └────────┬────────┘
                                   │                          │
                                   ▼                          ▼
                          ┌──────────────────┐       ┌─────────────────┐
                          │ Diff Calculation │       │  Audit Service  │
                          │ & PII Masking    │       │ (Event Consumer)│
                          └──────────────────┘       └────────┬────────┘
                                                              │
                                                              ▼
                                                     ┌─────────────────┐
                                                     │ Audit Database  │
                                                     │ (PostgreSQL)    │
                                                     └─────────────────┘
```

### Event-Driven Flow
1. **Service** executes business logic in a transaction.
2. **Service** calculates `delta` between old and new state.
3. **Service** emits `AuditLogEvent` *after* transaction commit.
4. **Audit Service** consumes event and persists it to a partitioned table.

---

## Data Model

### `audit_logs` (Partitioned by `created_at`)
- `id`: UUID (PK)
- `correlation_id`: UUID (Index)
- `service_name`: String
- `entity_type`: String
- `entity_id`: UUID (Index)
- `action`: String (CREATE, UPDATE, DELETE, etc.)
- `actor_id`: UUID (Index)
- `actor_type`: Enum (USER, ADMIN, SYSTEM)
- `delta`: JSONB (Format: `{ "field": { "old": "v1", "new": "v2" } }`)
- `reason`: Text (Optional)
- `ip_address`: String
- `user_agent`: Text
- `created_at`: Timestamp (Partition Key)

---

## Issues Breakdown

### **Issue 3.1: Audit Service Infrastructure**
- [ ] Create `audit-service` Spring Boot module.
- [ ] Implement partitioned PostgreSQL schema.
- [ ] Set up RabbitMQ/Kafka consumer logic.

### **Issue 3.2: Differential Logic Utility**
- [ ] Create shared utility for field-level diff calculation using reflection.
- [ ] Implement PII masking rules within the utility.

### **Issue 3.3: Identity & Auction Service Integration**
- [ ] Integrate diff logic into User and Auction services.
- [ ] Implement `@TransactionalEventListener` for event publishing.
- [ ] Propagate `correlation_id` via MDC.

### **Issue 3.4: Admin Dashboard - Audit UI**
- [ ] Build Admin UI for searching/filtering logs.
- [ ] Implement "Diff Viewer" component with JSON highlighting.

### **Issue 3.5: User "My Activity" View**
- [ ] Create user-facing API and frontend page for personal audit history.

---

## Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| **Performance Lag** | Medium | Low | Use async event emission; optimize diff logic for large objects. |
| **Data Growth** | High | Medium | Implement monthly partitioning and 1-year retention policy. |
| **PII Leakage** | Low | Critical | Automated unit tests for masking; strict code reviews on new entities. |
| **Missing Events** | Low | High | Use Message Broker persistence and DLQ for failed writes. |

---

## Document History

| Version | Date | Author | Changes |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-05-18 | Khoa | Initial epic definition for Differential Audit Logging |

---

**Status:** 🟢 **Active**  
**Document Owner:** Backend Team  
**Last Updated:** 2026-05-18
