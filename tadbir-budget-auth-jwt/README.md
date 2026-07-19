# tadbir-budget-auth-jwt

JWT-based authentication implementation. Provides the Spring Security filter chain, auth REST endpoints, refresh token rotation, and an append-only audit log of every auth event.

This module is **one possible implementation** of the authentication contract. See [Swapping the auth mechanism](#swapping-the-auth-mechanism) to replace it.

---

## Contents

### Controllers (`auth/controller/`)

| Endpoint | Method | Public | Body |
|---|---|---|---|
| `/api/v1/auth/login` | POST | ✓ | `{ "uid": "p.admin", "password": "..." }` |
| `/api/v1/auth/refresh` | POST | ✓ | — (refresh-token cookie) |
| `/api/v1/auth/logout` | POST | ✓ | — (refresh-token cookie) |
| `/api/v1/auth/audit` | GET | Admin only (`@PreAuthorize(Roles.IS_ADMIN)`) | — |

Login uses the user's **uid** (e.g. `p.admin`), not email. There is no self sign-up — accounts are
created by an admin through `POST /api/v1/user` (`tadbir-budget-user`).

### Services (`auth/service/`)

| Service | Responsibility |
|---|---|
| `AuthService` | login, refresh, logout flows |
| `RefreshTokenService` | create / rotate / revoke refresh tokens |
| `AuthAuditService` | record success + failure events; query the audit log |

### Security (`security/`, `config/`)

| Class | Role |
|---|---|
| `JwtService` | generate and validate JWT tokens |
| `JwtAuthFilter` | `OncePerRequestFilter` — extracts Bearer token, sets `SecurityContext` |
| `CustomUserDetailsService` | loads `User` by uid for Spring Security |
| `SecurityConfig` | `SecurityFilterChain` bean — the entry point Spring Security uses |

### Audit events (`auth/event/`)

- `AuthAuditEvent` — Spring application event (POJO)
- `AuthAuditListener` — `@Async @TransactionalEventListener(AFTER_COMMIT)` — persists success events after the transaction commits, non-blocking
- Failure events use `@Transactional(REQUIRES_NEW)` in `AuthAuditService` so they persist even when the auth transaction rolls back

---

## Authentication flow

```
POST /login
  → AuthController.login()
  → AuthService.login()
      → AuthenticationManager.authenticate()   validates credentials
      → JwtService.generateToken(user)         creates access token (15 min)
      → RefreshTokenService.create(userId)     creates refresh token (7 days)
      → Response: { jwt } + HttpOnly cookie(refreshToken)
      → AuthAuditService.recordSuccess(uid, LOGIN, ctx)
          → publishes AuthAuditEvent
              → AuthAuditListener persists to auth_audit after commit (async)
```

```
POST /refresh
  → AuthController.refresh()
  → AuthService.refresh()
      → extract refreshToken cookie
      → RefreshTokenService.rotate()   revoke old, issue new
      → JwtService.generateToken(user) new access token
      → Response: { jwt } + updated HttpOnly cookie
```

---

## Configuration properties

```yaml
jwt:
  secret: ${JWT_SECRET}   # Base64-encoded HMAC key, min 256 bits
  expiration: 900         # seconds (15 min)

refresh:
  expiration: 604800      # seconds (7 days)

app:
  cookie:
    secure: true          # false in dev (no HTTPS)
```

---

## Dependencies

```
auth-jwt
  ├── tadbir-budget-common   (ErrorCode, Roles, ApiError)
  ├── tadbir-budget-dao      (User, RefreshToken, AuthAudit, repositories)
  ├── spring-boot-starter-web
  ├── spring-boot-starter-validation
  └── jjwt (api + impl + jackson)

Note: spring-boot-starter-security and spring-boot-starter-data-jpa
come transitively from tadbir-budget-dao.
```

---

## Swapping the auth mechanism

The auth contract is implicit in Spring Security: any auth module must provide a `SecurityFilterChain` bean and populate `SecurityContextHolder`. The app module itself has no auth-specific code.

**To replace JWT with OAuth2/OIDC:**

1. Create a new module `tadbir-budget-auth-oauth2`.
2. Add `spring-boot-starter-oauth2-resource-server` as a dependency.
3. Provide a `SecurityFilterChain` bean configured for JWT/opaque-token validation:
   ```java
   @Bean
   public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
       return http
           .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
           .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
           .build();
   }
   ```
4. In `tadbir-budget-app/pom.xml`, replace:
   ```xml
   <!-- before -->
   <artifactId>tadbir-budget-auth-jwt</artifactId>

   <!-- after -->
   <artifactId>tadbir-budget-auth-oauth2</artifactId>
   ```

No other file in the project needs to change. `@PreAuthorize(Roles.IS_ADMIN)` works with any auth mechanism because it reads from `SecurityContextHolder`, not from JWT-specific code.