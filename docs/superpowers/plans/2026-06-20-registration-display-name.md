# Registration Display Name Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect a display name at registration, persist it to `user_profiles.display_name` at OTP verification, and surface it in `RegisterResponse`.

**Architecture:** `name` is added to `RegisterRequest` and stored in `identity_users.display_name` (new column, Liquibase migration). At OTP verification the value is forwarded to user-service via the existing synchronous Feign call (`CreateUserProfileRequest`), which writes it to the already-existing `user_profiles.display_name` column. The frontend adds one input above the email field and passes `name` as the first argument to `authService.register()`.

**Tech Stack:** Spring Boot 3.2.4, Maven multi-module, Lombok, Jakarta Validation, Liquibase, JUnit 5 + Mockito + AssertJ (backend); Next.js 16.2, TypeScript strict, shadcn/ui `<Input>` / `<Label>` (frontend).

## Global Constraints

- All backend commands run from `backend/` directory unless otherwise noted.
- Frontend commands run from `frontend/` directory.
- `display_name` column is nullable — existing rows (seeded admin, pre-migration accounts) get `NULL`.
- `displayName` in `CreateUserProfileRequest` has no `@NotBlank` — it is optional to keep existing callers unaffected.
- `@Size(max = 100)` on `RegisterRequest.name` matches `VARCHAR(100)` on both `identity_users.display_name` and `user_profiles.display_name`.
- No new Maven dependencies. No new npm packages.
- Spec: `docs/superpowers/specs/2026-06-20-registration-display-name-design.md`

---

## File Map

| File | Change |
|---|---|
| `backend/common/src/main/java/com/bidnow/common/dto/request/CreateUserProfileRequest.java` | Add optional `displayName` field |
| `backend/identity-service/src/main/resources/db/changelog/migrations/04-add-display-name-to-identity-users.sql` | New Liquibase migration |
| `backend/identity-service/src/main/resources/db/changelog/db.changelog-master.xml` | Include new migration |
| `backend/identity-service/src/main/java/com/bidnow/identity/domain/entity/User.java` | Add `displayName` field |
| `backend/identity-service/src/main/java/com/bidnow/identity/dto/request/RegisterRequest.java` | Add `name` field with validation |
| `backend/identity-service/src/main/java/com/bidnow/identity/dto/response/RegisterResponse.java` | Add `displayName` field |
| `backend/identity-service/src/main/java/com/bidnow/identity/mapper/UserMapper.java` | Map `displayName` in `toRegisterResponse()` |
| `backend/identity-service/src/main/java/com/bidnow/identity/service/impl/AuthServiceImpl.java` | Store in `register()`; forward in `verifyOtp()` |
| `backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java` | Set `displayName` in `createUserProfile()` |
| `backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java` | Add `createUserProfile` tests |
| `frontend/types/api/auth.api.ts` | Add `displayName` to `RegisterResponse` |
| `frontend/services/auth.service.ts` | Add `name` param to `register()` |
| `frontend/app/(auth)/register/page.tsx` | Add name input; pass `name` to service |

---

## Task 1: common — add `displayName` to `CreateUserProfileRequest`

This is the shared contract between identity-service (producer) and user-service (consumer). It must be built before either service.

**Files:**
- Modify: `backend/common/src/main/java/com/bidnow/common/dto/request/CreateUserProfileRequest.java`

**Interfaces:**
- Produces: `CreateUserProfileRequest.displayName: String` (optional, nullable) — consumed by Task 2 and Task 3.

- [ ] **Step 1: Add `displayName` field to `CreateUserProfileRequest`**

Replace the file content with:

```java
/*
 * BidNow Auction System
 */
package com.bidnow.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new user profile")
public class CreateUserProfileRequest {

    @NotNull
    @Schema(description = "Unique identifier of the user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @NotBlank
    @Email
    @Schema(description = "Email address of the user", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Size(max = 100)
    @Schema(description = "Optional display name for the new profile", example = "John Doe")
    private String displayName;
}
```

- [ ] **Step 2: Build the common module**

```bash
mvn clean install -pl common -DskipTests
```

Expected: `BUILD SUCCESS`. No compilation errors.

- [ ] **Step 3: Commit**

```bash
git add backend/common/src/main/java/com/bidnow/common/dto/request/CreateUserProfileRequest.java
git commit -m "feat(common): add optional displayName to CreateUserProfileRequest"
```

---

## Task 2: identity-service — migration, entity, DTOs, mapper, service

All identity-service changes land together — they form one compilable unit and must be reviewed as a whole.

**Files:**
- Create: `backend/identity-service/src/main/resources/db/changelog/migrations/04-add-display-name-to-identity-users.sql`
- Modify: `backend/identity-service/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `backend/identity-service/src/main/java/com/bidnow/identity/domain/entity/User.java`
- Modify: `backend/identity-service/src/main/java/com/bidnow/identity/dto/request/RegisterRequest.java`
- Modify: `backend/identity-service/src/main/java/com/bidnow/identity/dto/response/RegisterResponse.java`
- Modify: `backend/identity-service/src/main/java/com/bidnow/identity/mapper/UserMapper.java`
- Modify: `backend/identity-service/src/main/java/com/bidnow/identity/service/impl/AuthServiceImpl.java`

**Interfaces:**
- Consumes from Task 1: `CreateUserProfileRequest.displayName`
- Produces:
  - `RegisterRequest.name: String` (validated `@NotBlank @Size(max=100)`) — consumed by frontend Task 4
  - `RegisterResponse.displayName: String` — consumed by frontend Task 4
  - `User.displayName: String` — stored on `identity_users.display_name`

- [ ] **Step 1: Create the Liquibase migration**

Create file `backend/identity-service/src/main/resources/db/changelog/migrations/04-add-display-name-to-identity-users.sql`:

```sql
ALTER TABLE identity_users ADD COLUMN display_name VARCHAR(100);
```

- [ ] **Step 2: Register the migration in the changelog**

Edit `backend/identity-service/src/main/resources/db/changelog/db.changelog-master.xml` to add the new include as the last entry:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/migrations/V1_001__init_schema.sql"/>
    <include file="/db/changelog/migrations/02-add-otp-columns.sql"/>
    <include file="/db/changelog/migrations/03-add-role-and-seed-admin.sql"/>
    <include file="/db/changelog/migrations/04-add-display-name-to-identity-users.sql"/>

</databaseChangeLog>
```

- [ ] **Step 3: Add `displayName` to the `User` entity**

Edit `backend/identity-service/src/main/java/com/bidnow/identity/domain/entity/User.java`. Add after the `email` field (line 37):

```java
@Column(name = "display_name", length = 100)
private String displayName;
```

- [ ] **Step 4: Add `name` to `RegisterRequest`**

Replace `backend/identity-service/src/main/java/com/bidnow/identity/dto/request/RegisterRequest.java` with:

```java
package com.bidnow.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for user registration")
public class RegisterRequest {

    @NotBlank
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Schema(description = "Display name", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Email
    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "User password (minimum 8 characters)", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
```

- [ ] **Step 5: Add `displayName` to `RegisterResponse`**

Replace `backend/identity-service/src/main/java/com/bidnow/identity/dto/response/RegisterResponse.java` with:

```java
package com.bidnow.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for user registration")
public class RegisterResponse {

    @Schema(description = "Unique identifier of the newly created user", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Display name stored for the user", example = "John Doe")
    private String displayName;

    @Schema(description = "Current status of the account", example = "PENDING_VERIFICATION", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountStatus;

    @Schema(description = "Whether the user email is verified", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isEmailVerified;

    @Schema(description = "Whether the user account is active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isActive;

    @Schema(description = "Timestamp when the user was created", example = "2023-10-27T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Update `UserMapper.toRegisterResponse()` to include `displayName`**

Replace `backend/identity-service/src/main/java/com/bidnow/identity/mapper/UserMapper.java` with:

```java
package com.bidnow.identity.mapper;

import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.response.AdminUserResponse;
import com.bidnow.identity.dto.response.RegisterResponse;
import com.bidnow.identity.dto.response.VerifyOtpResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserMapper {

    public RegisterResponse toRegisterResponse(User user) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .accountStatus(user.getAccountStatus().name())
                .isEmailVerified(user.getIsEmailVerified())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public VerifyOtpResponse toVerifyOtpResponse(User user) {
        return VerifyOtpResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .accountStatus(user.getAccountStatus().name())
                .isEmailVerified(user.getIsEmailVerified())
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    public AdminUserResponse toAdminResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getAccountStatus())
                .statusReason(user.getStatusReason())
                .isEmailVerified(user.getIsEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 7: Update `AuthServiceImpl` — store in `register()`, forward in `verifyOtp()`**

In `register()`, add `.displayName(request.getName())` to the `User.builder()` call. The complete builder block (lines 87–97) becomes:

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

In `verifyOtp()`, add `.displayName(user.getDisplayName())` to the `CreateUserProfileRequest.builder()` call (lines 176–179):

```java
userServiceClient.createUserProfile(CreateUserProfileRequest.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .displayName(user.getDisplayName())
        .build());
```

- [ ] **Step 8: Build identity-service**

```bash
mvn clean install -pl identity-service -DskipTests
```

Expected: `BUILD SUCCESS`. The service depends on `common` which was installed in Task 1.

- [ ] **Step 9: Commit**

```bash
git add \
  backend/identity-service/src/main/resources/db/changelog/migrations/04-add-display-name-to-identity-users.sql \
  backend/identity-service/src/main/resources/db/changelog/db.changelog-master.xml \
  backend/identity-service/src/main/java/com/bidnow/identity/domain/entity/User.java \
  backend/identity-service/src/main/java/com/bidnow/identity/dto/request/RegisterRequest.java \
  backend/identity-service/src/main/java/com/bidnow/identity/dto/response/RegisterResponse.java \
  backend/identity-service/src/main/java/com/bidnow/identity/mapper/UserMapper.java \
  backend/identity-service/src/main/java/com/bidnow/identity/service/impl/AuthServiceImpl.java
git commit -m "feat(identity): accept and store displayName at registration"
```

---

## Task 3: user-service — write `displayName` to profile + add tests

**Files:**
- Modify: `backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java`
- Modify: `backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java`

**Interfaces:**
- Consumes from Task 1: `CreateUserProfileRequest.displayName`
- Consumes from Task 2: `CreateUserProfileRequest` is now populated with `displayName` when called from identity-service's `verifyOtp()`

- [ ] **Step 1: Write the failing tests first**

Add two new test methods to the bottom of `backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java`, before the closing `}`.

Add these imports at the top of the file (after the existing imports):

```java
import com.bidnow.common.dto.request.CreateUserProfileRequest;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
```

Add these test methods:

```java
// -------------------------------------------------------
// createUserProfile
// -------------------------------------------------------

@Test
void createUserProfile_withDisplayName_persistsDisplayName() {
    UUID userId = UUID.randomUUID();
    CreateUserProfileRequest request = CreateUserProfileRequest.builder()
            .userId(userId)
            .email("alice@example.com")
            .displayName("Alice Smith")
            .build();

    UserProfile savedProfile = UserProfile.builder()
            .userId(userId)
            .displayName("Alice Smith")
            .build();

    when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
    when(userProfileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

    userProfileService.createUserProfile(request);

    ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
    verify(userProfileRepository).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isEqualTo("Alice Smith");
}

@Test
void createUserProfile_withNullDisplayName_persistsNullDisplayName() {
    UUID userId = UUID.randomUUID();
    CreateUserProfileRequest request = CreateUserProfileRequest.builder()
            .userId(userId)
            .email("bob@example.com")
            .displayName(null)
            .build();

    UserProfile savedProfile = UserProfile.builder()
            .userId(userId)
            .displayName(null)
            .build();

    when(userProfileRepository.existsByUserId(userId)).thenReturn(false);
    when(userProfileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

    userProfileService.createUserProfile(request);

    ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
    verify(userProfileRepository).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isNull();
}
```

- [ ] **Step 2: Run tests — expect failure**

```bash
mvn test -pl user-service -Dtest=UserProfileServiceImplTest
```

Expected: FAIL — `createUserProfile_withDisplayName_persistsDisplayName` fails because `UserProfile.builder()` in the service does not yet include `.displayName(...)`, so the captured value's `displayName` is `null`, not `"Alice Smith"`.

- [ ] **Step 3: Update `createUserProfile()` in `UserProfileServiceImpl`**

Find the `UserProfile.builder()` call inside `createUserProfile()` and add `.displayName(request.getDisplayName())`:

```java
UserProfile profile = UserProfile.builder()
        .userId(request.getUserId())
        .displayName(request.getDisplayName())
        .build();
```

Do not change anything else in the method.

- [ ] **Step 4: Run tests — expect pass**

```bash
mvn test -pl user-service -Dtest=UserProfileServiceImplTest
```

Expected: all 5 tests pass (`getUserSummary` × 3 + `createUserProfile` × 2). Output ends with `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit**

```bash
git add \
  backend/user-service/src/main/java/com/bidnow/user/service/impl/UserProfileServiceImpl.java \
  backend/user-service/src/test/java/com/bidnow/user/service/impl/UserProfileServiceImplTest.java
git commit -m "feat(user): populate displayName when creating user profile"
```

---

## Task 4: frontend — types, service, registration form

**Files:**
- Modify: `frontend/types/api/auth.api.ts`
- Modify: `frontend/services/auth.service.ts`
- Modify: `frontend/app/(auth)/register/page.tsx`

**Interfaces:**
- Consumes from Task 2: backend `RegisterRequest` now expects `{ name, email, password }` and `RegisterResponse` now includes `displayName`

- [ ] **Step 1: Add `displayName` to the `RegisterResponse` TypeScript interface**

In `frontend/types/api/auth.api.ts`, update `RegisterResponse`:

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

- [ ] **Step 2: Add `name` parameter to `authService.register()`**

In `frontend/services/auth.service.ts`, update the `register` method:

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

- [ ] **Step 3: Update the registration form**

Replace `frontend/app/(auth)/register/page.tsx` with:

```tsx
'use client'

import { useState, type FormEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'

import { authService } from '@/services/auth.service'
import { toast } from 'sonner'

export default function RegisterPage() {
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsLoading(true)
    setError(null)

    const formData = new FormData(e.currentTarget)
    const name = formData.get('name') as string
    const email = formData.get('email') as string
    const password = formData.get('password') as string

    try {
      await authService.register(name, email, password)
      toast.success('Registration successful! Please check your email for OTP.')
      router.push(`/verify-otp?email=${encodeURIComponent(email)}`)
    } catch (err: any) {
      setError(err.message || 'Registration failed. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="w-full max-w-sm">
      <div className="rounded-xl border bg-card p-8 flex flex-col gap-6">
        <div className="text-center">
          <h1 className="font-display font-medium text-[length:var(--font-size-xl)]">Create account</h1>
          <p className="mt-1 text-sm text-muted-foreground">Join BidNow and start bidding</p>
        </div>

        {error && (
          <div className="bg-destructive/10 text-destructive text-sm p-3 rounded-lg border border-destructive/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Name</Label>
            <Input id="name" name="name" type="text" placeholder="John Doe" required maxLength={100} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" name="email" type="email" placeholder="you@example.com" required />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input id="password" name="password" type="password" placeholder="8+ characters" required minLength={8} />
          </div>
          <Button type="submit" variant="brand" className="w-full h-11 mt-1" disabled={isLoading}>
            {isLoading && <Loader2 className="size-4 animate-spin mr-2" />}
            Create account
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link href="/login" className="text-[var(--color-text-link)] hover:underline font-medium">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Verify lint and types pass**

```bash
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
```

Expected: zero new errors (the pre-existing `catch (err: any)` errors in other files are not introduced by this change).

- [ ] **Step 5: Verify in browser**

1. Run `npm run dev` from `frontend/`.
2. Navigate to `http://localhost:3000/register`.
3. Confirm a "Name" input appears above the Email field.
4. Fill in all three fields and submit.
5. Expected: toast "Registration successful!" appears and the page redirects to `/verify-otp?email=...`.
6. Verify OTP. After verification, check `user_profiles` in the database: `SELECT display_name FROM user_profiles WHERE user_id = '<new-user-id>';` — expected: the name entered in step 4.

- [ ] **Step 6: Commit**

```bash
git add \
  frontend/types/api/auth.api.ts \
  frontend/services/auth.service.ts \
  "frontend/app/(auth)/register/page.tsx"
git commit -m "feat(frontend): add name field to registration form"
```
