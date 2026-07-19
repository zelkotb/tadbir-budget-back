# tadbir-budget-common

Shared contracts used by every module. Contains no business logic and no Spring Boot starters — just the types and utilities that cross module boundaries.

---

## Contents

### `exception/`

| Class | Purpose |
|---|---|
| `ErrorCode` | Enum of every machine-readable error code returned by the API. Includes `RATE_LIMIT_EXCEEDED` (HTTP 429). |
| `ApiError` | Unified JSON error response body (`code`, `status`, `timestamp`, `fieldErrors`) |
| `CustomException` | Runtime exception carrying an `ErrorCode` + HTTP status |

### `constant/`

| Class | Purpose |
|---|---|
| `Roles` | Authority strings — technical `ROLE_ADMIN`, the org hierarchy (`ROLE_EMPLOYEE`, `ROLE_DEPARTMENT_MANAGER`, `ROLE_DIRECTION_MANAGER`, `ROLE_POLE_MANAGER`, `ROLE_DIRECTION_GENERALE`) and `ROLE_CONTROLE_GESTION` — and the matching `@PreAuthorize` SpEL constants. **Convention:** role names must never be a prefix or substring of another role name — this ensures `LIKE '%ROLE_X%'` queries are unambiguous. |

### `enums/`

| Class | Purpose |
|---|---|
| `AuditAction` | `CREATE / UPDATE / DELETE` — used in audit query APIs across all audited entities. Decoupled from Hibernate's `RevisionType` (mapping happens in the service layer). |

### `util/`

| Class | Purpose |
|---|---|
| `SecurityUtils` | `getCurrentUser()` → `UserDetails`, `getCurrentUsername()` → `String` (the uid). Shared across all service modules. Cast to concrete `User` entity when needed. |
| `MdcKeys` | Centralised MDC key constants (`ip`, `username`, `method`, `uri`). Prevents key name drift between producers and consumers. |

---

## Dependencies

```
common ← no internal dependencies
    spring-web              (HttpStatus for CustomException)
    jackson-annotations     (@JsonInclude for ApiError)
    spring-security-core    (SecurityContextHolder for SecurityUtils)
    lombok
```

---

## Adding a new error code

Open `ErrorCode.java` and add a constant to the appropriate section:

```java
// ── Authentication ────────────────────────────────────────────────────────
EMAIL_ALREADY_EXISTS,
ACCOUNT_LOCKED,          // ← new

// ── Field validation ─────────────────────────────────────────────────────
REQUIRED,
INVALID_PHONE_NUMBER,    // ← new field-level code
```

For new field-level codes, also update `resolveConstraintName()` in `GlobalExceptionHandler` (in `tadbir-budget-app`).

---

## Adding a new role

Open `Roles.java` — remember the substring convention:

```java
// Authority strings
public final String MANAGER    = "ROLE_MANAGER";

// @PreAuthorize SpEL
public final String IS_MANAGER = "hasRole('MANAGER')";
```

Usage:
```java
@PreAuthorize(Roles.IS_MANAGER)
public ResponseEntity<?> managerOnly() { ... }
```