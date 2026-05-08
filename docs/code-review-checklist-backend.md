# Backend Code Review Checklist

This checklist is used to review Pull Requests for the **BidNow** Java/Spring Boot backend.
Mark `[x]` for each item checked. Write `N/A` if not applicable.

---

## 1. General

- [ ] Code compiles successfully with no build errors
- [ ] No dead code, commented-out blocks, or debug print statements
- [ ] No magic numbers or magic strings — use constants instead
- [ ] Class, method, and variable names are clear and follow conventions (camelCase for methods/variables, PascalCase for classes)
- [ ] No microservice boundary violations (no direct DB calls to another service's database)
- [ ] If API surface or architecture changed → `/docs` has been updated

---

## 2. Controller Layer

- [ ] Class has `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor`, `@Validated`
- [ ] OpenAPI docs present: `@Tag` on class, `@Operation` and `@ApiResponses` on each method
- [ ] Controller is **thin** — no business logic, only delegates to service
- [ ] Correct HTTP method and status code used (GET/POST/PUT/DELETE, 200/201/204/400/404/409...)
- [ ] Request body validated with `@Valid`
- [ ] `@CurrentUserId` used to inject authenticated user — no manual token parsing in controller
- [ ] Response returns `ResponseEntity<ApiResponse<T>>` in the standard format
- [ ] No manual exception catching — let `GlobalExceptionHandler` handle it

---

## 3. Service Layer

- [ ] Class has `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- [ ] Class-level `@Transactional` for write operations; method-level `@Transactional(readOnly = true)` for reads
- [ ] No cross-service DB queries — communicate via events or REST calls
- [ ] Cache used correctly: `@Cacheable` for reads, `@CacheEvict` after write/delete, cache names are constants
- [ ] Business validation lives in the service layer, not in the controller
- [ ] Correct exception type thrown: `ResourceNotFoundException`, `ConflictException`, `BadRequestException`, `AccessDeniedException`, etc.
- [ ] Domain event published after a successful operation (via `ApplicationEventPublisher`)
- [ ] Important operations logged with `log.info(...)` / `log.warn(...)` / `log.error(...)`

---

## 4. Entity Layer

- [ ] Extends `BaseEntity` (provides `id`, `createdAt`, `updatedAt`, `deletedAt`)
- [ ] Primary key is `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- [ ] Soft delete via `deletedAt` — no hard delete unless explicitly required
- [ ] Table and column names use `snake_case`, declared explicitly via `@Table` and `@Column`
- [ ] Relationships use `FetchType.LAZY` by default
- [ ] Has `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [ ] Business methods (e.g. `activate()`, `softDelete()`) live inside the entity, not scattered across services

---

## 5. DTO Layer

- [ ] Request DTOs suffixed with `Request`, Response DTOs suffixed with `Response`
- [ ] Request DTOs have full validation annotations (`@NotBlank`, `@Size`, `@Email`, `@Valid` for nested objects...)
- [ ] Validation messages are meaningful — no generic default messages
- [ ] Response DTOs only expose fields needed for that specific use case (no full entity dump)
- [ ] `@Schema` descriptions present for OpenAPI documentation
- [ ] Collection fields initialized with defaults to avoid NPE: `private List<X> items = new ArrayList<>()`
- [ ] Uses `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

---

## 6. Repository Layer

- [ ] Every query filters `deletedAt IS NULL` (no soft-deleted records returned)
- [ ] `@Query` with JPQL/native SQL used for complex queries, with `@Param` for named parameters
- [ ] `Pageable` used for queries returning large result sets
- [ ] `@Modifying` + `@Transactional` present on update/delete queries
- [ ] `JOIN FETCH` used where needed to avoid N+1 queries
- [ ] Projections or DTO queries used for heavy read-only operations

---

## 7. Exception Handling

- [ ] No generic `Exception` thrown — use specific domain exception types
- [ ] Exception messages are clear and include enough context (resource type, id...)
- [ ] `GlobalExceptionHandler` covers any new exception types added
- [ ] No stack traces or internal details exposed in the response body
- [ ] Exceptions logged at the correct level: `warn` for business errors, `error` for unexpected failures

---

## 8. Security

- [ ] Sensitive endpoints have `@PreAuthorize` or are configured in `SecurityFilterChain`
- [ ] Admin endpoints require `hasRole('ADMIN')`
- [ ] No hardcoded secrets, tokens, or passwords in code
- [ ] Ownership check enforced: users can only modify their own resources (unless admin)
- [ ] Sensitive data (password, token) not serialized in JSON responses (`@JsonIgnore`)
- [ ] CORS config does not use wildcard `*` in production

---

## 9. Validation & Input Handling

- [ ] All client input is validated — never trust raw input
- [ ] `@Pattern` used for complex format rules (username, phone number...)
- [ ] Custom validators used for complex business rules (e.g. uniqueness checks)
- [ ] `UUID` type used directly instead of `String` for ID parameters

---

## 10. Mapper

- [ ] MapStruct `@Mapper(componentModel = "spring")` used
- [ ] Audit fields (`id`, `createdAt`, `updatedAt`, `deletedAt`) are `ignore`d when mapping Request → Entity
- [ ] `@MappingTarget` used for update operations (no new entity created)
- [ ] Custom mapping logic uses `@Named` methods — no inline logic in annotations

---

## 11. Logging & Observability

- [ ] `@Slf4j` used — no `System.out.println`
- [ ] No sensitive data logged (password, token, PII)
- [ ] Log messages include enough context (entity id, user id, operation...)
- [ ] Correlation ID propagated via MDC — not re-generated inside a service
- [ ] Business exceptions not logged at `ERROR` level

---

## 12. Configuration

- [ ] No hardcoded config values in code — use `@Value` or `@ConfigurationProperties`
- [ ] Config classes use `@Configuration`; feature toggles use `@ConditionalOnProperty`
- [ ] Sensitive config (passwords, secrets) not committed to the repo — use env variables or a secret manager
- [ ] `@ConfigurationProperties` classes have `@Validated` to catch bad config at startup

---

## 13. Constants

- [ ] Constant classes are `final` with a private constructor
- [ ] Constant names use `UPPER_SNAKE_CASE`
- [ ] No magic strings/numbers used directly in code — always reference a constant
- [ ] Enums have `displayName` and `description` if exposed via API

---

## 14. Performance & Scalability

- [ ] No N+1 queries (verify via Hibernate SQL log or `JOIN FETCH`)
- [ ] Pagination applied to every endpoint returning a list
- [ ] Cache used for data that is read-heavy and changes infrequently
- [ ] No large collections loaded entirely into memory
- [ ] Bulk operations have a batch size limit

---

## 15. BidNow-Specific (Auction Domain)

- [ ] Real-time bid updates published via event (RabbitMQ/Kafka) — no direct service-to-service DB calls
- [ ] Anti-sniping logic (time extension) handled correctly inside the Bidding Service
- [ ] Bid leaderboard reads from Redis — not queried directly from the DB
- [ ] WebSocket notifications use the correct topic pattern (`/topic/auction/{auctionId}`)
- [ ] No shared database between microservices

---

## Reviewer Notes

> Add any comments, concerns, or follow-up items here:

```
[Notes here]
```
