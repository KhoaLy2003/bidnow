# Winner Payment Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a returning winner see the true state of a sale (paid, deposit forfeited, cancelled) by adding a read-only payment-status endpoint to wallet-service and using it in the Won panel.

**Architecture:** wallet-service exposes `GET /api/v1/wallets/payments/{auctionId}` over the existing `payment_holds` table, readable by the winner and the seller only (404 for anyone else), with no locks and no writes. The frontend adds a per-auction status lookup to `paymentStore`, calls it when the pending list has no hold for the auction, and extends the Won panel's state machine with `forfeited` and `cancelled`. No auction-service change, no migration, no event change.

**Tech Stack:** Java 17 / Spring Boot 3 / JUnit 5 + Mockito + MockMvc (wallet-service, Maven); Next.js 16 / TypeScript strict / Zustand 5 / Vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-10-15-winner-payment-status-design.md` (builds on `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`)

## Global Constraints

- The endpoint is read-only: `@Transactional(readOnly = true)`, **no pessimistic lock** (never use `findByAuctionIdForUpdate*`), no writes, no events, no migration.
- The response is exactly `{ auctionId, status, totalAmount }`. No timestamps (the hold's `completed_at` / `deadline` are timezone-less `LocalDateTime`), no deposit / remaining / `fundsHeld`.
- A caller who is neither the hold's winner nor its seller, and a missing hold, both get **404 `PAYMENT_HOLD_NOT_FOUND`** (never 403), so existence is not leaked.
- A malformed `auctionId` must return **400 `INVALID_INPUT`**, not 500 (the shared `GlobalExceptionHandler` has a catch-all `Exception` handler that would otherwise answer 500).
- Frontend: amounts are dollars (`number`), no `any`, strict TypeScript, shadcn/Base UI primitives, colours via CSS variables.
- Frontend lint: `eslint-config-next` 16 forbids calling React `setState` synchronously in an effect body. Effects may call **store actions**.
- Zustand 5: a selector must return a **stable** value. Never write `useStore((s) => s.x ?? { kind: 'idle' })` with an inline object; use the module constant `IDLE_LOOKUP`, or the component re-renders forever.
- Vitest runs in the `node` environment and only picks up `**/*.test.ts`; test pure modules, services and stores; components are verified manually.
- Backend commands run from `backend/` (Git Bash, Maven 3.9, Java 17, no `mvnw`). Verified baseline: `mvn -q test -pl wallet-service -am -Dtest=PaymentServiceImplTest,PaymentControllerTest -Dsurefire.failIfNoSpecifiedTests=false` exits 0. Frontend commands run from `frontend/`; baseline before this plan: `npx vitest run` = 16 files / 122 tests pass, `npx tsc --noEmit` exit 0.
- **Do not run `git commit`, `git add`, `git stash`, `git checkout` or `git reset`.** The user commits this feature at the end. The previous feature (winner payment UI) is already committed (`9752883`, `3454461`) and this work lives on branch `feature/winner-payment-status`, created from it. Each file this plan touches is edited by exactly one task, so a diff of that file against `HEAD` is task-scoped. Never revert or "clean up" changes you did not make.
- Deploy order is mandatory: wallet-service first, then the frontend. An older wallet-service answers the unmapped route with 500 (the shared catch-all handler), which the new Won panel shows as its error state.

## Review Focus

Failure modes the spec implies that no happy-path test covers. Each has a pinning test in the owning task.

1. **A stranger must learn nothing.** A user who is not the winner or seller gets the same 404 as "no hold" (Task 1 service test, Task 2 controller test).
2. **Malformed id gives 400, not 500**, with the existing `INVALID_INPUT` shape (Task 2).
3. **Stale lookups must not win.** A superseded lookup for an auction, or one in flight when `markPaid` or `reset` runs, must not overwrite the newer value or leak across users (Task 4 store tests).
4. **A hold beats the lookup, and the paid marker beats both**, so an expired-but-unforfeited hold still reads "expired" and a just-paid auction never flashes "forfeited" (Task 4 derive tests).
5. **Any 404, including one with a non-JSON body (for example from a gateway), must read as "no hold"**, not as a failure (Task 3 service test). An older wallet-service without the route answers 500, not 404, so wallet-service must be deployed first.

Also checked manually (Task 5/6): the new selector in `PaymentStatusBlock` does not cause a render loop.

---

## File Structure

**Create**
- `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/PaymentStatusResponse.java`

**Modify (backend, wallet-service)**
- `.../repository/PaymentHoldRepository.java`: `findByAuctionId`
- `.../service/PaymentService.java` and `.../service/impl/PaymentServiceImpl.java`: `getPaymentStatus`
- `.../controller/PaymentController.java`: the GET endpoint
- `.../exception/WalletExceptionHandler.java`: type-mismatch mapping to 400
- `src/test/.../service/impl/PaymentServiceImplTest.java`, `src/test/.../controller/PaymentControllerTest.java`

**Modify (frontend)**
- `types/api/wallet.api.ts`, `types/ui/wallet.ui.ts`: status types
- `services/wallet.service.ts` (+ `services/wallet.service.test.ts`): `getPaymentStatus`
- `store/paymentStore.ts` (+ `store/paymentStore.test.ts`): `statuses`, `loadStatus`, `markPaid`, `reset`
- `lib/payment-utils.ts` (+ `lib/payment-utils.test.ts`): `IDLE_LOOKUP`, extended `derivePaymentPanelState`
- `components/payment/PaymentStatusBlock.tsx`: lookup effect, new states

**Modify (docs)**
- `docs/architecture.md`, `docs/diagrams/05-winner-payment-flow.md`, `docs/epics/wallet/epic.md`, `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`

---

### Task 1: Payment status in the wallet service layer

**Files:**
- Create: `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/PaymentStatusResponse.java`
- Modify: `backend/wallet-service/src/main/java/com/bidnow/wallet/repository/PaymentHoldRepository.java`
- Modify: `backend/wallet-service/src/main/java/com/bidnow/wallet/service/PaymentService.java`
- Modify: `backend/wallet-service/src/main/java/com/bidnow/wallet/service/impl/PaymentServiceImpl.java`
- Test: `backend/wallet-service/src/test/java/com/bidnow/wallet/service/impl/PaymentServiceImplTest.java`

**Interfaces:**
- Consumes: existing `PaymentHold` (`getWinnerUserId`, `getSellerUserId`, `getStatus`, `getTotalAmount`, `getAuctionId`), `NotFoundException(String message, String errorCode)`, `WalletErrorCodes.PAYMENT_HOLD_NOT_FOUND`.
- Produces:
  - `PaymentStatusResponse` (Lombok `@Data @Builder`): `UUID auctionId`, `PaymentHoldStatus status`, `BigDecimal totalAmount`
  - `Optional<PaymentHold> PaymentHoldRepository.findByAuctionId(UUID auctionId)`
  - `PaymentStatusResponse PaymentService.getPaymentStatus(UUID callerUserId, UUID auctionId)`

- [ ] **Step 1: Write the failing service tests**

In `PaymentServiceImplTest.java`, add this import next to the existing `PendingPaymentResponse` import:

```java
import com.bidnow.wallet.dto.response.PaymentStatusResponse;
```

Then insert this block immediately before the line `    // ── confirmPayment ────────────────────────────────────────────────────────` (right after `getPendingPayments_mapsHoldsWithHoursLeftAndExpiredFlag`):

```java
    // ── getPaymentStatus ──────────────────────────────────────────────────────

    @Test
    void getPaymentStatus_winnerSeesEveryStatus() {
        for (PaymentHoldStatus s : PaymentHoldStatus.values()) {
            PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                    LocalDateTime.now().plusHours(1), s);
            when(paymentHoldRepository.findByAuctionId(auctionId)).thenReturn(Optional.of(h));

            PaymentStatusResponse result = paymentService.getPaymentStatus(winnerUserId, auctionId);

            assertThat(result.getAuctionId()).isEqualTo(auctionId);
            assertThat(result.getStatus()).isEqualTo(s);
            assertThat(result.getTotalAmount()).isEqualByComparingTo("500.00");
        }
    }

    @Test
    void getPaymentStatus_sellerSeesStatus() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(1), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionId(auctionId)).thenReturn(Optional.of(h));

        PaymentStatusResponse result = paymentService.getPaymentStatus(sellerUserId, auctionId);

        assertThat(result.getStatus()).isEqualTo(PaymentHoldStatus.COMPLETED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void getPaymentStatus_otherUser_isNotFoundAndLeaksNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(1), PaymentHoldStatus.PENDING_PAYMENT);
        when(paymentHoldRepository.findByAuctionId(auctionId)).thenReturn(Optional.of(h));
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> paymentService.getPaymentStatus(stranger, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void getPaymentStatus_noHold_isNotFound() {
        when(paymentHoldRepository.findByAuctionId(auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentStatus(winnerUserId, auctionId))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("PAYMENT_HOLD_NOT_FOUND");
    }

    @Test
    void getPaymentStatus_isAPlainRead_takesNoLockAndWritesNothing() {
        PaymentHold h = hold("500.00", "50.00", "450.00", true, null,
                LocalDateTime.now().plusHours(1), PaymentHoldStatus.COMPLETED);
        when(paymentHoldRepository.findByAuctionId(auctionId)).thenReturn(Optional.of(h));

        paymentService.getPaymentStatus(winnerUserId, auctionId);

        verify(paymentHoldRepository, never()).findByAuctionIdForUpdate(any());
        verify(paymentHoldRepository, never()).findByAuctionIdForUpdateSkipLocked(any());
        verify(paymentHoldRepository, never()).saveAndFlush(any());
        verifyNoInteractions(walletRepository, transactionRepository, eventPublisher);
    }

```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`):

```bash
mvn test -pl wallet-service -am -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|ERROR|COMPILATION" | head -20
```

Expected: `COMPILATION ERROR` (`PaymentStatusResponse`, `findByAuctionId` and `getPaymentStatus` do not exist).

- [ ] **Step 3: Create the response DTO**

Create `backend/wallet-service/src/main/java/com/bidnow/wallet/dto/response/PaymentStatusResponse.java`:

```java
package com.bidnow.wallet.dto.response;

import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Status of an auction's payment hold, for its winner or seller. Deliberately no timestamps or deposit detail. */
@Data
@Builder
public class PaymentStatusResponse {
    private UUID auctionId;
    private PaymentHoldStatus status;
    private BigDecimal totalAmount;
}
```

- [ ] **Step 4: Add the repository method**

In `PaymentHoldRepository.java`, add directly after `boolean existsByAuctionId(UUID auctionId);`:

```java

    /** Plain read of an auction's hold, no lock: status lookups must not contend with confirm / cancel / forfeit. */
    Optional<PaymentHold> findByAuctionId(UUID auctionId);
```

- [ ] **Step 5: Add the service method**

In `PaymentService.java`, add the import `com.bidnow.wallet.dto.response.PaymentStatusResponse;` and, directly after `List<PendingPaymentResponse> getPendingPayments(UUID userId);`:

```java

    /** Status of the hold for an auction, for its winner or seller; anyone else, or no hold, is a 404. Read-only. */
    PaymentStatusResponse getPaymentStatus(UUID callerUserId, UUID auctionId);
```

In `PaymentServiceImpl.java`, add the import `com.bidnow.wallet.dto.response.PaymentStatusResponse;` (next to the `PendingPaymentResponse` import) and add this method at the end of the class, directly after `getPendingPayments` (before the final closing brace):

```java

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID callerUserId, UUID auctionId) {
        PaymentHold hold = paymentHoldRepository.findByAuctionId(auctionId)
                .filter(h -> callerUserId.equals(h.getWinnerUserId()) || callerUserId.equals(h.getSellerUserId()))
                .orElseThrow(() -> new NotFoundException("No payment hold for auction " + auctionId,
                        WalletErrorCodes.PAYMENT_HOLD_NOT_FOUND));
        return PaymentStatusResponse.builder()
                .auctionId(hold.getAuctionId())
                .status(hold.getStatus())
                .totalAmount(hold.getTotalAmount())
                .build();
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run (from `backend/`):

```bash
mvn test -pl wallet-service -am -Dtest=PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|ERROR|COMPILATION" | head -20
```

Expected: `BUILD SUCCESS` with `Failures: 0, Errors: 0` (the five new tests included).

- [ ] **Step 7: Make sure nothing else implements `PaymentService`**

Run (from `backend/`):

```bash
mvn -q test-compile -pl wallet-service -am 2>&1 | tail -15; echo "exit: ${PIPESTATUS[0]}"
```

Expected: exit 0 (compiles every main and test class of wallet-service). Do not commit.

---

### Task 2: The HTTP endpoint and malformed-id mapping

**Files:**
- Modify: `backend/wallet-service/src/main/java/com/bidnow/wallet/controller/PaymentController.java`
- Modify: `backend/wallet-service/src/main/java/com/bidnow/wallet/exception/WalletExceptionHandler.java`
- Test: `backend/wallet-service/src/test/java/com/bidnow/wallet/controller/PaymentControllerTest.java`

**Interfaces:**
- Consumes: `PaymentService.getPaymentStatus(UUID, UUID)` and `PaymentStatusResponse` (Task 1); `@AuthenticatedUserId`, `BaseResponse.success`, `ErrorResponse`, `ErrorCodes.INVALID_INPUT`.
- Produces: `GET /api/v1/wallets/payments/{auctionId}` → `200 {data:{auctionId,status,totalAmount}}`; `404 PAYMENT_HOLD_NOT_FOUND`; `400 INVALID_INPUT` with `errors{auctionId:"invalid value"}` for a non-UUID id.

- [ ] **Step 1: Write the failing controller tests**

In `PaymentControllerTest.java`, add these imports:

```java
import com.bidnow.wallet.domain.enums.PaymentHoldStatus;
import com.bidnow.wallet.dto.response.PaymentStatusResponse;
```

and add these tests inside the class, after `getPendingPayments_returnsList`:

```java
    @Test
    void getPaymentStatus_returnsStatusAndTotal() throws Exception {
        when(paymentService.getPaymentStatus(userId, auctionId)).thenReturn(PaymentStatusResponse.builder()
                .auctionId(auctionId)
                .status(PaymentHoldStatus.FORFEITED)
                .totalAmount(new BigDecimal("500.00"))
                .build());

        mockMvc.perform(get(BASE + "/" + auctionId).header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auctionId").value(auctionId.toString()))
                .andExpect(jsonPath("$.data.status").value("FORFEITED"))
                .andExpect(jsonPath("$.data.totalAmount").value(500.00))
                .andExpect(jsonPath("$.data.deadline").doesNotExist())
                .andExpect(jsonPath("$.data.remaining").doesNotExist());
    }

    @Test
    void getPaymentStatus_noHoldOrNotParticipant_returns404() throws Exception {
        when(paymentService.getPaymentStatus(userId, auctionId))
                .thenThrow(new NotFoundException("none", "PAYMENT_HOLD_NOT_FOUND"));

        mockMvc.perform(get(BASE + "/" + auctionId).header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_HOLD_NOT_FOUND"));
    }

    @Test
    void getPaymentStatus_malformedAuctionId_returns400AndSkipsService() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid").header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.auctionId").value("invalid value"));
        verify(paymentService, never()).getPaymentStatus(any(), any());
    }

    @Test
    void getPendingPayments_literalPathStillBeatsTheAuctionIdTemplate() throws Exception {
        when(paymentService.getPendingPayments(userId)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/pending").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());
        verify(paymentService).getPendingPayments(userId);
        verify(paymentService, never()).getPaymentStatus(any(), any());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `backend/`):

```bash
mvn test -pl wallet-service -am -Dtest=PaymentControllerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|FAIL|getPaymentStatus" | head -20
```

Expected: FAIL. The three `getPaymentStatus_*` tests fail because no handler is mapped for `GET /payments/{auctionId}`; the literal-path test passes already (it pins the precedence guarantee).

- [ ] **Step 3: Add the endpoint**

In `PaymentController.java`, add the imports `com.bidnow.wallet.dto.response.PaymentStatusResponse` and `org.springframework.web.bind.annotation.PathVariable`, then add directly after the `getPendingPayments` method:

```java

    @Operation(summary = "Payment status",
            description = "Status of the payment hold for an auction. Visible to the winner and the seller only; "
                    + "anyone else, or an auction without a hold, gets 404.")
    @GetMapping("/{auctionId}")
    public ResponseEntity<BaseResponse<PaymentStatusResponse>> getPaymentStatus(@AuthenticatedUserId UUID userId,
                                                                                 @PathVariable UUID auctionId) {
        return ResponseEntity.ok(BaseResponse.success(paymentService.getPaymentStatus(userId, auctionId)));
    }
```

- [ ] **Step 4: Map a malformed id to 400**

In `WalletExceptionHandler.java`, add the imports `com.bidnow.common.constant.ErrorCodes` and `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`, then add this handler after `handleInsufficientBalance`:

```java

    /** A path or query value that does not convert (for example a non-UUID auction id) is a client error, not a 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        log.warn("Invalid value for '{}'", ex.getName());
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCodes.INVALID_INPUT)
                .message("Invalid value for '" + ex.getName() + "'")
                .path(request.getRequestURI())
                .errors(Map.of(ex.getName(), "invalid value"))
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run (from `backend/`):

```bash
mvn test -pl wallet-service -am -Dtest=PaymentControllerTest,PaymentServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|ERROR|FAIL" | head -20
```

Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` for both classes. If `getPaymentStatus_malformedAuctionId_returns400AndSkipsService` still returns 500, confirm `WalletExceptionHandler` is registered first in `PaymentControllerTest.setUp` (it is: `setControllerAdvice(new WalletExceptionHandler(), new GlobalExceptionHandler())`) and that the new method is annotated `@ExceptionHandler(MethodArgumentTypeMismatchException.class)`. Do not commit.

---

### Task 3: Frontend types and service method

**Files:**
- Modify: `frontend/types/api/wallet.api.ts`
- Modify: `frontend/types/ui/wallet.ui.ts`
- Modify: `frontend/services/wallet.service.ts`
- Test: `frontend/services/wallet.service.test.ts`

**Interfaces:**
- Consumes: `apiFetch`, `PaymentRequestError`, `toPaymentRequestError` (already in `wallet.service.ts`), `ApiResponse`.
- Produces:
  - `type PaymentHoldStatus = 'PENDING_PAYMENT' | 'COMPLETED' | 'CANCELLED' | 'FORFEITED'`
  - `interface PaymentStatusResponse { auctionId: string; status: PaymentHoldStatus; totalAmount: number }`
  - `type PaymentStatusLookup = { kind: 'idle' } | { kind: 'loading' } | { kind: 'found'; status: PaymentHoldStatus } | { kind: 'not-found' } | { kind: 'failed' }`
  - `walletService.getPaymentStatus(auctionId: string): Promise<PaymentStatusResponse | null>` (`null` on any 404)

- [ ] **Step 1: Write the failing service tests**

Append to `frontend/services/wallet.service.test.ts`:

```ts

describe('walletService.getPaymentStatus', () => {
  beforeEach(() => mockFetch.mockReset())

  it('gets the status for an auction and returns the data', async () => {
    const data = { auctionId: 'a-1', status: 'COMPLETED', totalAmount: 500 }
    mockFetch.mockResolvedValue(json({ status: 200, data }))

    const result = await walletService.getPaymentStatus('a-1')

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/wallets/payments/a-1')
    expect(result).toEqual(data)
  })

  it('returns null on a 404 with the backend error body (no hold for this caller)', async () => {
    mockFetch.mockResolvedValue(
      json({ status: 404, errorCode: 'PAYMENT_HOLD_NOT_FOUND', message: 'none' }, 404))

    await expect(walletService.getPaymentStatus('a-1')).resolves.toBeNull()
  })

  it('returns null on a 404 without a JSON body (for example a gateway 404)', async () => {
    mockFetch.mockResolvedValue(new Response('Not Found', { status: 404 }))

    await expect(walletService.getPaymentStatus('a-1')).resolves.toBeNull()
  })

  it('throws a PaymentRequestError for any other failure', async () => {
    mockFetch.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 }))

    const err = await walletService.getPaymentStatus('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).status).toBe(502)
    expect((err as PaymentRequestError).body).toBeNull()
  })

  it('keeps the error body for a JSON failure', async () => {
    mockFetch.mockResolvedValue(json({ status: 500, errorCode: 'UNEXPECTED_ERROR', message: 'boom' }, 500))

    const err = await walletService.getPaymentStatus('a-1').catch((e: unknown) => e)

    expect(err).toBeInstanceOf(PaymentRequestError)
    expect((err as PaymentRequestError).body?.errorCode).toBe('UNEXPECTED_ERROR')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `frontend/`): `npx vitest run services/wallet.service.test.ts`
Expected: FAIL (`walletService.getPaymentStatus is not a function`).

- [ ] **Step 3: Add the API types**

Append to `frontend/types/api/wallet.api.ts`:

```ts

export type PaymentHoldStatus = 'PENDING_PAYMENT' | 'COMPLETED' | 'CANCELLED' | 'FORFEITED';

export interface PaymentStatusResponse {
  auctionId: string;
  status: PaymentHoldStatus;
  totalAmount: number;
}
```

- [ ] **Step 4: Add the UI lookup type**

In `frontend/types/ui/wallet.ui.ts`, add as the first line of the file:

```ts
import type { PaymentHoldStatus } from '@/types/api/wallet.api'

```

and append at the end of the file:

```ts

/** Result of asking wallet-service what became of an auction's payment hold ("idle" = not asked yet). */
export type PaymentStatusLookup =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'found'; status: PaymentHoldStatus }
  | { kind: 'not-found' }
  | { kind: 'failed' }
```

- [ ] **Step 5: Add the service method**

In `frontend/services/wallet.service.ts`, add `PaymentStatusResponse` to the type import from `@/types/api/wallet.api` (the list that already has `ConfirmPaymentResponse`), and add this method inside `walletService`, after `confirmPayment`:

```ts

  /**
   * The status of the caller's payment hold for an auction (winner or seller). Resolves null on any 404:
   * no hold yet, not a participant, or a gateway 404. (An older wallet-service without this route answers 500, not 404: deploy wallet-service first.)
   */
  async getPaymentStatus(auctionId: string): Promise<PaymentStatusResponse | null> {
    const response = await apiFetch(`/api/v1/wallets/payments/${encodeURIComponent(auctionId)}`)
    if (response.status === 404) return null
    if (!response.ok) throw await toPaymentRequestError(response)
    const body: ApiResponse<PaymentStatusResponse> = await response.json()
    return body.data
  },
```

- [ ] **Step 6: Run the tests, typecheck and lint**

Run (from `frontend/`): `npx vitest run services/wallet.service.test.ts && npx tsc --noEmit && npx eslint types services`
Expected: all service tests pass (the earlier 5 plus these 5); `tsc` and eslint exit 0. Do not commit.

---

### Task 4: Store lookup and panel state machine

**Files:**
- Modify: `frontend/store/paymentStore.ts`
- Modify: `frontend/lib/payment-utils.ts`
- Test: `frontend/store/paymentStore.test.ts`
- Test: `frontend/lib/payment-utils.test.ts`

**Interfaces:**
- Consumes: `walletService.getPaymentStatus`, `PaymentStatusResponse`, `PaymentHoldStatus` (Task 3); `PaymentStatusLookup` (Task 3); existing `PendingPayment`, `mapPendingPaymentResponse`.
- Produces:
  - store: `statuses: Record<string, PaymentStatusLookup>`; `loadStatus(auctionId: string): Promise<void>`; `markPaid` now also records `found COMPLETED`; `reset` clears `statuses`
  - `IDLE_LOOKUP: PaymentStatusLookup` (a stable module constant)
  - `type PaymentPanelState = 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'forfeited' | 'cancelled' | 'none'`
  - `PaymentPanelInput` gains `statusLookup: PaymentStatusLookup`; `derivePaymentPanelState` honours the precedence in the spec

- [ ] **Step 1: Write the failing store tests**

In `frontend/store/paymentStore.test.ts`:

(a) Replace the mock factory

```ts
vi.mock('@/services/wallet.service', () => ({
  walletService: { getPendingPayments: vi.fn() },
}))
```

with

```ts
vi.mock('@/services/wallet.service', () => ({
  walletService: { getPendingPayments: vi.fn(), getPaymentStatus: vi.fn() },
}))
```

(b) Replace the line `import type { PendingPaymentResponse } from '@/types/api/wallet.api'` with

```ts
import type { PaymentHoldStatus, PaymentStatusResponse, PendingPaymentResponse } from '@/types/api/wallet.api'
```

(c) Append this block at the end of the file:

```ts

function statusDto(status: PaymentHoldStatus, auctionId = 'a-1'): PaymentStatusResponse {
  return { auctionId, status, totalAmount: 500 }
}

describe('paymentStore.loadStatus', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    store().reset()
  })

  it('records a found status', async () => {
    service.getPaymentStatus.mockResolvedValue(statusDto('FORFEITED'))

    await store().loadStatus('a-1')

    expect(service.getPaymentStatus).toHaveBeenCalledWith('a-1')
    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'FORFEITED' })
  })

  it('records not-found when there is no hold for the caller', async () => {
    service.getPaymentStatus.mockResolvedValue(null)

    await store().loadStatus('a-1')

    expect(store().statuses['a-1']).toEqual({ kind: 'not-found' })
  })

  it('records failed when the request throws', async () => {
    service.getPaymentStatus.mockRejectedValue(new Error('down'))

    await store().loadStatus('a-1')

    expect(store().statuses['a-1']).toEqual({ kind: 'failed' })
  })

  it('shows loading while the request is in flight', async () => {
    let resolve!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolve = r }))

    const pending = store().loadStatus('a-1')
    expect(store().statuses['a-1']).toEqual({ kind: 'loading' })
    resolve(statusDto('COMPLETED'))
    await pending

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
  })

  it('keeps a separate lookup per auction', async () => {
    service.getPaymentStatus
      .mockResolvedValueOnce(statusDto('COMPLETED', 'a-1'))
      .mockResolvedValueOnce(null)

    await store().loadStatus('a-1')
    await store().loadStatus('a-2')

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
    expect(store().statuses['a-2']).toEqual({ kind: 'not-found' })
  })

  it('ignores a superseded lookup for the same auction', async () => {
    let resolveFirst!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus
      .mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveFirst = r }))
      .mockResolvedValueOnce(statusDto('COMPLETED'))

    const first = store().loadStatus('a-1')
    await store().loadStatus('a-1')
    resolveFirst(null)
    await first

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
  })

  it('markPaid records COMPLETED and ignores a lookup that started before it', async () => {
    let resolveSlow!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveSlow = r }))

    const slow = store().loadStatus('a-1')
    store().markPaid('a-1')
    resolveSlow(null)
    await slow

    expect(store().statuses['a-1']).toEqual({ kind: 'found', status: 'COMPLETED' })
    expect(store().paidIds.has('a-1')).toBe(true)
  })

  it('reset clears the lookups and ignores a lookup still in flight', async () => {
    let resolveSlow!: (v: PaymentStatusResponse | null) => void
    service.getPaymentStatus.mockReturnValueOnce(new Promise<PaymentStatusResponse | null>((r) => { resolveSlow = r }))
    const slow = store().loadStatus('a-1')

    store().reset()
    resolveSlow(statusDto('FORFEITED'))
    await slow

    expect(store().statuses).toEqual({})
  })
})
```

- [ ] **Step 2: Write the failing derive tests**

In `frontend/lib/payment-utils.test.ts`:

(a) In the existing `describe('derivePaymentPanelState', ...)`, replace the `base` constant

```ts
  const base: PaymentPanelInput = {
    holds: [], auctionId: 'a-1', paidIds: new Set<string>(),
    hasFetched: true, loadFailed: false, pollWindowOpen: false,
  }
```

with

```ts
  const base: PaymentPanelInput = {
    holds: [], auctionId: 'a-1', paidIds: new Set<string>(),
    hasFetched: true, loadFailed: false, pollWindowOpen: false,
    statusLookup: { kind: 'not-found' },
  }
```

(every existing derive test keeps its meaning: with no hold and a `not-found` lookup the old "preparing / none" results are unchanged).

(b) Add these two imports near the top, next to the existing type imports:

```ts
import type { PaymentHoldStatus } from '@/types/api/wallet.api'
import type { PaymentStatusLookup } from '@/types/ui/wallet.ui'
```

(c) Append this block at the end of the file:

```ts

describe('derivePaymentPanelState: status lookup', () => {
  const found = (status: PaymentHoldStatus): PaymentStatusLookup => ({ kind: 'found', status })
  const base: PaymentPanelInput = {
    holds: [], auctionId: 'a-1', paidIds: new Set<string>(),
    hasFetched: true, loadFailed: false, pollWindowOpen: false,
    statusLookup: { kind: 'idle' },
  }
  const state = (over: Partial<PaymentPanelInput>) => derivePaymentPanelState({ ...base, ...over }).state

  it('is loading while the lookup has not answered', () => {
    expect(state({ statusLookup: { kind: 'idle' } })).toBe('loading')
    expect(state({ statusLookup: { kind: 'loading' } })).toBe('loading')
  })

  it('is error when the lookup failed', () => {
    expect(state({ statusLookup: { kind: 'failed' } })).toBe('error')
  })

  it('maps each found status', () => {
    expect(state({ statusLookup: found('COMPLETED') })).toBe('paid')
    expect(state({ statusLookup: found('FORFEITED') })).toBe('forfeited')
    expect(state({ statusLookup: found('CANCELLED') })).toBe('cancelled')
  })

  it('treats a PENDING_PAYMENT lookup as preparing, whether or not the poll window is open', () => {
    expect(state({ statusLookup: found('PENDING_PAYMENT'), pollWindowOpen: true })).toBe('preparing')
    expect(state({ statusLookup: found('PENDING_PAYMENT'), pollWindowOpen: false })).toBe('preparing')
  })

  it('is preparing for not-found while the window is open and none once it has closed', () => {
    expect(state({ statusLookup: { kind: 'not-found' }, pollWindowOpen: true })).toBe('preparing')
    expect(state({ statusLookup: { kind: 'not-found' }, pollWindowOpen: false })).toBe('none')
  })

  it('a pending hold beats the lookup, so an unforfeited expired hold still reads expired', () => {
    const hold = {
      auctionId: 'a-1', totalAmount: 500, depositApplied: 100, remaining: 400,
      fundsHeld: false, hoursLeft: 0, expired: true,
    }
    expect(derivePaymentPanelState({ ...base, holds: [hold], statusLookup: found('FORFEITED') }))
      .toEqual({ state: 'expired', hold })
  })

  it('the paid marker beats the lookup', () => {
    expect(state({ paidIds: new Set(['a-1']), statusLookup: found('FORFEITED') })).toBe('paid')
  })

  it('ignores the lookup until the pending list has loaded', () => {
    expect(state({ hasFetched: false, statusLookup: found('COMPLETED') })).toBe('loading')
    expect(state({ hasFetched: false, loadFailed: true, statusLookup: found('COMPLETED') })).toBe('error')
  })
})
```

- [ ] **Step 3: Run the tests to verify they fail**

Run (from `frontend/`): `npx vitest run store/paymentStore.test.ts lib/payment-utils.test.ts`
Expected: FAIL (`loadStatus` / `statuses` do not exist; `statusLookup` is not a known input; the new states are not produced). `npx tsc --noEmit` would also fail on the test files at this point.

- [ ] **Step 4: Update the store**

Replace the contents of `frontend/store/paymentStore.ts` with:

```ts
import { create } from 'zustand'
import { walletService } from '@/services/wallet.service'
import { mapPendingPaymentResponse } from '@/types/mappers/wallet.mapper'
import type { PaymentStatusLookup, PendingPayment } from '@/types/ui/wallet.ui'

interface PaymentState {
  /** The caller's unpaid winner holds. */
  holds:      PendingPayment[]
  /** Auctions paid in this browser session; lets the panel say "paid" once the hold has left the server list. */
  paidIds:    ReadonlySet<string>
  /** What wallet-service says became of each auction's hold (missing = not asked yet); see loadStatus. */
  statuses:   Record<string, PaymentStatusLookup>
  isLoading:  boolean
  hasFetched: boolean
  /** The latest load failed. The panel only surfaces it before the first successful load. */
  loadFailed: boolean
  load:       () => Promise<void>
  /** Reloads only if the store has been loaded before (nobody is looking otherwise). */
  refresh:    () => Promise<void>
  /** Reads this auction's hold status (winner or seller). The latest request per auction wins. */
  loadStatus: (auctionId: string) => Promise<void>
  markPaid:   (auctionId: string) => void
  reset:      () => void
}

export const usePaymentStore = create<PaymentState>((set, get) => {
  /** Bumped by every load and by reset(); only the latest load may write its result. */
  let seq = 0
  /** Source of request tokens for loadStatus. */
  let statusCounter = 0
  /** The one token allowed to write each auction's lookup; cleared by reset() and, per auction, by markPaid(). */
  const latestStatusToken = new Map<string, number>()

  return {
    holds:      [],
    paidIds:    new Set<string>(),
    statuses:   {},
    isLoading:  false,
    hasFetched: false,
    loadFailed: false,

    load: async () => {
      const mine = ++seq
      set({ isLoading: true })
      try {
        const dtos = await walletService.getPendingPayments()
        if (mine !== seq) return
        const paid = get().paidIds
        set({
          holds:      dtos.map(mapPendingPaymentResponse).filter((h) => !paid.has(h.auctionId)),
          hasFetched: true,
          loadFailed: false,
          isLoading:  false,
        })
      } catch {
        if (mine !== seq) return
        set({ loadFailed: true, isLoading: false })
      }
    },

    refresh: async () => {
      if (get().hasFetched) await get().load()
    },

    loadStatus: async (auctionId) => {
      const token = ++statusCounter
      latestStatusToken.set(auctionId, token)
      set((s) => ({ statuses: { ...s.statuses, [auctionId]: { kind: 'loading' } } }))
      let lookup: PaymentStatusLookup
      try {
        const response = await walletService.getPaymentStatus(auctionId)
        lookup = response ? { kind: 'found', status: response.status } : { kind: 'not-found' }
      } catch {
        lookup = { kind: 'failed' }
      }
      if (latestStatusToken.get(auctionId) !== token) return
      set((s) => ({ statuses: { ...s.statuses, [auctionId]: lookup } }))
    },

    markPaid: (auctionId) => {
      // A lookup that started before the payment must not overwrite "completed" with stale data.
      latestStatusToken.delete(auctionId)
      set((s) => ({
        holds:    s.holds.filter((h) => h.auctionId !== auctionId),
        paidIds:  new Set(s.paidIds).add(auctionId),
        statuses: { ...s.statuses, [auctionId]: { kind: 'found', status: 'COMPLETED' } },
      }))
    },

    reset: () => {
      seq++
      latestStatusToken.clear()
      set({
        holds: [], paidIds: new Set<string>(), statuses: {},
        isLoading: false, hasFetched: false, loadFailed: false,
      })
    },
  }
})
```

- [ ] **Step 5: Update the pure logic**

In `frontend/lib/payment-utils.ts`:

(a) Replace the line `import type { PendingPayment } from '@/types/ui/wallet.ui'` with:

```ts
import type { PaymentHoldStatus } from '@/types/api/wallet.api'
import type { PaymentStatusLookup, PendingPayment } from '@/types/ui/wallet.ui'
```

(b) Replace everything from the line `export type PaymentPanelState = ...` through the end of the `derivePaymentPanelState` function (the line before `export interface AuctionPreview`) with:

```ts
/** The stable "not asked yet" lookup. Selectors must return this constant, never a fresh `{ kind: 'idle' }`. */
export const IDLE_LOOKUP: PaymentStatusLookup = { kind: 'idle' }

export type PaymentPanelState =
  | 'loading' | 'error' | 'preparing' | 'due' | 'expired' | 'paid' | 'forfeited' | 'cancelled' | 'none'

export interface PaymentPanelInput {
  holds:          readonly PendingPayment[]
  auctionId:      string
  paidIds:        ReadonlySet<string>
  hasFetched:     boolean
  loadFailed:     boolean
  pollWindowOpen: boolean
  statusLookup:   PaymentStatusLookup
}

function stateForStatus(status: PaymentHoldStatus): PaymentPanelState {
  switch (status) {
    case 'COMPLETED':
      return 'paid'
    case 'FORFEITED':
      return 'forfeited'
    case 'CANCELLED':
      return 'cancelled'
    case 'PENDING_PAYMENT':
      return 'preparing' // a hold exists but the pending list has not caught up yet
  }
}

/**
 * Precedence: the paid marker, then a pending hold (due / expired), then the pending list's own load state,
 * then what wallet-service says became of the hold.
 */
export function derivePaymentPanelState(
  input: PaymentPanelInput,
): { state: PaymentPanelState; hold: PendingPayment | null } {
  if (input.paidIds.has(input.auctionId)) return { state: 'paid', hold: null }

  const hold = input.holds.find((h) => h.auctionId === input.auctionId) ?? null
  if (hold) return { state: hold.expired ? 'expired' : 'due', hold }

  if (!input.hasFetched) return { state: input.loadFailed ? 'error' : 'loading', hold: null }

  const lookup = input.statusLookup
  switch (lookup.kind) {
    case 'idle':
    case 'loading':
      return { state: 'loading', hold: null }
    case 'failed':
      return { state: 'error', hold: null }
    case 'not-found':
      return { state: input.pollWindowOpen ? 'preparing' : 'none', hold: null }
    case 'found':
      return { state: stateForStatus(lookup.status), hold: null }
  }
}

```

- [ ] **Step 6: Run the tests, typecheck and lint**

Run (from `frontend/`): `npx vitest run store/paymentStore.test.ts lib/payment-utils.test.ts`
Expected: PASS (all existing and new tests).

Then (the component still calls the old `derivePaymentPanelState` signature, so `tsc` is expected to fail on `PaymentStatusBlock.tsx` until Task 5; that single error is acceptable here): `npx tsc --noEmit 2>&1 | grep -v PaymentStatusBlock | head` should print nothing, and `npx eslint store lib` should exit 0. Do not commit.

---

### Task 5: Won panel reads the status

**Files:**
- Modify: `frontend/components/payment/PaymentStatusBlock.tsx`

**Interfaces:**
- Consumes: `usePaymentStore` (`statuses`, `loadStatus`), `IDLE_LOOKUP`, extended `derivePaymentPanelState`, `PaymentPanelState` (Task 4); `usePendingPayments` (existing).
- Produces: the panel renders the new states `forfeited` and `cancelled`; Retry reloads both the pending list and the status; the status lookup runs when the pending list has loaded and this auction has no pending hold, and again whenever the hold disappears.

No unit test (component); the gates are `tsc`, eslint and the manual checks.

- [ ] **Step 1: Replace the component**

Replace the contents of `frontend/components/payment/PaymentStatusBlock.tsx` with:

```tsx
'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PayNowDialog } from '@/components/payment/PayNowDialog'
import { usePendingPayments } from '@/hooks/usePendingPayments'
import { formatCurrency } from '@/lib/format'
import {
  IDLE_LOOKUP,
  PREPARING_POLL_MS,
  PREPARING_WINDOW_MS,
  derivePaymentPanelState,
  formatHoursLeft,
  isRecentWin,
} from '@/lib/payment-utils'
import { usePaymentStore } from '@/store/paymentStore'

interface PaymentStatusBlockProps {
  auctionId:    string
  title:        string
  completedAt?: Date
}

export function PaymentStatusBlock({ auctionId, title, completedAt }: PaymentStatusBlockProps) {
  const { holds, paidIds, hasFetched, loadFailed, refetch } = usePendingPayments()
  // IDLE_LOOKUP is a module constant on purpose: a selector returning a fresh object makes zustand re-render forever.
  const statusLookup = usePaymentStore((s) => s.statuses[auctionId] ?? IDLE_LOOKUP)
  const loadStatus = usePaymentStore((s) => s.loadStatus)
  const [dialogOpen, setDialogOpen] = useState(false)
  // Only a fresh win can still be waiting for its hold; an old win with no hold is simply "nothing due".
  const [windowOpen, setWindowOpen] = useState(() => isRecentWin(completedAt))

  const holdPresent = holds.some((h) => h.auctionId === auctionId)
  const paidHere = paidIds.has(auctionId)

  useEffect(() => {
    if (!windowOpen) return
    const t = setTimeout(() => setWindowOpen(false), PREPARING_WINDOW_MS)
    return () => clearTimeout(t)
  }, [windowOpen])

  // With no pending hold for this auction, ask wallet-service what became of it. Runs once the pending list has
  // loaded and again whenever the hold disappears (paid elsewhere, forfeited), not on every poll tick.
  useEffect(() => {
    if (hasFetched && !holdPresent && !paidHere) void loadStatus(auctionId)
  }, [auctionId, hasFetched, holdPresent, paidHere, loadStatus])

  const { state, hold } = derivePaymentPanelState({
    holds, auctionId, paidIds, hasFetched, loadFailed, pollWindowOpen: windowOpen, statusLookup,
  })

  useEffect(() => {
    if (state !== 'preparing') return
    const i = setInterval(() => void refetch(), PREPARING_POLL_MS)
    return () => clearInterval(i)
  }, [state, refetch])

  switch (state) {
    case 'loading':
      return <Skeleton className="h-11 w-full" />

    case 'error':
      return (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">We couldn&apos;t load your payment details.</p>
          <Button
            variant="outline"
            className="w-full"
            onClick={() => {
              void refetch()
              void loadStatus(auctionId)
            }}
          >
            Retry
          </Button>
        </div>
      )

    case 'preparing':
      return (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
          <Loader2 className="size-4 animate-spin" />
          Preparing your payment…
        </div>
      )

    case 'due':
      if (!hold) return null
      return (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">Remaining to pay</span>
            <span className="font-mono font-medium">{formatCurrency(hold.remaining)}</span>
          </div>
          <p className="text-xs text-muted-foreground">{formatHoursLeft(hold.hoursLeft)}</p>
          <Button variant="brand" className="h-11 w-full" onClick={() => setDialogOpen(true)}>
            Proceed to payment →
          </Button>
          <PayNowDialog hold={hold} title={title} open={dialogOpen} onOpenChange={setDialogOpen} />
        </div>
      )

    case 'expired':
      return (
        <p role="alert" className="text-sm text-[var(--color-danger-text)]">
          The payment deadline has passed. Your deposit may be forfeited.
        </p>
      )

    case 'paid':
      return (
        <div className="flex items-center gap-2 text-sm font-medium" role="status">
          <CheckCircle2 className="size-4 text-[var(--color-success-default)]" />
          Payment complete
        </div>
      )

    case 'forfeited':
      return (
        <div role="alert" className="flex flex-col gap-1 text-sm">
          <p className="text-[var(--color-danger-text)]">
            The payment deadline passed and your deposit was forfeited.
          </p>
          <Link href="/wallet" className="text-muted-foreground underline underline-offset-2">
            View wallet history
          </Link>
        </div>
      )

    case 'cancelled':
      return (
        <div role="status" className="flex flex-col gap-1 text-sm">
          <p className="text-muted-foreground">
            This sale was cancelled. Your deposit and any held funds were returned.
          </p>
          <Link href="/wallet" className="text-muted-foreground underline underline-offset-2">
            View wallet history
          </Link>
        </div>
      )

    case 'none':
      return (
        <p className="text-sm text-muted-foreground">
          No payment due. See your{' '}
          <Link href="/wallet" className="underline underline-offset-2">
            wallet history
          </Link>{' '}
          for details.
        </p>
      )
  }
}
```

- [ ] **Step 2: Typecheck, lint and run the whole suite**

Run (from `frontend/`): `npx tsc --noEmit && npx eslint components/payment store lib hooks services types && npx vitest run`
Expected: `tsc` exit 0; eslint exit 0 (in particular no `react-hooks/set-state-in-effect` or `exhaustive-deps` complaint); all Vitest tests pass (previously 122, now more). Do not commit.

- [ ] **Step 3: Manual verification (needs the backend stack; skip if it is not available and say so)**

With wallet-service restarted on the new code and `npm run dev`, as the winner of a completed auction:
1. Pay, reload the auction page: the panel reads "Payment complete".
2. Pay in a second tab, then watch the first tab: it flips to "Payment complete" (hold disappears, lookup reads `COMPLETED`).
3. For a forfeited hold (let the 48 h deadline pass, or set the hold's `deadline` in the past in the wallet DB and wait for the 5-minute forfeit run): the panel reads "The payment deadline passed and your deposit was forfeited." with a wallet link.
4. A fresh win still goes "Preparing your payment…" then "Remaining to pay".
5. Take wallet-service down: the panel shows the error with a working Retry.
6. The panel does not re-render in a loop (React DevTools profiler stays quiet).

---

### Task 6: Docs and final verification

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/diagrams/05-winner-payment-flow.md`
- Modify: `docs/epics/wallet/epic.md`
- Modify: `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`

**Interfaces:** none (documentation and verification).

- [ ] **Step 1: Document the endpoint in the architecture doc**

In `docs/architecture.md`, replace the line (currently line 116):

```markdown
**Winner payment endpoints (public, via gateway):** `GET /api/v1/wallets/payments/pending`, `POST /api/v1/wallets/payments/confirm { auctionId }`. Confirm settles atomically: hold row locked first, then winner and seller wallets in ascending id order.
```

with:

```markdown
**Winner payment endpoints (public, via gateway):** `GET /api/v1/wallets/payments/pending`, `GET /api/v1/wallets/payments/{auctionId}` (status of the auction's hold, `PENDING_PAYMENT | COMPLETED | CANCELLED | FORFEITED`, plus `totalAmount`; readable by the winner and the seller only, anyone else or a missing hold gets 404 `PAYMENT_HOLD_NOT_FOUND`; a malformed id gets 400), `POST /api/v1/wallets/payments/confirm { auctionId }`. Confirm settles atomically: hold row locked first, then winner and seller wallets in ascending id order. The status read takes no lock and writes nothing.
```

- [ ] **Step 2: Update the winner payment diagram**

In `docs/diagrams/05-winner-payment-flow.md`, replace these three lines (the end of section C and the fence that closes the mermaid block):

````markdown
        NS-->>Winner: Payment-failed notice (in-app PAYMENT_FAILED push)
    end
```
````

with (no semicolons in message text; Mermaid treats `;` as a statement end):

````markdown
        NS-->>Winner: Payment-failed notice (in-app PAYMENT_FAILED push)
    end

    Note over Winner, WS: D. Winner returns later (reload, another tab, after a timed-out confirm)
    Winner->>App: Opens the auction page
    App->>GW: GET /api/v1/wallets/payments/pending
    GW->>WS: Route
    WS-->>App: No pending hold for this auction
    App->>GW: GET /api/v1/wallets/payments/{auctionId}
    GW->>WS: Route
    WS-->>App: Status COMPLETED, FORFEITED or CANCELLED, or 404 if there is no hold or the caller is not a participant
    App-->>Winner: Payment complete, deposit forfeited, or sale cancelled
```
````

Then replace the last prose line of the file:

```markdown
The frontend refetches pending payments when a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push arrives. Auction-service does not yet learn that the winner paid (`winner_paid_at`); see the WALLET-305 follow-ups.
```

with:

```markdown
The frontend refetches pending payments when a `PAYMENT_REQUIRED`, `PAYMENT_RECEIVED` or `PAYMENT_FAILED` push arrives. A returning winner's panel reads the final state from `GET /api/v1/wallets/payments/{auctionId}`: wallet-service owns the payment facts and auction-service does not record payment (its `winner_paid_at` and `payment_deadline` columns stay unused). Revisit only if auction-service needs the paid fact for its own features.
```

- [ ] **Step 3: Update the wallet epic**

In `docs/epics/wallet/epic.md`:

(a) Replace `States: loading, error (retry), preparing (fresh win, hold not created yet; polls 3 s for up to 30 s), due, expired, paid, none.` with:

```markdown
States: loading, error (retry), preparing (fresh win, hold not created yet; polls 3 s for up to 30 s), due, expired, paid, forfeited, cancelled, none (no hold exists for the auction).
```

(b) Replace the paragraph beginning `**Known limitation:** after a page reload a paid auction reads` (the whole paragraph) with:

```markdown
**Payment status:** when the pending list has no hold for the auction, the panel reads `GET /api/v1/wallets/payments/{auctionId}` (winner or seller only, 404 for anyone else), so a reload, a payment made in another tab, or a forfeit shows the true state. The response carries only `status` and `totalAmount`, no timestamps. No seller screen uses it yet; the endpoint already authorizes the seller.
```

(c) Replace the line `Design: \`docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md\`.` with:

```markdown
Design: `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`, `docs/superpowers/specs/2026-10-15-winner-payment-status-design.md`.
```

- [ ] **Step 4: Resolve the earlier spec's known limitation**

In `docs/superpowers/specs/2026-10-15-winner-payment-ui-design.md`:

(a) Replace `- Showing a paid auction as "Paid" after a page reload (see Known Limitations)` with:

```markdown
- Showing a paid auction as "Paid" after a page reload (done in `2026-10-15-winner-payment-status-design.md`)
```

(b) Replace the first Known Limitations bullet (beginning `- After a page reload, a paid auction reads "No payment due" instead of "Paid"`) with:

```markdown
- (Resolved by `2026-10-15-winner-payment-status-design.md`.) After a page reload a paid auction used to read "No payment due"; the panel now reads the hold's final state from the wallet status endpoint.
```

(c) Replace the second bullet (beginning `- "Preparing" and "paid or forfeited" are indistinguishable`) with:

```markdown
- "Preparing" and "no hold exists" are indistinguishable once the 30 s poll window ends; both fall through to `none`. Paid, forfeited and cancelled are now told apart by the status endpoint.
```

- [ ] **Step 5: Full verification**

Run (from `backend/`):

```bash
mvn test -pl wallet-service -am 2>&1 | grep -E "Tests run:.*Fail|BUILD|ERROR\]" | tail -15
```

Expected: `BUILD SUCCESS`. If the module contains tests that need infrastructure unavailable here and they fail for reasons unrelated to this change, report them (names and the error) instead of fixing them, and confirm `PaymentServiceImplTest` and `PaymentControllerTest` pass on their own.

Run (from `frontend/`): `npm test && npx tsc --noEmit && npm run lint && npm run build`
Expected: all Vitest tests pass; `tsc`, lint and the production build exit 0.

- [ ] **Step 6: Final working-tree check (no commit)**

Run (from the repo root): `git status --short`
Expected: this plan's files appear alongside the previous feature's still-uncommitted files; nothing is staged or committed. Do not commit.
