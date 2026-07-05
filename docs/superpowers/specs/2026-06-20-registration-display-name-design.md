# Registration Display Name Design

**Date:** 2026-06-20
**Issue:** [#98 — Add display name field to registration form](https://github.com/KhoaLy2003/bidnow/issues/98)
**Milestone:** v0.1-foundation

---

## Problem

The registration form collects only `email` and `password`. The `user_profiles.display_name` column already exists and is `VARCHAR(100)`, but it is never populated at account creation because `CreateUserProfileRequest` only carries `userId` and `email`. Every new user starts with a null display name.

---

## Goals

- Collect a display name at registration (one additional field, no extra step).
- Persist it to `user_profiles.display_name` when the account is activated at OTP verification.
- Surface it in `RegisterResponse` so the frontend can confirm the value was stored.

---

## Out of Scope

- Updating existing users' display names (that is a profile-edit feature).
- Showing the display name anywhere in the UI beyond what already exists (downstream consumers read from `user_profiles` via the user-service API, unchanged).

---

## Architecture

The name travels through three hops in dependency order:

```
Register form (name input)
  → POST /api/v1/auth/register  { name, email, password }
    → identity-service: store displayName on identity_users row
      → POST /internal/users/profile  { userId, email, displayName }
        → user-service: write displayName to user_profiles row
```

The `register → verifyOtp` gap (the user enters their name at step 1 but the profile isn't created until step 2) is bridged by persisting `display_name` on `identity_users`. A single Liquibase migration adds the column; no migration is needed for user-service because `user_profiles.display_name VARCHAR(100)` already exists.

---

## Design

### 1. identity-service — Liquibase migration

New file: `backend/identity-service/src/main/resources/db/changelog/migrations/04-add-display-name-to-identity-users.sql`

```sql
ALTER TABLE identity_users ADD COLUMN display_name VARCHAR(100);
```

Include it in `db.changelog-master.xml`:
```xml
<include file="/db/changelog/migrations/04-add-display-name-to-identity-users.sql"/>
```

Column is nullable — the seeded admin row and any existing rows get `NULL`, which is correct.

---

### 2. identity-service — `User` entity

File: `backend/identity-service/src/main/java/com/bidnow/identity/domain/entity/User.java`

Add after the existing `email` field:
```java
@Column(name = "display_name", length = 100)
private String displayName;
```

---

### 3. identity-service — `RegisterRequest`

File: `backend/identity-service/src/main/java/com/bidnow/identity/dto/request/RegisterRequest.java`

Add as the first field (before `email`):
```java
@NotBlank
@Size(max = 100, message = "Name must be at most 100 characters")
@Schema(description = "Display name", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
private String name;
```

---

### 4. identity-service — `RegisterResponse`

File: `backend/identity-service/src/main/java/com/bidnow/identity/dto/response/RegisterResponse.java`

Add field:
```java
@Schema(description = "Display name stored for the user", example = "John Doe")
private String displayName;
```

---

### 5. identity-service — `AuthServiceImpl`

File: `backend/identity-service/src/main/java/com/bidnow/identity/service/impl/AuthServiceImpl.java`

**In `register()`** — add `displayName` to the `User.builder()` call:
```java
User user = User.builder()
    .email(request.getEmail())
    .passwordHash(passwordEncoder.encode(request.getPassword()))
    .displayName(request.getName())
    .isEmailVerified(false)
    .isActive(false)
    .accountStatus(AccountStatus.PENDING_VERIFICATION)
    .verificationOtp(otp)
    .otpExpiresAt(otpExpiresAt)
    .otpFailedAttempts(0)
    .failedLoginAttempts(0)
    .build();
```

**In `verifyOtp()`** — extend the `CreateUserProfileRequest` builder call:
```java
userServiceClient.createUserProfile(CreateUserProfileRequest.builder()
    .userId(user.getId())
    .email(user.getEmail())
    .displayName(user.getDisplayName())
    .build());
```

---

### 6. identity-service — `UserMapper`

File: `backend/identity-service/src/main/java/com/bidnow/identity/mapper/UserMapper.java`

In `toRegisterResponse()`, add:
```java
return RegisterResponse.builder()
    .userId(user.getId())
    .email(user.getEmail())
    .displayName(user.getDisplayName())   // ← new
    .accountStatus(user.getAccountStatus().name())
    .isEmailVerified(user.getIsEmailVerified())
    .isActive(user.getIsActive())
    .createdAt(user.getCreatedAt())
    .build();
```

---

### 7. common module — `CreateUserProfileRequest`

File: `backend/common/src/main/java/com/bidnow/common/dto/request/CreateUserProfileRequest.java`

Add optional `displayName` field (no `@NotBlank` — the field is optional to keep existing callers unaffected):
```java
@Size(max = 100)
@Schema(description = "Optional display name for the new profile", example = "John Doe")
private String displayName;
```

---

### 8. user-service — `UserProfileServiceImpl`

File: `backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java`

In `createUserProfile()`, extend the `UserProfile.builder()` call:
```java
UserProfile profile = UserProfile.builder()
    .userId(request.getUserId())
    .displayName(request.getDisplayName())
    .build();
```

`displayName` may be `null` for users created before this change — that is acceptable; the column is nullable.

---

### 9. Frontend — `types/api/auth.api.ts`

Add `displayName` to `RegisterResponse`:
```ts
export interface RegisterResponse {
  userId: string;
  email: string;
  accountStatus: string;
  isEmailVerified: boolean;
  isActive: boolean;
  createdAt: string;
  displayName: string;
}
```

---

### 10. Frontend — `services/auth.service.ts`

Add `name` as first parameter and include it in the request body:
```ts
async register(
  name: string,
  email: string,
  password: string,
): Promise<ApiResponse<RegisterResponse>> {
  const response = await fetch(`${API_URL}/api/v1/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, email, password }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw error;
  }

  return response.json();
},
```

---

### 11. Frontend — `app/(auth)/register/page.tsx`

Extract `name` from form data and pass it to the service:
```ts
const name = formData.get('name') as string
await authService.register(name, email, password)
```

Add the name `<Input>` **above** the email field:
```tsx
<div className="flex flex-col gap-1.5">
  <Label htmlFor="name">Name</Label>
  <Input
    id="name"
    name="name"
    type="text"
    placeholder="John Doe"
    required
    maxLength={100}
  />
</div>
```

`maxLength={100}` mirrors the backend `@Size(max = 100)` validation.

---

## Acceptance Criteria

- [ ] Registration form has a `name` field (required, positioned above the email field, max 100 chars)
- [ ] `authService.register()` sends `{ name, email, password }` to the backend
- [ ] `identity_users.display_name` column exists (Liquibase migration `04`)
- [ ] `RegisterResponse` includes `displayName`
- [ ] `user_profiles.display_name` is populated for new accounts after OTP verification
- [ ] `name` validation: required, max 100 characters (enforced on both backend and frontend)
- [ ] Existing email + password flow is unaffected (existing rows get `NULL` display name, which is valid)
- [ ] Seeded admin account is unaffected (nullable column, no backfill needed)
