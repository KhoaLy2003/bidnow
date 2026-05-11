# Meeting 4: Epic Auction Deep Dive & Frontend Auth Completion

## Basic Information

- **Date:** 2026-05-18
- **Time:** 09:00 PM - 10:30 PM
- **Meeting Type:** Feature Grooming & Frontend Sync
- **Participants:**
  - Backend Team
  - Frontend Team
  - Product Owner
- **Host:** Khoa Ly

---

## Agenda

1. **Epic 2: Auction Management & Discovery:** Deep dive into requirements and API design.
2. **Frontend Auth Completion:** Finalizing the UI/UX for login, registration, and session persistence.
3. **Technical Integration:** Event flows and real-time updates for the Auction service.

---

## Discussion

### 1. Epic 2: Auction Management & Discovery
- **Auction Lifecycle:**
  - Create, Update, Delete (before start).
  - Automated start/end transitions.
  - **Anti-sniping mechanism:** 5-minute extension for bids placed in the final minutes.
- **Discovery Features:**
  - Public listing (no auth required for browsing).
  - Advanced search and category filtering.

### 2. Frontend: Completing Auth Flow
- **UI Implementation:** Finalizing forms for Login and Registration.
- **Session Management:** Using Zustand or React Context for global auth state.
- **Route Guarding:** Protecting private routes (e.g., /profile, /create-auction).

---

## Decisions

_(To be filled during/after the meeting)_

- [ ] Choice of real-time update technology (Socket.io vs Native WebSockets).

---

## Action Items

| Task                                       | Owner | Due Date | Status |
| ------------------------------------------ | ----- | -------- | ------ |
| Complete Frontend Auth Integration         |       |          | TODO   |
| Draft API Specs for Auction Service        |       |          | TODO   |
| Prepare Auction detail UI mockups          |       |          | TODO   |

---

## References

- **Epic: Auction Service:** [docs/epics/auction/epic-1.md](../epics/auction/epic-1.md)
- **Frontend Repository:** `frontend/`

---

## Notes

- This week marks the transition from infrastructure/auth to the core "Auction" business logic.
- Real-time performance is a critical factor for the Auction detail page.
