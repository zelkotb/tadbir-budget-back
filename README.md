# tadbir-budget-back

Spring Boot 3.4 / Java 17 backend **base**. A multi-module Maven project that bundles the
cross-cutting concerns most services need — authentication, authorization, rate limiting,
structured logging, notifications, a BPMN workflow engine, file storage and auditing — so a new
project starts from a hardened, wired-together skeleton instead of a blank `main`.

It carries **no business domain of its own**: add your entities, endpoints and BPMN processes on
top of the modules below.

---

## Module structure

```
tadbir-budget-back/
├── tadbir-budget-common/        Shared contracts (ErrorCode, Roles, MDC keys, utilities, validation)
├── tadbir-budget-dao/           Persistence: entities, repositories, Liquibase, Envers audit
├── tadbir-budget-auth-jwt/      JWT authentication + Spring Security config
├── tadbir-budget-user/          User management (CRUD, password, roles, Envers diff audit)
├── tadbir-budget-org/           Organisation structure (pôles/directions/départements tree)
├── tadbir-budget-nomenclature/  Budget nomenclature definitions + real nomenclatures (rubrique trees)
├── tadbir-budget-project/       Projects (chef, team, org scoping, lifecycle)
├── tadbir-budget-settings/      Company-wide settings (paramétrage) — key/value store
├── tadbir-budget-files/         Generic file storage (traversal-safe), Excel/PDF/JasperReports export
├── tadbir-budget-notification/  Durable notification queue (e-mail + in-app), dispatcher, templating
├── tadbir-budget-workflow/      Flowable engine wrapper: runtime API, listeners, event bridge
└── tadbir-budget-app/           Deployable fat JAR — assembles all modules, config, logging, filters
```

## Dependency graph

```
common ← (no internal deps)
  ↑
dao ← common
  ↑
├── auth-jwt      ← common + dao
├── user          ← common + dao
├── org           ← common + dao
├── nomenclature  ← common + dao
├── project       ← common + dao
├── settings      ← common + dao
├── files         ← common
├── notification  ← common + dao
└── workflow      ← common + dao + notification
        ↑
app ← all of the above
```

---

## Tech stack

| Concern | Technology |
|---|---|
| Framework | Spring Boot 3.4.8 / Java 17 |
| Security | Spring Security 6 + JWT (jjwt 0.12); method security `@PreAuthorize` |
| Persistence | Spring Data JPA / Hibernate / PostgreSQL 16 |
| Migrations | Liquibase 4.33 (`db/changelog/master.xml`) |
| Workflow / BPMN | Flowable 7.1 (embedded, shares the datasource; history level `full`) |
| Entity audit | Hibernate Envers (`@Audited`) + custom `RevInfo` (actor uid + IP) |
| Auth event log | `auth_audit` table (LOGIN / LOGOUT / TOKEN_REFRESH) |
| Notifications | Durable queue + background dispatcher, e-mail (SMTP) + in-app, ShedLock-guarded |
| Observability | Micrometer OTel (traceId/spanId in every log line); central `ControllerLoggingAspect` |
| Rate limiting | Bucket4j 8.10 (token-bucket) + Caffeine (per-IP) |
| File storage | Local filesystem, backend-decided paths, path-traversal hardened |
| Reporting / export | Apache POI (`.xlsx`), Flying Saucer (HTML→PDF), JasperReports 6.21 (`.jrxml`→PDF) |
| Build | Maven multi-module |

---

## What you get out of the box

- **JWT authentication** — login/refresh/logout by **uid**, refresh-token rotation, account lockout
  on repeated failures, `auth_audit` event log. Accounts are created by an admin (no self sign-up).
- **Authorization** — role-based method security via `@PreAuthorize` and the `Roles` catalogue:
  a technical `ROLE_ADMIN`, the organisation hierarchy `ROLE_EMPLOYEE`, `ROLE_CELL_MANAGER`,
  `ROLE_SERVICE_MANAGER`, `ROLE_DEPARTMENT_MANAGER`, `ROLE_DIRECTION_MANAGER`, `ROLE_POLE_MANAGER`,
  `ROLE_DIRECTION_GENERALE`, and `ROLE_CONTROLE_GESTION` (swap for your own).
- **Rate limiting** — per-IP token bucket (`RateLimitFilter`), configurable and toggleable per profile.
- **Structured logging** — MDC filter stamps request/trace ids; `ControllerLoggingAspect` logs every
  controller call; Logback profiles per environment; OTel trace/span ids in each line.
- **Notifications** — durable queue with a background dispatcher, multi-channel (SMTP e-mail +
  in-app), Thymeleaf templates, delivery rate-limiting, ShedLock so only one instance dispatches.
- **Workflow** — embedded Flowable engine wrapper with a runtime/definition REST API, assignment
  listeners (sticky, load-balancing, hierarchy resolution) and an event bridge that turns task
  events into notifications. Drop a `*.bpmn20.xml` under `resources/processes/` to deploy it.
- **Files & reports** — traversal-safe local storage with backend-owned layout, plus Excel export,
  HTML→PDF (Flying Saucer) and designed **JasperReports** (`.jrxml`→PDF) helpers.
- **Auditing** — Hibernate Envers field-level history with a custom revision entity capturing the
  acting user's uid and IP.

---

## Running locally

```bash
# Start PostgreSQL
docker run -d --name tadbir-pg \
  -e POSTGRES_DB=TADBIR_BUDGET -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=admin \
  -p 5432:5432 postgres:16

# Run the app (builds upstream modules first)
mvn spring-boot:run -pl tadbir-budget-app -am
```

- Default profile is `dev` (see `tadbir-budget-app/src/main/resources/application-dev.yaml`).
- Liquibase creates the schema on first boot **and seeds a default admin** (`uid=pm.admin`,
  password `P@ss2026` — change it after first login). Flowable auto-deploys any bundled BPMN.
- Profiles: `dev`, `test`, `stage`, `prod` (prod externalizes everything via env vars).
- All other accounts are created by an admin via `POST /api/v1/user` — there is no self sign-up.

### Build

```bash
mvn -DskipTests clean package          # fat jar in tadbir-budget-app/target
mvn verify -Psecurity                  # + OWASP dependency-check (fails on CVSS >= 7)
```

---

## Deployment

> **Not defined yet.** The Docker/compose files and deploy scripts were removed — the production
> deployment (image build, orchestration, secrets delivery) will be (re)written separately.
>
> Build the runnable artifact with `mvn -DskipTests clean package` (fat jar in
> `tadbir-budget-app/target/`). At minimum a real deployment must provide, via environment /
> externalised config: a datasource, a strong `JWT_SECRET` (Base64, ≥ 256-bit — the app must not fall
> back to a default in prod), `CORS_ALLOWED_ORIGINS`, `APP_COOKIE_SECURE=true` behind HTTPS, and SMTP
> settings. Run with `SPRING_PROFILES_ACTIVE=prod`. Liquibase seeds a `pm.admin` admin on first boot
> (see `2026_07_17_baseline.xml`) — rotate that credential immediately (or scope the seed out of prod).

---

## License

Copyright (c) 2026 Zakaria El Kotb. All rights reserved. See the header in each source file.
