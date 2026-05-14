# Meeting 3: Backend Auth Code Review & Epic Identity Refinement

## Basic Information

- **Date:** 2026-05-10
- **Time:** 09:00 PM - 10:00 PM
- **Meeting Type:** Code Review & Technical Sync
- **Participants:**
  - Backend Team
  - Product Owner
- **Host:** Khoa Ly

---

## Agenda

1. **Backend Code Review:** Review implementation of `identity-service` and `user-service`.
2. **Logic Walk-through:** Detailed step-by-step review of the Registration and Login flows.
3. **Security Check:** Validate JWT structure, filter chains, and password encryption.

---

## Discussion

### 1. Identity & User Service Code Review
- **Code Structure:** The team reviewed the `UserProfileServiceImpl` and core controllers. The separation between `identity-service` (auth) and `user-service` (profile data) was verified.
- **Registration Flow:** Discussed the transactional consistency when creating a user in both the auth database and the profile database.
- **Login Flow:** Verified the authentication provider configuration and JWT generation logic.

### 2. Authentication Logic
- **JWT Implementation:** Currently using a single access token. The team discussed the future need for refresh tokens.
- **Validation:** Gateway-level validation is working as expected, properly extracting user claims for downstream services.

---

## Decisions

- **Password Hashing:** Standardize on BCrypt with a strength of 10.
- **Token Expiry:** Access tokens will have a 24-hour TTL for the MVP phase to simplify testing.
- **Error Handling:** Standardized error response DTOs across both Identity and User services.

---

## Action Items

| Task                                       | Owner | Due Date | Status |
| ------------------------------------------ | ----- | -------- | ------ |
| Refactor `UserProfileServiceImpl` lints    | @backend | 2026-05-13 | TODO   |
| Finalize DB migrations for User module     | @backend | 2026-05-14 | TODO   |
| Set up basic Auth forms in Frontend        | @frontend| 2026-05-15 | TODO   |

---

## References

- **Identity Service Code:** `backend/identity-service/`
- **User Service Code:** `backend/user-service/`
- **Epic: User Management:** [docs/epics/user-management/](../epics/user-management/)

---

## Notes

- The backend auth flow is now stable enough for frontend integration.
- Next meeting will pivot to the Auction and Wallet services.
