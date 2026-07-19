# tadbir-budget-app

The deployable assembly. This module owns no business logic — its job is to wire all feature modules together and provide application-wide infrastructure: exception handling, request logging (MDC), Spring Boot configuration, and the executable fat JAR.

---

## Module dependency graph

```
tadbir-budget-common      shared contracts + utilities
        ↑
tadbir-budget-dao         persistence layer
        ↑
tadbir-budget-auth-jwt    auth implementation   ─┐
tadbir-budget-user        user management       ─┤
[future modules …]                              ─┤
                                                 ↓
                              tadbir-budget-app  ← you are here
```

`tadbir-budget-app` declares direct dependencies on `auth-jwt`, `user`, and `common`. Everything else (`dao`, JPA, Security, Liquibase, Envers) arrives transitively.

Adding a new feature module: see [Adding a new feature module](#adding-a-new-feature-module).

---

## Contents

### Application entry point

`TadbirBudgetApplication` — `@SpringBootApplication`. Enables `@Async`, `@Retry`, `@Scheduling`, `@EnableWebSecurity`, `@EnableMethodSecurity`. Validates the DB connection on startup.

### Global exception handler (`exception/GlobalExceptionHandler`)

Lives here because it handles exceptions from **all** modules uniformly. Extends `ResponseEntityExceptionHandler` to guarantee priority over Spring MVC's default handler (prevents empty 400 bodies).

| Exception | HTTP | Log |
|---|---|---|
| `CustomException` | as declared | WARN `[status] errorCode` |
| `BadCredentialsException` | 401 | WARN |
| `DisabledException` | 403 | WARN |
| `AccessDeniedException` | 403 | WARN |
| `MethodArgumentNotValidException` | 400 | WARN + field errors map |
| `ConstraintViolationException` | 400 | WARN + field errors map |
| `HttpMessageNotReadableException` | 400 | WARN |
| `NoResourceFoundException` | 404 | WARN |
| `Exception` (catch-all) | 500 | ERROR + stack trace |

Response shape (from `tadbir-budget-common`):
```json
{
  "code":        "VALIDATION_ERROR",
  "status":      400,
  "timestamp":   "2026-06-05T10:00:00Z",
  "fieldErrors": { "email": "INVALID_EMAIL" }
}
```

### Rate limit filter (`filter/RateLimitFilter`)

Runs at `@Order(-200)` — before Spring Security (`-100`) and MdcFilter (`1`). This ensures bad actors are rejected at the outermost layer, before JWT processing or DB hits.

**Strategy:** token-bucket per client IP, backed by Caffeine in-memory cache. Two separate bucket pools:

| Pool | Endpoints | Default limit |
|---|---|---|
| `auth` | `/api/v1/auth/**` | 5 req / 60 s |
| `api`  | everything else | 100 req / 60 s |

**Response on exhaustion (HTTP 429):**
```json
{ "code": "RATE_LIMIT_EXCEEDED", "status": 429, "timestamp": "..." }
```

Headers on every request:
- `X-RateLimit-Limit` — bucket capacity
- `X-RateLimit-Remaining` — tokens left (`0` on 429)
- `Retry-After` — seconds until refill (only on 429)

**Configuration** (`rate-limit.*`):

| Property | Default | Description |
|---|---|---|
| `rate-limit.enabled` | `true` | Set `false` to bypass entirely (dev / test) |
| `rate-limit.auth.capacity` | `5` | Max burst for auth endpoints |
| `rate-limit.auth.refill-tokens` | `5` | Tokens restored per interval |
| `rate-limit.auth.refill-seconds` | `60` | Refill interval in seconds |
| `rate-limit.api.capacity` | `100` | Max burst for general API |
| `rate-limit.api.refill-tokens` | `100` | Tokens restored per interval |
| `rate-limit.api.refill-seconds` | `60` | Refill interval in seconds |

All six are env-var overridable in prod/stage:
```
RATE_LIMIT_ENABLED, RATE_LIMIT_AUTH_CAPACITY, RATE_LIMIT_AUTH_REFILL_TOKENS,
RATE_LIMIT_AUTH_REFILL_SECONDS, RATE_LIMIT_API_CAPACITY, RATE_LIMIT_API_REFILL_TOKENS,
RATE_LIMIT_API_REFILL_SECONDS
```

**CORS interaction:** the filter runs before Spring Security (`-200` vs `-100`), so two edge cases are handled explicitly:
- `OPTIONS` (preflight) requests bypass the filter entirely via `shouldNotFilter()` — they must reach Spring Security's CORS handler without consuming tokens.
- `429` responses include `Access-Control-Allow-Origin` / `Access-Control-Allow-Credentials` headers (resolved from the same `CorsConfigurationSource` bean used by `SecurityConfig`) so the browser can read the error body instead of seeing a CORS failure.

**Upgrading to Redis (horizontal scale):** swap `Caffeine` for `bucket4j-redis-lettuce`. The `buildBucket()` logic and `RateLimitProperties` are unchanged — only the cache backend swaps.

---

### MDC filter (`filter/MdcFilter`)

Runs at `@Order(1)` — after Spring Security (order −100) so the authenticated user is available.

Injects per-request context into SLF4J MDC. Key names are defined in `MdcKeys` (in `tadbir-budget-common`):

| MDC key (`MdcKeys.*`) | Constant | Source | Example |
|---|---|---|---|
| `ip` | `MdcKeys.IP` | `MdcFilter` (proxy-header-aware) | `192.168.1.1` |
| `username` | `MdcKeys.USERNAME` | `SecurityContextHolder` | `pm.admin` / `anonymous` |
| `method` | `MdcKeys.METHOD` | `HttpServletRequest` | `POST` |
| `uri` | `MdcKeys.URI` | `HttpServletRequest` | `/api/v1/auth/login` |
| `traceId` | — | Micrometer OTel (auto) | `4bf92f3577b34da6...` |
| `spanId` | — | Micrometer OTel (auto) | `00f067aa0ba902b7` |

`traceId` is the request correlation ID. `spanId` distinguishes spans within a trace (e.g. main request vs `@Async` audit listener). MDC is cleared in `finally` after every request.

---

## Log format

```
HH:mm:ss.SSS [traceId/spanId] [username] [ip] [method] [uri] LEVEL logger - message
```

Example:
```
10:23:45.123 [4bf92f35/00f067aa] [pm.admin] [192.168.1.5] [POST] [/api/v1/auth/login] INFO  m.z.t.a.s.AuthService - [200] LOGIN
```

### Switching SIEM tools

`MdcFilter` and all log call-sites are SIEM-agnostic. Only the logback appender in `log-config/logback-spring-{env}.xml` changes:

| Target | What to change |
|---|---|
| **ELK** | Uncomment `FILE-JSON` appender in prod/stage logback. Add `logstash-logback-encoder:8.0` to pom. Point Filebeat at `application.json`. |
| **Grafana Loki** | No logback change. Point Promtail at `application.log`. |
| **Graylog** | Replace `LogstashEncoder` with `LogstashTcpSocketAppender` (GELF). |

---

## Changing the authentication mechanism

The app module has **zero** auth-specific code.

1. Create `tadbir-budget-auth-oauth2` providing a `SecurityFilterChain` bean (see `tadbir-budget-auth-jwt/README.md`).
2. In `pom.xml` replace:
   ```xml
   <artifactId>tadbir-budget-auth-jwt</artifactId>
   ```
   with:
   ```xml
   <artifactId>tadbir-budget-auth-oauth2</artifactId>
   ```
3. Done. `@PreAuthorize(Roles.IS_ADMIN)` and `SecurityContextHolder` are unchanged.

---

## Adding a new feature module

Example: `tadbir-budget-files`.

1. Create the module — depends on `tadbir-budget-dao` + `tadbir-budget-common`.
2. Add to root `pom.xml` `<modules>` and `<dependencyManagement>`.
3. Add as a dependency here:
   ```xml
   <dependency>
       <groupId>ma.zakaria</groupId>
       <artifactId>tadbir-budget-files</artifactId>
   </dependency>
   ```
4. Spring Boot component scan picks up all beans automatically (shared `ma.zakaria.tadbirbudget` base package).

---

## Configuration files

| File | Profile | Purpose |
|---|---|---|
| `application.yaml` | all | DataSource, JPA, Liquibase, JWT, Envers, pagination |
| `application-dev.yaml` | dev | localhost DB, `cookie.secure: false` |
| `application-prod.yaml` | prod | production DB, `cookie.secure: true` |
| `application-stage.yaml` | stage | staging DB |
| `application-test.yaml` | test | TestContainers — rate limiting disabled |
| `log-config/logback-spring.xml` | all | delegates to profile-specific file |
| `log-config/logback-spring-dev.xml` | dev | console output |
| `log-config/logback-spring-prod.xml` | prod | rolling file + commented ELK appender |
| `log-config/logback-spring-stage.xml` | stage | rolling file + commented ELK appender |