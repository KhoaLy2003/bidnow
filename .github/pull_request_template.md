## Description

> Briefly describe what this PR does and why.

---

## Type of Change

- [ ] ✨ New feature
- [ ] 🐛 Bug fix
- [ ] ♻️ Refactor
- [ ] 📝 Docs / Config

---

## Checklist — Backend (Java/Spring Boot)

> Mark `[x]` for each item checked. Write `N/A` if not applicable.
> See full details in [`docs/code-review-checklist-backend.md`](../docs/code-review-checklist-backend.md)

### General
- [ ] Build passes with no compile errors
- [ ] No dead code, magic numbers, or magic strings
- [ ] No microservice boundary violations
- [ ] `/docs` updated if API surface or architecture changed

### Controller
- [ ] Controller is thin — delegates only, no business logic
- [ ] OpenAPI docs present (`@Operation`, `@ApiResponses`, `@Tag`)
- [ ] Correct HTTP method and status code used
- [ ] Response returns standard `ApiResponse<T>` format

### Service
- [ ] `@Transactional` used correctly (readOnly for reads, default for writes)
- [ ] Correct domain exception type thrown
- [ ] Cache used correctly (`@Cacheable` / `@CacheEvict`)
- [ ] Domain event published after successful operation

### Entity & Repository
- [ ] Extends `BaseEntity`, uses UUID, soft delete via `deletedAt`
- [ ] Every query filters `deletedAt IS NULL`
- [ ] No N+1 queries

### Security
- [ ] Sensitive endpoints have authorization checks
- [ ] Ownership check enforced for resource modification
- [ ] No hardcoded secrets or tokens in code

---

## Related Ticket / Issue

Closes #

---

## Screenshots / Logs (if applicable)

<!-- Paste logs or screenshots if needed -->
